package trusttunnelcore

import (
	"context"
	"encoding/base64"
	"encoding/pem"
	"errors"
	"net"
	"net/netip"
	"strconv"
	"strings"
)

// Deep-link decoding for hosts without the shared Kotlin parser (the iOS
// Packet Tunnel extension). Spec: TrustTunnel DEEP_LINK.md, format version 2.

const maxLinkVersion = 2

// Endpoint holds the deep-link fields this client can use.
type Endpoint struct {
	Hostname         string
	Addresses        []string
	CustomSNI        string
	Username         string
	Password         string
	SkipVerification bool
	CertificatePEM   string
	HTTP3            bool
	AntiDPI          bool
	ClientRandom     bool
	Name             string
}

func readVarint(b []byte, i int) (uint64, int, error) {
	if i >= len(b) {
		return 0, 0, errors.New("truncated")
	}
	size := 1 << (b[i] >> 6)
	if i+size > len(b) {
		return 0, 0, errors.New("truncated")
	}
	v := uint64(b[i] & 0x3f)
	for _, c := range b[i+1 : i+size] {
		v = v<<8 | uint64(c)
	}
	return v, i + size, nil
}

// derToPEM splits concatenated DER certificates by their SEQUENCE lengths.
func derToPEM(der []byte) string {
	var out strings.Builder
	for len(der) > 0 {
		n := len(der)
		if len(der) > 1 && der[0] == 0x30 {
			if l := int(der[1]); l < 0x80 {
				n = 2 + l
			} else if c := l & 0x7f; c > 0 && c <= 4 && len(der) >= 2+c {
				length := 0
				for _, b := range der[2 : 2+c] {
					length = length<<8 | int(b)
				}
				n = 2 + c + length
			}
		}
		if n > len(der) {
			n = len(der)
		}
		_ = pem.Encode(&out, &pem.Block{Type: "CERTIFICATE", Bytes: der[:n]})
		der = der[n:]
	}
	return out.String()
}

// ParseLink decodes `tt://?<base64url TLV>[#name]` with static parameters.
func ParseLink(link string) (Endpoint, error) {
	var e Endpoint
	text := strings.TrimSpace(link)
	if len(text) < 5 || !strings.EqualFold(text[:5], "tt://") {
		return e, fail("CONFIG", "not a tt:// link")
	}
	payload, _, _ := strings.Cut(text[5:], "#")
	payload = strings.TrimRight(strings.TrimPrefix(payload, "?"), "=")
	raw, err := base64.RawURLEncoding.DecodeString(payload)
	if err != nil {
		if raw, err = base64.RawStdEncoding.DecodeString(payload); err != nil {
			return e, fail("CONFIG", "link payload is not base64")
		}
	}
	var version uint64
	for i := 0; i < len(raw); {
		tag, next, err := readVarint(raw, i)
		if err != nil {
			return e, fail("CONFIG", "truncated link")
		}
		length, start, err := readVarint(raw, next)
		if err != nil || length > uint64(len(raw)-start) {
			return e, fail("CONFIG", "truncated link")
		}
		value := raw[start : start+int(length)]
		i = start + int(length)
		flag := len(value) == 1 && value[0] != 0
		switch tag {
		case 0x00:
			version, _, _ = readVarint(value, 0)
		case 0x01:
			e.Hostname = strings.TrimSpace(string(value))
		case 0x02:
			if a := strings.TrimSpace(string(value)); a != "" {
				e.Addresses = append(e.Addresses, a)
			}
		case 0x03:
			e.CustomSNI = strings.TrimSpace(string(value))
		case 0x05:
			e.Username = strings.TrimSpace(string(value))
		case 0x06:
			e.Password = string(value)
		case 0x07:
			e.SkipVerification = flag
		case 0x08:
			if len(value) > 0 {
				e.CertificatePEM = derToPEM(value)
			}
		case 0x09:
			p, _, _ := readVarint(value, 0)
			e.HTTP3 = p == 2
		case 0x0A:
			e.AntiDPI = flag
		case 0x0B, 0x0F:
			e.ClientRandom = e.ClientRandom || strings.TrimSpace(string(value)) != ""
		case 0x0C:
			e.Name = strings.TrimSpace(string(value))
		}
	}
	if version > maxLinkVersion {
		return e, fail("CONFIG", "unsupported deep link version")
	}
	if e.Hostname == "" || len(e.Addresses) == 0 || e.Username == "" || e.Password == "" {
		return e, fail("CONFIG", "link has no static connection parameters")
	}
	return e, nil
}

func splitAddress(address string) (string, int, error) {
	host, port, err := net.SplitHostPort(address)
	if err != nil {
		// A bare host without a port uses 443, like the official client.
		return strings.Trim(address, "[]"), 443, nil
	}
	p, err := strconv.Atoi(port)
	if err != nil || p < 1 || p > 65535 {
		return "", 0, fail("CONFIG", "invalid endpoint port")
	}
	return host, p, nil
}

// requestFromLink fills connection fields from Link, resolving the first
// usable endpoint address through the (protected) resolver.
func requestFromLink(ctx context.Context, r Request, resolver *net.Resolver) (Request, error) {
	e, err := ParseLink(r.Link)
	if err != nil {
		return r, err
	}
	var lastErr error = fail("UNREACHABLE", "cannot resolve endpoint address")
	for _, address := range e.Addresses {
		host, port, err := splitAddress(address)
		if err != nil {
			lastErr = err
			continue
		}
		ip, perr := netip.ParseAddr(host)
		if perr != nil {
			ips, err := resolver.LookupNetIP(ctx, "ip", host)
			if err != nil || len(ips) == 0 {
				continue
			}
			ip = ips[0]
			for _, candidate := range ips {
				if candidate.Is4() {
					ip = candidate
					break
				}
			}
		}
		r.Server = netip.AddrPortFrom(ip.Unmap(), uint16(port)).String()
		r.SNI = e.CustomSNI
		if r.SNI == "" {
			r.SNI = e.Hostname
		}
		r.VerifyName = e.Hostname
		r.Username, r.Password = e.Username, e.Password
		r.HTTP3, r.SkipVerification, r.Certificate = e.HTTP3, e.SkipVerification, e.CertificatePEM
		return r, nil
	}
	return r, lastErr
}
