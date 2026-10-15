package naivecore

import "testing"

func TestParse(t *testing.T) {
	c, e := Parse("naive+https://a+b:p%3A%2B%40@192.0.2.1:8443?peer=proxy.example#Human readable name")
	if e != nil || c.Host != "192.0.2.1" || c.ServerName != "proxy.example" || c.Username != "a+b" || c.Password != "p:+@" || c.Port != 8443 || c.QUIC {
		t.Fatalf("valid link failed: %v", e)
	}
	c, e = Parse("naive+quic://u:p@[2001:db8::1]?sni=secure.example")
	if e != nil || !c.QUIC || c.Port != 443 || c.Host != "2001:db8::1" {
		t.Fatal("IPv6/QUIC failed")
	}
}
func TestRejectConfigWithoutEchoingSecrets(t *testing.T) {
	for _, s := range []string{"vless://secret:secret@host", "naive://u@host", "naive://u:p@host:0", "naive://u:p@host:", "naive://u:p@host:65536", "naive://u:p@host/path", "naive://u:p@host?peer=x%2C%20EXCLUDE%20*", "naive://u:p@host?peer=bad%00name", "naive://u:%zz@host", "naive://u:p@host\nnaive://u:p@other", "naive://u:p@host?peer=a..b", "naive://u:p@host#name\nnaive://u:p@other"} {
		if _, e := Parse(s); e != ErrConfig {
			t.Fatal("invalid link must return generic config error")
		}
	}
}
