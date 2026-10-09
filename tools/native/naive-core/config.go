package naivecore

import (
	"errors"
	"net"
	"net/url"
	"strconv"
	"strings"
	"unicode"
)

var ErrConfig = errors.New("invalid NaiveProxy configuration")

const Version = "150.0.7871.63"

type Config struct {
	Host, ServerName, Username, Password string
	Port                                 uint16
	QUIC                                 bool
}

func Parse(link string) (Config, error) {
	fail := func() (Config, error) { return Config{}, ErrConfig }
	if len(link) > 16*1024 {
		return fail()
	}
	link = strings.TrimSpace(link)
	// Human-readable fragments may contain spaces or emoji, but are not transport data.
	link = strings.SplitN(link, "#", 2)[0]
	if strings.IndexFunc(link, unicode.IsSpace) >= 0 {
		return fail()
	}
	u, err := url.Parse(link)
	if err != nil || u.User == nil || (u.Path != "" && u.Path != "/") || u.Opaque != "" {
		return fail()
	}
	scheme := strings.ToLower(u.Scheme)
	if scheme != "naive" && scheme != "naive+https" && scheme != "naive+quic" {
		return fail()
	}
	password, ok := u.User.Password()
	if !ok || u.User.Username() == "" || password == "" || strings.ContainsAny(u.User.Username()+password, "\x00\r\n") {
		return fail()
	}
	host := u.Hostname()
	if !validHost(host) {
		return fail()
	}
	port := uint64(443)
	if strings.HasSuffix(u.Host, ":") {
		return fail()
	}
	if u.Port() != "" {
		port, err = strconv.ParseUint(u.Port(), 10, 16)
		if err != nil || port == 0 {
			return fail()
		}
	}
	q, err := url.ParseQuery(u.RawQuery)
	if err != nil {
		return fail()
	}
	name := q.Get("sni")
	if name == "" {
		name = q.Get("peer")
	}
	if name == "" {
		name = host
	}
	if !validHost(name) {
		return fail()
	}
	return Config{Host: host, Port: uint16(port), ServerName: name, Username: u.User.Username(), Password: password, QUIC: scheme == "naive+quic"}, nil
}
func validHost(host string) bool {
	if net.ParseIP(host) != nil {
		return true
	}
	if len(host) == 0 || len(host) > 253 {
		return false
	}
	for _, label := range strings.Split(strings.TrimSuffix(host, "."), ".") {
		if len(label) == 0 || len(label) > 63 || label[0] == '-' || label[len(label)-1] == '-' {
			return false
		}
		for _, r := range label {
			if !(r >= 'a' && r <= 'z' || r >= 'A' && r <= 'Z' || r >= '0' && r <= '9' || r == '-') {
				return false
			}
		}
	}
	return true
}
