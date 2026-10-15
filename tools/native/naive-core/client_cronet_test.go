//go:build with_naive

package naivecore

import (
	"context"
	"github.com/miekg/dns"
	"io"
	"log"
	"net/http"
	"net/http/httptest"
	"net/url"
	"sync/atomic"
	"testing"
	"time"
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

// Real Chromium must reject an untrusted proxy certificate before sending
// proxy credentials or a CONNECT destination. This is not a mock TLS stack.
func TestNativeRejectsUntrustedProxyTLS(t *testing.T) {
	var requests atomic.Int32
	server := httptest.NewUnstartedServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { requests.Add(1); w.WriteHeader(200) }))
	server.Config.ErrorLog = log.New(io.Discard, "", 0)
	server.StartTLS()
	defer server.Close()
	u, _ := url.Parse(server.URL)
	cfg, err := Parse("naive+https://fixture:secret@" + u.Host + "?peer=proxy.invalid")
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	c, err := newNativeClient(ctx, cfg)
	if err != nil {
		t.Fatal(err)
	}
	defer c.Close()
	conn, err := c.DialContext(ctx, "destination.invalid:443")
	if err == nil {
		conn.Close()
		t.Fatal("untrusted proxy accepted")
	}
	if requests.Load() != 0 {
		t.Fatal("request sent before certificate validation")
	}
}
