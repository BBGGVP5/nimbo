//go:build android && with_gvisor

package mihomocore

import (
	"errors"
	"io"

	"github.com/metacubex/mihomo/config"
	mihomoTun "github.com/metacubex/mihomo/listener/sing_tun"
	"github.com/metacubex/mihomo/tunnel"
	"golang.org/x/sys/unix"
)

const androidTunCompiled = true

func androidPlatformCheck(fd int64) error {
	if fd <= 0 || int64(int(fd)) != fd {
		return problem("INVALID_FD", "borrowedFD", "positive native descriptor required")
	}
	return nil
}

// The platform keeps its original ParcelFileDescriptor. Mihomo receives only
// a validated CLOEXEC duplicate and takes ownership of that duplicate when its
// upstream sing-tun listener is constructed.
func duplicateAndroidTun(fd int64) (int, error) {
	if err := androidPlatformCheck(fd); err != nil {
		return -1, err
	}
	duplicate, err := unix.FcntlInt(uintptr(fd), unix.F_DUPFD_CLOEXEC, 3)
	if err != nil {
		return -1, err
	}
	valid := false
	defer func() {
		if !valid {
			_ = unix.Close(duplicate)
		}
	}()
	flags, err := unix.FcntlInt(uintptr(duplicate), unix.F_GETFL, 0)
	if err != nil {
		return -1, err
	}
	if flags&unix.O_NONBLOCK == 0 {
		return -1, errors.New("borrowed TUN must already be nonblocking")
	}
	if flags&unix.O_ACCMODE != unix.O_RDWR {
		return -1, errors.New("TUN must be readable and writable")
	}
	req, err := unix.NewIfreq("")
	if err != nil {
		return -1, err
	}
	if err = unix.IoctlIfreq(duplicate, unix.TUNGETIFF, req); err != nil {
		return -1, err
	}
	if req.Uint16()&(unix.IFF_TUN|unix.IFF_NO_PI) != (unix.IFF_TUN|unix.IFF_NO_PI) || req.Uint16()&(unix.IFF_TAP|unix.IFF_VNET_HDR|unix.IFF_MULTI_QUEUE) != 0 {
		return -1, errors.New("requires single-queue raw IP TUN without PI/vnet header")
	}
	valid = true
	return duplicate, nil
}

func startAndroidTun(fd int64, cfg *config.Config) (io.Closer, error) {
	if cfg == nil || cfg.General == nil {
		return nil, errors.New("Mihomo Android TUN config is unavailable")
	}
	duplicate, err := duplicateAndroidTun(fd)
	if err != nil {
		return nil, err
	}
	options := androidTunProjection(cfg.General.Tun, duplicate, cfg.General.IPv6)
	listener, err := mihomoTun.New(options, tunnel.Tunnel)
	if err != nil {
		// The pinned lifecycle patch makes sing_tun.New consume the descriptor
		// even when construction fails before the link endpoint exists.
		return nil, err
	}
	return listener, nil
}
