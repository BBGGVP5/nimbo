//go:build with_naive

package naivecore

import (
	"context"
	"github.com/miekg/dns"
	cronet "github.com/sagernet/cronet-go"
	M "github.com/sagernet/sing/common/metadata"
	"net"
	"strings"
	"time"
)

type nativeClient struct{ *cronet.NaiveClient }

func (c *nativeClient) DialContext(ctx context.Context, destination string) (net.Conn, error) {
	return c.NaiveClient.DialContext(ctx, "tcp", M.ParseSocksaddr(destination))
}
func newNativeClient(ctx context.Context, cfg Config) (Client, error) {
	c, e := cronet.NewNaiveClient(cronet.NaiveClientOptions{
		Context: ctx, ServerAddress: M.ParseSocksaddrHostPort(cfg.Host, cfg.Port), ServerName: cfg.ServerName,
		Username: cfg.Username, Password: cfg.Password, QUIC: cfg.QUIC,
		ReceiveWindow: 4 * 1024 * 1024, QUICSessionReceiveWindow: 8 * 1024 * 1024,
		DNSResolver: bootstrapResolver(cfg.Host),
	})
	if e != nil {
		return nil, e
	}
	if e = c.Start(); e != nil {
		c.Close()
		return nil, e
	}
	return &nativeClient{c}, nil
}

// Only the outer proxy name may be resolved on the physical network. CONNECT
// destination names are sent inside the encrypted tunnel, never looked up here.
func bootstrapResolver(host string) cronet.DNSResolverFunc {
	return func(ctx context.Context, q *dns.Msg) *dns.Msg {
		r := new(dns.Msg)
		r.SetReply(q)
		if len(q.Question) != 1 {
			r.Rcode = dns.RcodeRefused
			return r
		}
		question := q.Question[0]
		if !strings.EqualFold(strings.TrimSuffix(question.Name, "."), strings.TrimSuffix(host, ".")) || question.Qclass != dns.ClassINET {
			r.Rcode = dns.RcodeRefused
			return r
		}
		if question.Qtype != dns.TypeA && question.Qtype != dns.TypeAAAA {
			return r
		}
		ctx, cancel := context.WithTimeout(ctx, 10*time.Second)
		defer cancel()
		ips, e := net.DefaultResolver.LookupIPAddr(ctx, host)
		if e != nil {
			r.Rcode = dns.RcodeServerFailure
			return r
		}
		for _, a := range ips {
			header := dns.RR_Header{Name: question.Name, Rrtype: question.Qtype, Class: dns.ClassINET, Ttl: 60}
			if v4 := a.IP.To4(); question.Qtype == dns.TypeA && v4 != nil {
				r.Answer = append(r.Answer, &dns.A{Hdr: header, A: v4})
			} else if question.Qtype == dns.TypeAAAA && v4 == nil {
				r.Answer = append(r.Answer, &dns.AAAA{Hdr: header, AAAA: a.IP})
			}
		}
		return r
	}
}
