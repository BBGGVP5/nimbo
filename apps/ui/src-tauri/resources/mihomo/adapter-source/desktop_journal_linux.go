//go:build linux && !android

package mihomocore

import (
	"bytes"
	"crypto/rand"
	"encoding/binary"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"reflect"
	"strconv"
	"strings"
	"sync"

	LC "github.com/metacubex/mihomo/listener/config"
	"github.com/sagernet/netlink"
	"golang.org/x/sys/unix"
)

const desktopJournalPath = "/run/nimbo-mihomo-tun-rules.json"
const desktopJournalLimit = 64 << 10

type desktopRuleJournal struct {
	Version   int            `json:"version"`
	Boot      string         `json:"boot"`
	Namespace string         `json:"namespace"`
	PID       int            `json:"pid"`
	Start     string         `json:"start"`
	Mark      uint32         `json:"mark"`
	Rules     []netlink.Rule `json:"rules"`
}

func journalProblem() error {
	return problem("TUN_CLEANUP_FAILED", "", "native rule ownership could not be verified; unknown state was retained")
}

func decodeDesktopJournal(data []byte) (desktopRuleJournal, error) {
	var j desktopRuleJournal
	if len(data) == 0 || len(data) > desktopJournalLimit {
		return j, journalProblem()
	}
	d := json.NewDecoder(bytes.NewReader(data))
	d.DisallowUnknownFields()
	if err := d.Decode(&j); err != nil {
		return j, journalProblem()
	}
	if d.Decode(new(any)) != io.EOF {
		return j, journalProblem()
	}
	return j, nil
}
func (j desktopRuleJournal) validate(boot, namespace string) error {
	if j.Version != 1 || j.Boot != boot || j.Namespace != namespace || j.PID <= 0 || j.Start == "" {
		return journalProblem()
	}
	if _, err := strconv.ParseUint(j.Start, 10, 64); err != nil {
		return journalProblem()
	}
	return validateDesktopPlan(j.Rules, j.Mark)
}
func validateDesktopPlan(rules []netlink.Rule, mark uint32) error {
	if mark == 0 || len(rules) == 0 || len(rules) > 64 {
		return journalProblem()
	}
	for _, r := range rules {
		if (r.Family != netlink.FAMILY_V4 && r.Family != netlink.FAMILY_V6) || r.Priority < desktopTunRule || r.Priority >= desktopTunRule+16 || (r.Table != -1 && r.Table != 0 && r.Table != unix.RT_TABLE_MAIN && r.Table != desktopTunTable) || r.Mark != mark || !r.MarkSet || r.Mask != 0 {
			return journalProblem()
		}
	}
	return nil
}
func sameDesktopRule(a, b netlink.Rule) bool {
	// Normalize unspecified planner table/action values to kernel readback.
	// All selectors and mark presence remain exact (pinned netlink readback).
	normalize := func(r netlink.Rule) netlink.Rule {
		if r.Table < 0 {
			r.Table = 0
		}
		if r.Type == 0 {
			if r.Table > 0 {
				r.Type = unix.FR_ACT_TO_TBL
			} else if r.Goto >= 0 {
				r.Type = unix.FR_ACT_GOTO
			} else {
				r.Type = unix.FR_ACT_NOP
			}
		}
		return r
	}
	return reflect.DeepEqual(normalize(a), normalize(b))
}
func desktopNamespaceIdentity() (string, string, error) {
	boot, err := os.ReadFile("/proc/sys/kernel/random/boot_id")
	if err != nil {
		return "", "", err
	}
	ns, err := os.Readlink("/proc/self/ns/net")
	if err != nil {
		return "", "", err
	}
	return strings.TrimSpace(string(boot)), ns, nil
}
func processStartIdentity(pid int) (string, error) {
	data, err := os.ReadFile(fmt.Sprintf("/proc/%d/stat", pid))
	if err != nil {
		return "", err
	}
	end := bytes.LastIndexByte(data, ')')
	if end < 0 {
		return "", journalProblem()
	}
	fields := strings.Fields(string(data[end+1:]))
	if len(fields) <= 19 {
		return "", journalProblem()
	}
	if _, err = strconv.ParseUint(fields[19], 10, 64); err != nil {
		return "", journalProblem()
	}
	return fields[19], nil
}
func protectedDesktopFile(file *os.File) error {
	var st unix.Stat_t
	if err := unix.Fstat(int(file.Fd()), &st); err != nil {
		return err
	}
	if st.Uid != 0 || st.Mode&unix.S_IFMT != unix.S_IFREG || st.Mode&0077 != 0 || st.Nlink != 1 || st.Size > desktopJournalLimit {
		return journalProblem()
	}
	return nil
}
func readDesktopJournal() (desktopRuleJournal, error) {
	fd, err := unix.Open(desktopJournalPath, unix.O_RDONLY|unix.O_CLOEXEC|unix.O_NOFOLLOW|unix.O_NONBLOCK, 0)
	if err != nil {
		return desktopRuleJournal{}, err
	}
	f := os.NewFile(uintptr(fd), "native-rule-journal")
	defer f.Close()
	if err = protectedDesktopFile(f); err != nil {
		return desktopRuleJournal{}, err
	}
	data, err := io.ReadAll(io.LimitReader(f, desktopJournalLimit+1))
	if err != nil {
		return desktopRuleJournal{}, err
	}
	return decodeDesktopJournal(data)
}
func syncDesktopJournalDirectory() error {
	d, err := os.Open("/run")
	if err != nil {
		return err
	}
	defer d.Close()
	return d.Sync()
}
func writeDesktopJournal(j desktopRuleJournal) error {
	// Existing paths are verified even though rename would not follow symlinks.
	if _, err := readDesktopJournal(); err != nil && !errors.Is(err, os.ErrNotExist) {
		return err
	}
	data, err := json.Marshal(j)
	if err != nil || len(data) > desktopJournalLimit {
		return journalProblem()
	}
	f, err := os.CreateTemp("/run", ".nimbo-rule-journal-*")
	if err != nil {
		return err
	}
	name := f.Name()
	defer os.Remove(name)
	defer f.Close()
	if err = f.Chmod(0600); err != nil {
		return err
	}
	if err = protectedDesktopFile(f); err != nil {
		return err
	}
	if _, err = f.Write(data); err != nil {
		return err
	}
	if err = f.Sync(); err != nil {
		return err
	}
	if err = f.Close(); err != nil {
		return err
	}
	if err = os.Rename(name, desktopJournalPath); err != nil {
		return err
	}
	return syncDesktopJournalDirectory()
}
func clearDesktopPlannedRules(j desktopRuleJournal) error {
	type removal struct{ rule netlink.Rule }
	var remove []removal
	for _, family := range []int{netlink.FAMILY_V4, netlink.FAMILY_V6} {
		current, err := netlink.RuleList(family)
		if err != nil {
			return err
		}
		for _, actual := range current {
			if actual.Mark != j.Mark || actual.Mask != 0 {
				continue
			}
			matched := false
			for _, planned := range j.Rules {
				if sameDesktopRule(planned, actual) {
					remove = append(remove, removal{planned})
					matched = true
					break
				}
			}
			// Validate every marked rule before touching any of them.
			if !matched {
				return journalProblem()
			}
		}
	}
	for _, item := range remove {
		r := item.rule
		if r.Table < 0 && r.Goto < 0 {
			r.Type = unix.FR_ACT_NOP
		}
		if err := netlink.RuleDel(&r); err != nil {
			return err
		}
	}
	// A kernel ACK is not proof of complete cleanup; verify the marker is gone.
	for _, family := range []int{netlink.FAMILY_V4, netlink.FAMILY_V6} {
		rules, err := netlink.RuleList(family)
		if err != nil {
			return err
		}
		for _, r := range rules {
			if r.Mark == j.Mark && r.Mask == 0 {
				return journalProblem()
			}
		}
	}
	return nil
}
func recoverDesktopJournal() error {
	j, err := readDesktopJournal()
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}
	if err != nil {
		return err
	}
	boot, ns, err := desktopNamespaceIdentity()
	if err != nil {
		return err
	}
	if err = j.validate(boot, ns); err != nil {
		return err
	}
	start, err := processStartIdentity(j.PID)
	if err == nil && start == j.Start {
		return problem("TUN_IN_USE", "", "journal owner is still alive")
	}
	if err != nil && !errors.Is(err, os.ErrNotExist) {
		return err
	}
	if err = desktopInterfaceVacant(); err != nil {
		return err
	}
	if err = clearDesktopPlannedRules(j); err != nil {
		return err
	}
	if err = os.Remove(desktopJournalPath); err != nil {
		return err
	}
	return syncDesktopJournalDirectory()
}
func newDesktopTunLock(fd int) (*desktopTunLock, error) {
	boot, ns, err := desktopNamespaceIdentity()
	if err != nil {
		return nil, err
	}
	start, err := processStartIdentity(os.Getpid())
	if err != nil {
		return nil, err
	}
	var token [4]byte
	for binary.BigEndian.Uint32(token[:]) == 0 {
		if _, err = rand.Read(token[:]); err != nil {
			return nil, err
		}
	}
	return &desktopTunLock{fd: fd, journal: desktopRuleJournal{Version: 1, Boot: boot, Namespace: ns, PID: os.Getpid(), Start: start, Mark: binary.BigEndian.Uint32(token[:])}}, nil
}
func (l *desktopTunLock) recordRules(plan []byte) error {
	l.mu.Lock()
	defer l.mu.Unlock()
	if l.closed {
		return journalProblem()
	}
	var rules []netlink.Rule
	if len(plan) > desktopJournalLimit || json.Unmarshal(plan, &rules) != nil {
		return journalProblem()
	}
	if err := validateDesktopPlan(rules, l.journal.Mark); err != nil {
		return err
	}
	j := l.journal
	// Retain the union across physical-path rule resets, so a crash at any
	// mutation boundary has a complete write-ahead deletion plan.
	j.Rules = append([]netlink.Rule(nil), j.Rules...)
	for _, r := range rules {
		seen := false
		for _, old := range j.Rules {
			if sameDesktopRule(old, r) {
				seen = true
				break
			}
		}
		if !seen {
			j.Rules = append(j.Rules, r)
		}
	}
	if err := validateDesktopPlan(j.Rules, j.Mark); err != nil {
		return err
	}
	if err := writeDesktopJournal(j); err != nil {
		return err
	}
	l.journal = j
	return nil
}
func (l *desktopTunLock) clearRules() error {
	l.mu.Lock()
	defer l.mu.Unlock()
	if l.closed {
		return journalProblem()
	}
	if len(l.journal.Rules) == 0 {
		return nil
	}
	return clearDesktopPlannedRules(l.journal)
}
func (l *desktopTunLock) Close() error {
	l.once.Do(func() {
		l.mu.Lock()
		defer l.mu.Unlock()
		l.closed = true
		if len(l.journal.Rules) > 0 {
			l.closeErr = desktopInterfaceVacant()
			if l.closeErr == nil {
				l.closeErr = clearDesktopPlannedRules(l.journal)
			}
			if l.closeErr == nil {
				l.closeErr = os.Remove(desktopJournalPath)
			}
			if l.closeErr == nil {
				l.closeErr = syncDesktopJournalDirectory()
			}
		}
		l.closeErr = errors.Join(l.closeErr, unix.Close(l.fd))
	})
	return l.closeErr
}
func configureDesktopRuleJournal(o LC.Tun, guard io.Closer) LC.Tun {
	l := guard.(*desktopTunLock)
	o.NimboRuleMark, o.NimboRecordRules, o.NimboClearRules = l.journal.Mark, l.recordRules, l.clearRules
	return o
}

// Fixed privileged CLI only: no user-supplied path, marker, table or namespace.
func RecoverDesktopTun() error {
	fd, err := acquireDesktopTunLock()
	if err != nil {
		return err
	}
	defer unix.Close(fd)
	return recoverDesktopJournal()
}

// State lives only under the retained kernel lock; callbacks never enter JSON.
type desktopTunLock struct {
	fd       int
	journal  desktopRuleJournal
	mu       sync.Mutex
	once     sync.Once
	closed   bool
	closeErr error
}
