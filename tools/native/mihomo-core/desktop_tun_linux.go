//go:build linux && !android

package mihomocore

import (
	"github.com/sagernet/netlink"
	"golang.org/x/sys/unix"
	"io"
	"os"
	"syscall"
)

const desktopTunCompiled = true

func desktopPrivilegeCheck() error {
	if os.Geteuid() != 0 {
		return problem("PRIVILEGE_REQUIRED", "", "desktop TUN must be owned by the privileged helper, not the GUI")
	}
	return nil
}

func acquireDesktopTunLock() (int, error) {
	if err := desktopPrivilegeCheck(); err != nil {
		return -1, err
	}
	var parent unix.Stat_t
	if err := unix.Lstat("/run", &parent); err != nil {
		return -1, err
	}
	if parent.Uid != 0 || parent.Mode&unix.S_IFMT != unix.S_IFDIR || parent.Mode&0022 != 0 {
		return -1, journalProblem()
	}
	// Root-created, no-follow, exclusive process lease; never unlink a lock while
	// another process could still hold the old inode. Not stored in a user home.
	fd, err := unix.Open("/run/nimbo-mihomo-tun.lock", unix.O_CREAT|unix.O_RDWR|unix.O_CLOEXEC|unix.O_NOFOLLOW, 0600)
	if err != nil {
		return -1, err
	}
	ok := false
	defer func() {
		if !ok {
			_ = unix.Close(fd)
		}
	}()
	var stat unix.Stat_t
	if err = unix.Fstat(fd, &stat); err != nil {
		return -1, err
	}
	if stat.Uid != 0 || stat.Mode&unix.S_IFMT != unix.S_IFREG || stat.Mode&0077 != 0 || stat.Nlink != 1 {
		return -1, problem("TUN_IN_USE", "", "unsafe native ownership lock")
	}
	if err = unix.Flock(fd, unix.LOCK_EX|unix.LOCK_NB); err != nil {
		return -1, problem("TUN_IN_USE", "", "another native TUN owner is active")
	}
	ok = true
	return fd, nil
}
func acquireDesktopTunOwner() (io.Closer, error) {
	fd, err := acquireDesktopTunLock()
	if err != nil {
		return nil, err
	}
	ok := false
	defer func() {
		if !ok {
			_ = unix.Close(fd)
		}
	}()
	if err = recoverDesktopJournal(); err != nil {
		return nil, err
	}
	for _, family := range []int{netlink.FAMILY_V4, netlink.FAMILY_V6} {
		routes, err := netlink.RouteListFiltered(family, &netlink.Route{Table: desktopTunTable}, netlink.RT_FILTER_TABLE)
		if err != nil {
			return nil, err
		}
		if len(routes) != 0 {
			return nil, problem("TUN_IN_USE", "", "reserved route table is not empty; nothing was removed")
		}
		rules, err := netlink.RuleList(family)
		if err != nil {
			return nil, err
		}
		for _, rule := range rules {
			if rule.Table == desktopTunTable || (rule.Priority >= desktopTunRule && rule.Priority < desktopTunRule+16) {
				return nil, problem("TUN_IN_USE", "", "reserved rules already exist; nothing was removed")
			}
		}
	}
	lock, err := newDesktopTunLock(fd)
	if err != nil {
		return nil, err
	}
	ok = true
	return lock, nil
}

func bindDesktopInterface(conn syscall.RawConn, name, network string) error {
	var bindErr error
	if err := conn.Control(func(fd uintptr) {
		bindErr = unix.SetsockoptString(int(fd), unix.SOL_SOCKET, unix.SO_BINDTODEVICE, name)
	}); err != nil {
		return err
	}
	return bindErr
}
