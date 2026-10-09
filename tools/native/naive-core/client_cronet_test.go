//go:build with_naive

package naivecore

import (
	"context"
	"github.com/miekg/dns"
	"testing"
)

func TestBootstrapResolverDoesNotResolveUserDestinations(t *testing.T) {
	q := new(dns.Msg)
	q.SetQuestion("user-destination.invalid.", dns.TypeA)
	r := bootstrapResolver("proxy.invalid")(context.Background(), q)
	if r.Rcode != dns.RcodeRefused {
		t.Fatal("user DNS escaped tunnel")
	}
}
func TestNativeEngineStartStop(t *testing.T) {
	for _, scheme := range []string{"naive+https", "naive+quic"} {
		r, e := Start(scheme+"://u:p@192.0.2.1?peer=proxy.invalid", "127.0.0.1:0", "nimbo", testPassword)
		if e != nil {
			t.Fatal(e)
		}
		if !r.Running() {
			t.Fatal("native engine not running")
		}
		r.NetworkChanged()
		r.Close()
		if r.Running() {
			t.Fatal("native engine not closed")
		}
	}
}
