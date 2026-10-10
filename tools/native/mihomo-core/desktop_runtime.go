package mihomocore

import (
	"errors"
	"github.com/metacubex/mihomo/component/dialer"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	LC "github.com/metacubex/mihomo/listener/config"
	mihomoTun "github.com/metacubex/mihomo/listener/sing_tun"
	"github.com/metacubex/mihomo/tunnel"
	"io"
	"net"
	"net/netip"
	"path"
	"strings"
	"syscall"
	"time"
)

const desktopTunName = "nimbo-mh0"
const desktopTunTable = 52888
const desktopTunRule = 22888

// StartDesktopTun is a privileged local entry, never a JSON owner override.
// The service must supply a protected data directory and keep a process lease.
// The GUI must not elevate itself or expose this entry on a public RPC listener.
func StartDesktopTun(input string) string { return invokeOwners(input, nil, false, true) }

// Pure source admission, also usable before replacing an existing connection.
// Retain protocol/rule/provider semantics; only host ownership is projected.
func desktopRuntimePolicy(d *inspection) error {
	view := *d
	view.root = make(map[string]any, len(d.root))
	for k, v := range d.root {
		if k != "tun" {
			view.root[k] = v
		}
	}
	if err := androidRuntimePolicy(&view); err != nil {
		var typed *issue
		if errors.As(err, &typed) && typed.Code == "UNSUPPORTED_ANDROID_CONFIG" {
			return problem("UNSUPPORTED_DESKTOP_CONFIG", typed.Path, "setting conflicts with the managed desktop network owner")
		}
		return err
	}
	for _, section := range []string{"proxy-providers", "rule-providers"} {
		definitions, _ := d.root[section].(map[string]any)
		for name, value := range definitions {
			provider, _ := value.(map[string]any)
			if value, exists := provider["path"]; exists {
				resource, ok := value.(string)
				portable := strings.ReplaceAll(resource, "\\", "/")
				if !ok || resource == "" || path.IsAbs(portable) || strings.Contains(portable, ":") {
					return problem("UNSUPPORTED_DESKTOP_CONFIG", section+"."+name+".path", "service-private relative resource required")
				}
				for _, segment := range strings.Split(portable, "/") {
					if segment == ".." {
						return problem("UNSUPPORTED_DESKTOP_CONFIG", section+"."+name+".path", "resource may not escape the protected service home")
					}
				}
			}
		}
	}
	if androidUsesSystemDNS(d.root["dns"]) {
		return problem("UNSUPPORTED_DESKTOP_CONFIG", "dns", "use explicit upstream DNS; system resolver could reenter the TUN")
	}
	value, exists := d.root["tun"]
	if !exists {
		return nil
	}
	tun, ok := value.(map[string]any)
	if !ok {
		return problem("INVALID_CONFIG", "tun", "mapping required")
	}
	for field, setting := range tun {
		bad := func() error {
			return problem("UNSUPPORTED_DESKTOP_CONFIG", "tun."+field, "interface, routes, stack and DNS hijack are owned by the desktop service")
		}
		switch field {
		case "enable", "auto-route", "auto-detect-interface", "strict-route":
			if setting != true {
				return bad()
			}
		case "stack":
			if setting != "system" {
				return bad()
			}
		case "dns-hijack":
			rows, ok := setting.([]any)
			if !ok || len(rows) != 2 {
				return bad()
			}
			seen := map[string]bool{}
			for _, v := range rows {
				s, ok := v.(string)
				if !ok || (s != "any:53" && s != "tcp://any:53") {
					return bad()
				}
				seen[s] = true
			}
			if len(seen) != 2 {
				return bad()
			}
		case "endpoint-independent-nat", "udp-timeout", "icmp-timeout", "disable-icmp-forwarding":
			// Retained verbatim and type-checked by the pinned upstream parser.
		default:
			return bad()
		}
	}
	return nil
}

// Replace all source-defined OS controls rather than letting a subscription
// attach to somebody else's device or delete their route table on teardown.
func desktopTunProjection(source LC.Tun, ipv6 bool) LC.Tun {
	o := androidTunProjection(source, 0, ipv6)
	o.Device = desktopTunName
	o.NimboWindowsExclusive = true
	o.Stack = C.TunSystem
	o.AutoRoute = true
	o.AutoDetectInterface = true
	o.StrictRoute = true
	o.IPRoute2TableIndex = desktopTunTable
	o.IPRoute2RuleIndex = desktopTunRule
	o.DNSHijack = []string{"any:53", "tcp://any:53"}
	o.Inet4Address = []netip.Prefix{netip.MustParsePrefix("172.29.255.1/30")}
	if ipv6 {
		o.Inet6Address = []netip.Prefix{netip.MustParsePrefix("fdfe:dcba:5288::1/126")}
	}
	return o
}

func desktopInterfaceVacant() error {
	interfaces, err := net.Interfaces()
	if err != nil {
		return err
	}
	for _, iface := range interfaces {
		if iface.Name == desktopTunName {
			return problem("TUN_IN_USE", "tun", "existing interface is not adopted or deleted")
		}
	}
	return nil
}

func startDesktopTun(cfg *config.Config) (io.Closer, error) {
	if cfg == nil || cfg.General == nil {
		return nil, errors.New("desktop TUN configuration unavailable")
	}
	guard, err := acquireDesktopTunOwner()
	if err != nil {
		return nil, err
	}
	if err = desktopInterfaceVacant(); err != nil {
		_ = guard.Close()
		return nil, err
	}
	listener, err := mihomoTun.New(configureDesktopRuleJournal(desktopTunProjection(cfg.General.Tun, cfg.General.IPv6), guard), tunnel.Tunnel)
	if err != nil {
		_ = guard.Close()
		return nil, err
	}
	return &desktopTunLifetime{listener: listener, guard: guard}, nil
}

type desktopTunLifetime struct {
	listener io.Closer
	guard    io.Closer
}

func (l *desktopTunLifetime) Close() error {
	// Close routes/DNS/interface before releasing exclusive ownership.
	err := l.listener.Close()
	// Linux/Wintun device deletion may complete asynchronously after closing the
	// last handle. Keep the owner lock until bounded actual interface retirement,
	// including a failure after TUN construction but before controller readiness.
	deadline := time.Now().Add(2 * time.Second)
	var retired error
	for {
		retired = desktopInterfaceVacant()
		if retired == nil || !time.Now().Before(deadline) {
			break
		}
		var issue *issue
		if !errors.As(retired, &issue) || issue.Code != "TUN_IN_USE" {
			break
		}
		time.Sleep(10 * time.Millisecond)
	}
	other := l.guard.Close()
	return errors.Join(err, retired, other)
}

// Upstream intentionally skips its interface finder when DefaultSocketHook is
// installed (mobile ownership). Desktop must bind here instead, without racing
// replacement of that global callback or changing the mobile protection path.
func bindDesktopSocket(network, address string, conn syscall.RawConn, committed bool) error {
	destination, err := desktopSocketDestination(network, address)
	if err != nil {
		return err
	}
	destination = destination.Unmap()
	if destination.IsLoopback() {
		return nil
	}
	finder := dialer.DefaultInterfaceFinder.Load()
	if finder == nil {
		// Provider bootstrap precedes TUN construction. Once committed, never
		// silently dial unbound after a lost monitor/owner.
		if !committed {
			return nil
		}
		return problem("PHYSICAL_INTERFACE_UNAVAILABLE", "", "native desktop interface monitor is not attached")
	}
	name := finder.FindInterfaceName(destination)
	if name == "" || name == "<invalid>" || name == "<nil>" || name == desktopTunName {
		return problem("PHYSICAL_INTERFACE_UNAVAILABLE", "", "physical egress is unavailable; no unbound fallback")
	}
	return bindDesktopInterface(conn, name, network)
}

func desktopSocketDestination(network, address string) (netip.Addr, error) {
	host, _, err := net.SplitHostPort(address)
	if err != nil {
		return netip.Addr{}, err
	}
	if host == "" {
		if strings.HasSuffix(network, "6") {
			return netip.IPv6Unspecified(), nil
		}
		return netip.IPv4Unspecified(), nil
	}
	return netip.ParseAddr(host)
}
