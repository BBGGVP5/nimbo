// Package awgcore owns the pinned AWG engine and an authenticated local SOCKS bridge.
package awgcore

import (
	"bufio"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"fmt"
	"net"
	"net/netip"
	"strconv"
	"strings"
)

const Version = "v3.1.20260828"
const MaxConfigBytes = 128 * 1024

// Config contains validated input only. Never print it: UAPI contains private keys.
type Config struct {
	Addresses, DNS []netip.Addr
	MTU            int
	Endpoint       string
	UAPI           string
}

func keyHex(value string) (string, error) {
	b, e := base64.StdEncoding.DecodeString(value)
	if e != nil || len(b) != 32 {
		b, e = hex.DecodeString(value)
	}
	if e != nil || len(b) != 32 {
		return "", errors.New("key must encode exactly 32 bytes")
	}
	return hex.EncodeToString(b), nil
}

// ParseConfig accepts a single peer only. Hook commands and unknown keys fail closed.
func ParseConfig(text string) (Config, error) {
	c := Config{MTU: 1280}
	if len(text) > MaxConfigBytes {
		return c, errors.New("configuration exceeds 128 KiB")
	}
	fields := map[string]map[string]string{"interface": {}, "peer": {}}
	section := ""
	seen := map[string]bool{}
	scan := bufio.NewScanner(strings.NewReader(strings.TrimPrefix(text, "\ufeff")))
	scan.Buffer(make([]byte, 4096), MaxConfigBytes)
	for scan.Scan() {
		line := strings.TrimSpace(scan.Text())
		if line == "" || strings.HasPrefix(line, "#") || strings.HasPrefix(line, ";") {
			continue
		}
		if strings.HasPrefix(line, "[") {
			section = strings.ToLower(strings.TrimSpace(strings.TrimSuffix(strings.TrimPrefix(line, "["), "]")))
			if !strings.HasSuffix(line, "]") || fields[section] == nil || seen[section] {
				return c, errors.New("expected one Interface and one Peer section")
			}
			seen[section] = true
			continue
		}
		k, v, ok := strings.Cut(line, "=")
		k = strings.ToLower(strings.TrimSpace(k))
		aliases := map[string]string{"headerprotectionkey": "header_protection_key", "contentpaddingaddition": "content_padding_addition", "rekeyaftertime": "rekey_after_time", "rekeytimeout": "rekey_timeout", "rejectaftertime": "reject_after_time", "keepalivetimeout": "keepalive_timeout", "maxhandshakeattempts": "max_handshake_attempts", "randomtrailers": "random_trailers", "disablecookies": "disable_cookies"}
		if alias, ok := aliases[k]; ok {
			k = alias
		}
		v = strings.TrimSpace(v)
		if !ok || section == "" || v == "" || strings.ContainsAny(v, "\r\n\x00") {
			return c, errors.New("invalid configuration line")
		}
		if _, exists := fields[section][k]; exists {
			return c, fmt.Errorf("duplicate field %s", k)
		}
		fields[section][k] = v
	}
	if scan.Err() != nil {
		return c, errors.New("invalid configuration length")
	}
	if !seen["interface"] || !seen["peer"] {
		return c, errors.New("Interface and Peer sections are required")
	}
	i, p := fields["interface"], fields["peer"]
	private, e := keyHex(i["privatekey"])
	if e != nil {
		return c, fmt.Errorf("PrivateKey: %w", e)
	}
	public, e := keyHex(p["publickey"])
	if e != nil {
		return c, fmt.Errorf("PublicKey: %w", e)
	}
	for _, s := range strings.Split(i["address"], ",") {
		prefix, e := netip.ParsePrefix(strings.TrimSpace(s))
		if e != nil {
			return c, errors.New("Address must contain IP prefixes")
		}
		c.Addresses = append(c.Addresses, prefix.Addr())
	}
	if len(c.Addresses) > 8 {
		return c, errors.New("too many interface addresses")
	}
	dns := i["dns"]
	if dns == "" {
		dns = "1.1.1.1"
	}
	for _, s := range strings.Split(dns, ",") {
		ip, e := netip.ParseAddr(strings.TrimSpace(s))
		if e != nil {
			return c, errors.New("DNS must contain IP addresses")
		}
		c.DNS = append(c.DNS, ip)
	}
	if len(c.DNS) > 8 {
		return c, errors.New("too many DNS addresses")
	}
	if v := i["mtu"]; v != "" {
		c.MTU, e = strconv.Atoi(v)
		if e != nil || c.MTU < 1280 || c.MTU > 9000 {
			return c, errors.New("MTU must be 1280..9000")
		}
	}
	host, port, e := net.SplitHostPort(p["endpoint"])
	if e != nil || host == "" {
		return c, errors.New("Endpoint must be host:port, with brackets for IPv6")
	}
	n, e := strconv.ParseUint(port, 10, 16)
	if e != nil || n == 0 {
		return c, errors.New("invalid endpoint port")
	}
	c.Endpoint = net.JoinHostPort(host, port)
	lines := []string{"private_key=" + private, "listen_port=0"}
	order := []string{"jc", "jmin", "jmax", "s1", "s2", "s3", "s4", "h1", "h2", "h3", "h4", "i1", "i2", "i3", "i4", "i5", "header_protection_key", "content_padding_addition", "rekey_after_time", "rekey_timeout", "reject_after_time", "keepalive_timeout", "max_handshake_attempts", "random_trailers", "disable_cookies"}
	allow := map[string]bool{"privatekey": true, "address": true, "dns": true, "mtu": true, "listenport": true}
	for _, k := range order {
		allow[k] = true
		v, ok := i[k]
		if !ok {
			continue
		}
		if len(v) > 8192 {
			return c, fmt.Errorf("%s is too long", k)
		}
		if strings.HasPrefix(k, "i") && len(k) == 2 { // AWG packet expressions are validated by the engine.
		} else if (strings.HasPrefix(k, "h") && len(k) == 2) || k == "content_padding_addition" || k == "rekey_after_time" || k == "rekey_timeout" || k == "reject_after_time" || k == "keepalive_timeout" || k == "max_handshake_attempts" {
			a, b, rangeOK := strings.Cut(v, "-")
			x, ea := strconv.ParseUint(a, 10, 32)
			if ea != nil {
				return c, fmt.Errorf("invalid %s", k)
			}
			if rangeOK {
				y, eb := strconv.ParseUint(b, 10, 32)
				if eb != nil || y < x {
					return c, fmt.Errorf("invalid %s range", k)
				}
			}
		} else if k == "random_trailers" || k == "disable_cookies" {
			b, e := strconv.ParseBool(v)
			if e != nil {
				return c, fmt.Errorf("invalid %s", k)
			}
			v = strconv.FormatBool(b)
		} else if k == "header_protection_key" {
			v, e = keyHex(v)
			if e != nil {
				return c, fmt.Errorf("invalid %s", k)
			}
		} else {
			u, e := strconv.ParseUint(v, 10, 32)
			if e != nil || u > 65535 {
				return c, fmt.Errorf("invalid %s", k)
			}
		}
		lines = append(lines, k+"="+v)
	}
	for k := range i {
		if !allow[k] {
			return c, fmt.Errorf("unsupported interface field %s", k)
		}
	}
	if lo, ok := i["jmin"]; ok {
		if hi, yes := i["jmax"]; yes {
			a, _ := strconv.Atoi(lo)
			b, _ := strconv.Atoi(hi)
			if a > b {
				return c, errors.New("Jmin exceeds Jmax")
			}
		}
	}
	lines = append(lines, "replace_peers=true", "public_key="+public)
	if s := p["presharedkey"]; s != "" {
		h, e := keyHex(s)
		if e != nil {
			return c, errors.New("invalid PresharedKey")
		}
		lines = append(lines, "preshared_key="+h)
	}
	if s := p["persistentkeepalive"]; s != "" {
		if _, e := strconv.ParseUint(s, 10, 16); e != nil {
			return c, errors.New("invalid PersistentKeepalive")
		}
		lines = append(lines, "persistent_keepalive_interval="+s)
	}
	lines = append(lines, "replace_allowed_ips=true")
	allowed := p["allowedips"]
	if allowed == "" {
		return c, errors.New("AllowedIPs required")
	}
	ips := strings.Split(allowed, ",")
	if len(ips) > 256 {
		return c, errors.New("too many AllowedIPs")
	}
	for _, s := range ips {
		prefix, e := netip.ParsePrefix(strings.TrimSpace(s))
		if e != nil {
			return c, errors.New("invalid AllowedIPs prefix")
		}
		lines = append(lines, "allowed_ip="+prefix.Masked().String())
	}
	for k := range p {
		switch k {
		case "publickey", "presharedkey", "endpoint", "allowedips", "persistentkeepalive":
		default:
			return c, fmt.Errorf("unsupported peer field %s", k)
		}
	}
	c.UAPI = strings.Join(lines, "\n") + "\n"
	return c, nil
}
