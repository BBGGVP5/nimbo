//go:build linux && !android

package mihomocore

import (
	"encoding/json"
	"github.com/sagernet/netlink"
	"testing"
)

func fixtureOwnedRule() netlink.Rule {
	r := *netlink.NewRule()
	r.Family, r.Priority, r.Table = netlink.FAMILY_V4, desktopTunRule, desktopTunTable
	r.Mark, r.MarkSet, r.Mask = 12345, true, 0
	return r
}
func TestOwnedRuleRequiresExactPlan(t *testing.T) {
	planned := fixtureOwnedRule()
	kernel := planned
	kernel.Type = 1
	if !sameDesktopRule(planned, kernel) {
		t.Fatal("kernel action normalization")
	}
	for _, mutate := range []func(*netlink.Rule){
		func(r *netlink.Rule) { r.Mark++ }, func(r *netlink.Rule) { r.Mask = 1 },
		func(r *netlink.Rule) { r.Priority++ }, func(r *netlink.Rule) { r.Table = 254 },
		func(r *netlink.Rule) { r.IifName = "foreign" }, func(r *netlink.Rule) { r.Invert = true },
		func(r *netlink.Rule) { r.Type = 6 },
		func(r *netlink.Rule) { r.MarkSet = false },
	} {
		other := kernel
		mutate(&other)
		if sameDesktopRule(planned, other) {
			t.Fatal("foreign rule adopted")
		}
	}
	planned.Table = -1
	kernel = planned
	kernel.Table = 0
	if !sameDesktopRule(planned, kernel) {
		t.Fatal("kernel unspecified table normalization")
	}
}
func TestJournalPlanAdmission(t *testing.T) {
	valid := fixtureOwnedRule()
	if err := validateDesktopPlan([]netlink.Rule{valid}, 12345); err != nil {
		t.Fatal(err)
	}
	for _, mutate := range []func(*netlink.Rule){
		func(r *netlink.Rule) { r.Family = 1 }, func(r *netlink.Rule) { r.Priority-- },
		func(r *netlink.Rule) { r.Table = 1234 }, func(r *netlink.Rule) { r.Mark = 0 },
		func(r *netlink.Rule) { r.Mask = -1 }, func(r *netlink.Rule) { r.MarkSet = false },
	} {
		r := valid
		mutate(&r)
		if validateDesktopPlan([]netlink.Rule{r}, 12345) == nil {
			t.Fatal("invalid plan accepted")
		}
	}
	if validateDesktopPlan(nil, 12345) == nil {
		t.Fatal("empty plan")
	}
	if validateDesktopPlan(make([]netlink.Rule, 65), 12345) == nil {
		t.Fatal("oversize plan")
	}
}
func TestJournalIdentityAdmission(t *testing.T) {
	j := desktopRuleJournal{Version: 1, Boot: "boot", Namespace: "net:[1]", PID: 123, Start: "456", Mark: 12345, Rules: []netlink.Rule{fixtureOwnedRule()}}
	if err := j.validate("boot", "net:[1]"); err != nil {
		t.Fatal(err)
	}
	if j.validate("other", "net:[1]") == nil || j.validate("boot", "net:[2]") == nil {
		t.Fatal("foreign identity adopted")
	}
	j.Version = 2
	if j.validate("boot", "net:[1]") == nil {
		t.Fatal("unknown schema")
	}
	data, _ := json.Marshal(j)
	data = append(data, []byte(" {}")...)
	if _, err := decodeDesktopJournal(data); err == nil {
		t.Fatal("trailing JSON adopted")
	}
	if _, err := decodeDesktopJournal(make([]byte, desktopJournalLimit+1)); err == nil {
		t.Fatal("oversize JSON adopted")
	}
}

func TestClosedOwnerCannotWriteOrClearRules(t *testing.T) {
	owner := &desktopTunLock{closed: true}
	if owner.recordRules([]byte("[]")) == nil || owner.clearRules() == nil {
		t.Fatal("late callback escaped closed lease")
	}
}
