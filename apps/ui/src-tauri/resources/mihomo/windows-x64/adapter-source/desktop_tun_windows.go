//go:build windows

package mihomocore

import (
	"errors"
	"golang.org/x/sys/windows"
	"io"
	"math/bits"
	"net"
	"strings"
	"syscall"
	"unsafe"
)

const desktopTunCompiled = true

// Same Windows socket ABI constants used by the pinned Mihomo dialer.
const desktopIPUnicastIF = 31
const desktopIPv6UnicastIF = 31

func desktopPrivilegeCheck() error {
	if !windows.GetCurrentProcessToken().IsElevated() {
		return problem("PRIVILEGE_REQUIRED", "", "desktop TUN requires a privileged service owner")
	}
	return nil
}

type desktopTunLock struct{ handle windows.Handle }

func (l *desktopTunLock) Close() error { return windows.CloseHandle(l.handle) }
func acquireDesktopTunOwner() (io.Closer, error) {
	if err := desktopPrivilegeCheck(); err != nil {
		return nil, err
	}
	// LocalSystem/admins only. An unprivileged process cannot impersonate an
	// owner or create an inheritable handle keeping an obsolete lease alive.
	descriptor, err := windows.SecurityDescriptorFromString("D:P(A;;GA;;;SY)(A;;GA;;;BA)")
	if err != nil {
		return nil, err
	}
	attributes := windows.SecurityAttributes{Length: uint32(unsafe.Sizeof(windows.SecurityAttributes{})), SecurityDescriptor: descriptor}
	name, err := windows.UTF16PtrFromString(`Global\NimboMihomoTunOwnerV1`)
	if err != nil {
		return nil, err
	}
	handle, err := windows.CreateMutex(&attributes, false, name)
	if err == windows.ERROR_ALREADY_EXISTS {
		if handle != 0 {
			_ = windows.CloseHandle(handle)
		}
		return nil, problem("TUN_IN_USE", "", "another desktop TUN owner is active")
	}
	if err != nil {
		return nil, err
	}
	return &desktopTunLock{handle}, nil
}

func bindDesktopInterface(conn syscall.RawConn, name, network string) error {
	iface, err := net.InterfaceByName(name)
	if err != nil {
		return err
	}
	var bindErr error
	if err = conn.Control(func(fd uintptr) {
		// A dual-stack UDP socket can carry IPv4-mapped datagrams: bind both
		// families, not only the "udp6" socket's nominal family.
		bind6 := windows.SetsockoptInt(windows.Handle(fd), windows.IPPROTO_IPV6, desktopIPv6UnicastIF, iface.Index)
		bind4 := windows.SetsockoptInt(windows.Handle(fd), windows.IPPROTO_IP, desktopIPUnicastIF, int(bits.ReverseBytes32(uint32(iface.Index))))
		if network == "udp6" {
			bindErr = errors.Join(bind6, bind4)
		} else if strings.HasSuffix(network, "6") {
			bindErr = bind6
		} else {
			bindErr = bind4
		}

	}); err != nil {
		return err
	}
	return bindErr
}
