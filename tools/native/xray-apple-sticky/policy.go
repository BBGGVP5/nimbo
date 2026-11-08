package router

import (
	"sync"
	"time"
)

const (
	nimboStickyFreshness    = 75 * time.Second
	nimboStickyMinDwell     = 90 * time.Second
	nimboStickyCooldown     = 5 * time.Minute
	nimboStickyFailureLimit = 3
)

// nimboStickySample is a copied observation. LastTryTime is Unix seconds.
// The adapter filters nil observations before constructing samples.
type nimboStickySample struct {
	Tag         string
	Alive       bool
	Delay       int64
	LastTryTime int64
}

// nimboStickyPolicy belongs to one core instance. Its zero value is ready to
// use; it must not be copied after first use. Callers retain ownership of inputs
// and must not mutate their slices during pick.
type nimboStickyPolicy struct {
	mu              sync.Mutex
	current         string
	selectedAt      time.Time
	lastObservation int64
	failures        int
	cooldown        map[string]time.Time
	lastChecked     time.Time
	hasChecked      bool
}

type nimboStickyObservation struct {
	sample   nimboStickySample
	conflict bool
}

func (o nimboStickyObservation) fresh(now time.Time) bool {
	if o.conflict || o.sample.LastTryTime <= 0 {
		return false
	}
	age := now.Sub(time.Unix(o.sample.LastTryTime, 0))
	return age >= 0 && age <= nimboStickyFreshness
}

func (s *nimboStickyPolicy) pick(candidates []string, samples []nimboStickySample, now time.Time) string {
	s.mu.Lock()
	defer s.mu.Unlock()

	members := make(map[string]struct{}, len(candidates))
	for _, tag := range candidates {
		if tag != "" {
			members[tag] = struct{}{}
		}
	}
	// Membership is authoritative, including during clock rollback. Retained
	// metadata is bounded by the candidate set, even if pools exceed 16 nodes.
	for tag := range s.cooldown {
		if _, ok := members[tag]; !ok {
			delete(s.cooldown, tag)
		}
	}
	if _, ok := members[s.current]; !ok {
		s.current = ""
		s.selectedAt = time.Time{}
		s.lastObservation = 0
		s.failures = 0
	}
	// Do not use rolled-back time to gather evidence, expire cooldowns, or pick
	// a replacement. time.Time comparisons preserve monotonic clock readings.
	if s.hasChecked && now.Before(s.lastChecked) {
		return s.current
	}
	s.lastChecked, s.hasChecked = now, true
	for tag, until := range s.cooldown {
		if !now.Before(until) {
			delete(s.cooldown, tag)
		}
	}

	observations := make(map[string]nimboStickyObservation, len(members))
	for _, sample := range samples {
		if _, ok := members[sample.Tag]; !ok || sample.LastTryTime <= 0 {
			continue
		}
		previous, ok := observations[sample.Tag]
		switch {
		case !ok || sample.LastTryTime > previous.sample.LastTryTime:
			observations[sample.Tag] = nimboStickyObservation{sample: sample}
		case sample.LastTryTime == previous.sample.LastTryTime && sample != previous.sample:
			// A disagreement at the newest timestamp is unknown. Never repair
			// it by choosing the healthier duplicate or older healthy data.
			previous.conflict = true
			observations[sample.Tag] = previous
		}
	}

	best := func() string {
		var tag string
		var delay int64
		for _, candidate := range candidates {
			if candidate == "" || candidate == s.current {
				continue
			}
			if until, ok := s.cooldown[candidate]; ok && now.Before(until) {
				continue
			}
			observation := observations[candidate]
			if !observation.fresh(now) || !observation.sample.Alive || observation.sample.Delay < 0 {
				continue
			}
			if tag == "" || observation.sample.Delay < delay {
				tag, delay = candidate, observation.sample.Delay
			}
		}
		return tag
	}
	selectTag := func(tag string) {
		s.current = tag
		s.selectedAt = now
		s.lastObservation = observations[tag].sample.LastTryTime
		s.failures = 0
	}
	if s.current == "" {
		if tag := best(); tag != "" {
			selectTag(tag)
		}
		return s.current
	}

	observation := observations[s.current]
	if !observation.fresh(now) || observation.sample.LastTryTime < s.lastObservation {
		return s.current
	}
	sample := observation.sample
	if sample.LastTryTime > s.lastObservation {
		s.lastObservation = sample.LastTryTime
		switch {
		case sample.Alive && sample.Delay >= 0:
			s.failures = 0
		case !sample.Alive && s.failures < nimboStickyFailureLimit:
			s.failures++
		}
	}
	// Missing, invalid, or healthy observations cannot trigger failover. A
	// repeated failure can satisfy elapsed dwell, but never adds failure count.
	if sample.Alive || s.failures < nimboStickyFailureLimit || now.Sub(s.selectedAt) < nimboStickyMinDwell {
		return s.current
	}
	if tag := best(); tag != "" {
		if s.cooldown == nil {
			s.cooldown = make(map[string]time.Time)
		}
		s.cooldown[s.current] = now.Add(nimboStickyCooldown)
		selectTag(tag)
	}
	return s.current
}
