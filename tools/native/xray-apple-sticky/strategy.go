package router

import (
	"time"

	"github.com/xtls/xray-core/app/observatory"
)

// Only instantiated for Nimbo's generated Auto balancer. The core owns this
// state; UI suspension and individual packet/connection counts cannot drive it.
type nimboStickyStrategy struct {
	LeastPingStrategy
	policy nimboStickyPolicy
	now    func() time.Time // optional deterministic test clock
}

func (s *nimboStickyStrategy) PickOutbound(candidates []string) string {
	now := time.Now()
	if s.now != nil {
		now = s.now()
	}
	var samples []nimboStickySample
	if s.observatory != nil {
		report, err := s.observatory.GetObservation(s.ctx)
		if result, ok := report.(*observatory.ObservationResult); err == nil && ok && result != nil {
			for _, status := range result.Status {
				if status != nil {
					samples = append(samples, nimboStickySample{
						Tag: status.OutboundTag, Alive: status.Alive,
						Delay: status.Delay, LastTryTime: status.LastTryTime,
					})
				}
			}
		}
	}
	// Missing/error observations are unknown, not fabricated route failures.
	return s.policy.pick(candidates, samples, now)
}

func (s *nimboStickyStrategy) GetPrincipleTarget(candidates []string) []string {
	if tag := s.PickOutbound(candidates); tag != "" {
		return []string{tag}
	}
	return nil
}
