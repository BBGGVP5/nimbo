//go:build (!linux && !windows) || android

package mihomocore

import (
	"io"
	"syscall"
)

const desktopTunCompiled = false

func desktopPrivilegeCheck() error {
	return problem("PLATFORM_UNAVAILABLE", "", "managed desktop TUN requires Windows or Linux")
}
func acquireDesktopTunOwner() (io.Closer, error) { return nil, desktopPrivilegeCheck() }

func bindDesktopInterface(conn syscall.RawConn, name, network string) error {
	return desktopPrivilegeCheck()
}
