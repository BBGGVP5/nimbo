package awgcore

import (
	"strings"
	"testing"
)

const testConfig = `[Interface]
PrivateKey = AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=
Address = 10.23.0.1/32, fd00::1/128
DNS = 1.1.1.1
Jc = 4
Jmin = 40
Jmax = 70
H1 = 12345-12346
HeaderProtectionKey = AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=
ContentPaddingAddition = 1-5
[Peer]
PublicKey = ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8=
Endpoint = [::1]:51820
AllowedIPs = 0.0.0.0/0, ::/0
PersistentKeepalive = 25
`

func TestParseConfig(t *testing.T) {
	c, e := ParseConfig(testConfig)
	if e != nil {
		t.Fatal(e)
	}
	if len(c.Addresses) != 2 || c.MTU != 1280 || c.Endpoint != "[::1]:51820" {
		t.Fatal("fields")
	}
	for _, s := range []string{"h1=12345-12346", "header_protection_key=00010203", "content_padding_addition=1-5", "allowed_ip=::/0"} {
		if !strings.Contains(c.UAPI, s) {
			t.Fatalf("missing %s", s)
		}
	}
	if strings.Index(c.UAPI, "jc=") > strings.Index(c.UAPI, "public_key=") {
		t.Fatal("device settings must precede peer")
	}
}

func TestHexKey(t *testing.T) {
	want := "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
	for _, value := range []string{want, strings.ToUpper(want), "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="} {
		got, err := keyHex(value)
		if err != nil || got != want {
			t.Fatalf("key conversion failed: %v", err)
		}
	}
}
func TestRejectConfig(t *testing.T) {
	for name, c := range map[string]string{
		"duplicate": testConfig + "[Peer]\n", "unknown": strings.Replace(testConfig, "Jc = 4", "PostUp = curl example", 1), "badKey": strings.Replace(testConfig, "PrivateKey = AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=", "PrivateKey = secret", 1), "mtu": strings.Replace(testConfig, "Jc = 4", "MTU = 99999", 1), "ipv6": strings.Replace(testConfig, "[::1]:51820", "::1:51820", 1), "port": strings.Replace(testConfig, "51820", "0", 1), "range": strings.Replace(testConfig, "Jmin = 40", "Jmin = 100", 1), "prefix": strings.Replace(testConfig, "0.0.0.0/0", "bad", 1), "duplicateKey": strings.Replace(testConfig, "Jc = 4", "Jc = 4\nJc = 5", 1), "injection": strings.Replace(testConfig, "H1 = 12345-12346", "H1 = 1234\nprivate_key=leak", 1), "large": strings.Repeat("x", MaxConfigBytes+1), "section": "PrivateKey=x"} {
		t.Run(name, func(t *testing.T) {
			_, e := ParseConfig(c)
			if e == nil {
				t.Fatal("accepted invalid config")
			}
			if strings.Contains(e.Error(), "secret") || strings.Contains(e.Error(), "leak") {
				t.Fatal("error leaks value")
			}
		})
	}
}
func TestAddressCodec(t *testing.T) {
	for _, s := range []string{"127.0.0.1:42", "[::1]:443", "example.com:53"} {
		b, e := addressBytes(s)
		if e != nil {
			t.Fatal(e)
		}
		got, e := readAddress(&byteReader{b: b})
		if e != nil || got != s {
			t.Fatalf("%s => %s %v", s, got, e)
		}
	}
}
