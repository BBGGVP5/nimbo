package router

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/xtls/xray-core/app/observatory"
	"google.golang.org/protobuf/proto"
)

type nimboStickyObserver struct {
	report proto.Message
	err    error
}

func (*nimboStickyObserver) Type() interface{} { return nil }
func (*nimboStickyObserver) Start() error      { return nil }
func (*nimboStickyObserver) Close() error      { return nil }
func (o *nimboStickyObserver) GetObservation(context.Context) (proto.Message, error) {
	return o.report, o.err
}

func TestNimboStickyAdapter(t *testing.T) {
	now := time.Unix(10_000, 0)
	o := &nimboStickyObserver{}
	s := &nimboStickyStrategy{LeastPingStrategy: LeastPingStrategy{ctx: context.Background(), observatory: o}, now: func() time.Time { return now }}
	tags := []string{"proxy/0", "proxy/1"}
	if got := s.PickOutbound(tags); got != "" {
		t.Fatalf("unknown startup selected %q", got)
	}
	o.report = &observatory.ObservationResult{Status: []*observatory.OutboundStatus{
		nil, {OutboundTag: "foreign", Alive: true, Delay: 0, LastTryTime: now.Unix()},
		{OutboundTag: "proxy/1", Alive: true, Delay: 30, LastTryTime: now.Unix()},
	}}
	if got := s.PickOutbound(tags); got != "proxy/1" {
		t.Fatalf("selected %q", got)
	}
	for _, report := range []proto.Message{nil, (*observatory.ObservationResult)(nil), &observatory.Config{}} {
		o.report = report
		if got := s.PickOutbound(tags); got != "proxy/1" {
			t.Fatalf("unknown report changed route: %q", got)
		}
	}
	o.err = errors.New("fixture")
	if got := s.GetPrincipleTarget(tags); len(got) != 1 || got[0] != "proxy/1" {
		t.Fatalf("error changed route: %v", got)
	}
}

func TestNimboStickyExactOptIn(t *testing.T) {
	for _, c := range []struct {
		tag, selector, fallback string
		sticky                  bool
	}{
		{"nimbo-auto-sticky-v1", "proxy/", "proxy/0", true},
		{"balancer", "proxy/", "proxy/0", false},
		{"nimbo-auto-sticky-v1", "foreign/", "proxy/0", false},
		{"nimbo-auto-sticky-v1", "proxy/", "direct", false},
	} {
		rule := &BalancingRule{Tag: c.tag, Strategy: "leastping", OutboundSelector: []string{c.selector}, FallbackTag: c.fallback}
		balancer, err := rule.Build(nil, nil)
		if err != nil {
			t.Fatal(err)
		}
		_, sticky := balancer.strategy.(*nimboStickyStrategy)
		if sticky != c.sticky {
			t.Fatalf("%+v: wrong strategy", c)
		}
		if !sticky {
			if _, ok := balancer.strategy.(*LeastPingStrategy); !ok {
				t.Fatal("ordinary leastPing changed")
			}
		}
	}
}
