package router

import (
	"fmt"
	"sync"
	"testing"
	"time"
)

var nimboTestEpoch = time.Unix(2_000_000_000, 0)

func nimboTestTime(seconds int64) time.Time {
	return nimboTestEpoch.Add(time.Duration(seconds) * time.Second)
}

func nimboTestSample(tag string, alive bool, delay, seconds int64) nimboStickySample {
	return nimboStickySample{Tag: tag, Alive: alive, Delay: delay, LastTryTime: nimboTestTime(seconds).Unix()}
}

func nimboTestPick(t *testing.T, s *nimboStickyPolicy, seconds int64, want string, samples ...nimboStickySample) {
	t.Helper()
	if got := s.pick([]string{"a", "b"}, samples, nimboTestTime(seconds)); got != want {
		t.Fatalf("t=%d: pick=%q, want %q", seconds, got, want)
	}
}

func nimboTestStart(t *testing.T) *nimboStickyPolicy {
	t.Helper()
	s := new(nimboStickyPolicy)
	nimboTestPick(t, s, 0, "a", nimboTestSample("a", true, 10, 0), nimboTestSample("b", true, 20, 0))
	return s
}

func TestNimboStickyInitialVerification(t *testing.T) {
	a := nimboTestSample("a", true, 10, 0)
	b := nimboTestSample("b", true, 20, 0)
	cases := []struct {
		name       string
		candidates []string
		samples    []nimboStickySample
		want       string
	}{
		{"none", []string{"a", "b"}, nil, ""},
		{"no members", nil, []nimboStickySample{a}, ""},
		{"best", []string{"b", "a"}, []nimboStickySample{b, a}, "a"},
		{"stable tie", []string{"b", "a"}, []nimboStickySample{a, nimboTestSample("b", true, 10, 0)}, "b"},
		{"foreign", []string{"a"}, []nimboStickySample{nimboTestSample("direct", true, 0, 0)}, ""},
		{"failed", []string{"a"}, []nimboStickySample{nimboTestSample("a", false, -1, 0)}, ""},
		{"negative alive delay", []string{"a"}, []nimboStickySample{nimboTestSample("a", true, -1, 0)}, ""},
		{"zero delay valid", []string{"a"}, []nimboStickySample{nimboTestSample("a", true, 0, 0)}, "a"},
		{"zero timestamp", []string{"a"}, []nimboStickySample{{Tag: "a", Alive: true, Delay: 1}}, ""},
		{"negative timestamp", []string{"a"}, []nimboStickySample{{Tag: "a", Alive: true, Delay: 1, LastTryTime: -1}}, ""},
		{"stale", []string{"a"}, []nimboStickySample{nimboTestSample("a", true, 1, -76)}, ""},
		{"future", []string{"a"}, []nimboStickySample{nimboTestSample("a", true, 1, 1)}, ""},
		{"empty tag", []string{"", "a"}, []nimboStickySample{nimboTestSample("", true, 0, 0), a}, "a"},
		{"duplicate candidates", []string{"b", "b", "a"}, []nimboStickySample{a, nimboTestSample("b", true, 10, 0)}, "b"},
		{"identical duplicates", []string{"a"}, []nimboStickySample{a, a}, "a"},
		{"alive conflict", []string{"a", "b"}, []nimboStickySample{a, nimboTestSample("a", false, 10, 0), b}, "b"},
		{"delay conflict", []string{"a", "b"}, []nimboStickySample{a, nimboTestSample("a", true, 11, 0), b}, "b"},
		{"invalid conflict", []string{"a", "b"}, []nimboStickySample{a, nimboTestSample("a", true, -1, 0), b}, "b"},
		{"newest failure", []string{"a", "b"}, []nimboStickySample{nimboTestSample("a", true, 1, -1), nimboTestSample("a", false, -1, 0), b}, "b"},
		{"newest success", []string{"a", "b"}, []nimboStickySample{nimboTestSample("a", false, -1, -1), a, b}, "a"},
		{"newest invalid hides old health", []string{"a", "b"}, []nimboStickySample{nimboTestSample("a", true, 1, -1), nimboTestSample("a", true, -1, 0), b}, "b"},
		{"future hides old health", []string{"a", "b"}, []nimboStickySample{a, nimboTestSample("a", true, 1, 1), b}, "b"},
		{"older conflict irrelevant", []string{"a"}, []nimboStickySample{nimboTestSample("a", true, 1, -1), nimboTestSample("a", false, -1, -1), a}, "a"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			// Order of the adapter's samples cannot resolve a conflicting duplicate.
			for order := 0; order < 2; order++ {
				samples := append([]nimboStickySample(nil), tc.samples...)
				if order == 1 {
					for i, j := 0, len(samples)-1; i < j; i, j = i+1, j-1 {
						samples[i], samples[j] = samples[j], samples[i]
					}
				}
				if got := new(nimboStickyPolicy).pick(tc.candidates, samples, nimboTestTime(0)); got != tc.want {
					t.Fatalf("order %d: got %q, want %q", order, got, tc.want)
				}
			}
		})
	}
}

func TestNimboStickyFreshnessBoundary(t *testing.T) {
	for _, tc := range []struct {
		offset time.Duration
		want   string
	}{
		{0, "a"}, {75 * time.Second, "a"}, {75*time.Second + time.Nanosecond, ""}, {-time.Nanosecond, ""},
	} {
		if got := new(nimboStickyPolicy).pick([]string{"a"}, []nimboStickySample{nimboTestSample("a", true, 1, 0)}, nimboTestTime(0).Add(tc.offset)); got != tc.want {
			t.Fatalf("age %v: got %q, want %q", tc.offset, got, tc.want)
		}
	}
}

func TestNimboStickyHealthyLatencyOscillation(t *testing.T) {
	s := nimboTestStart(t)
	for second := int64(1); second <= 600; second++ {
		nimboTestPick(t, s, second, "a", nimboTestSample("a", true, 500+second%100, second), nimboTestSample("b", true, second%2, second))
	}
}

func TestNimboStickyDistinctFailuresAndDwell(t *testing.T) {
	s := nimboTestStart(t)
	for _, second := range []int64{80, 85, 89} {
		nimboTestPick(t, s, second, "a", nimboTestSample("a", false, -1, second), nimboTestSample("b", true, 1, second))
	}
	if s.failures != 3 {
		t.Fatalf("failures=%d, want 3", s.failures)
	}
	nimboTestPick(t, s, 90, "b", nimboTestSample("a", false, -1, 89), nimboTestSample("b", true, 1, 90))
	if s.failures != 0 {
		t.Fatalf("new selection retained failures: %d", s.failures)
	}
}

func TestNimboStickyRepeatedAndOutOfOrderFailures(t *testing.T) {
	s := nimboTestStart(t)
	for i := 0; i < 1000; i++ {
		nimboTestPick(t, s, 90, "a", nimboTestSample("a", false, -1, 90), nimboTestSample("b", true, 1, 90))
	}
	nimboTestPick(t, s, 91, "a", nimboTestSample("a", false, -1, 89), nimboTestSample("b", true, 1, 91))
	if s.failures != 1 {
		t.Fatalf("duplicate/out-of-order failures=%d, want 1", s.failures)
	}
	nimboTestPick(t, s, 92, "a", nimboTestSample("a", false, -1, 92), nimboTestSample("b", true, 1, 92))
	nimboTestPick(t, s, 93, "b", nimboTestSample("a", false, -1, 93), nimboTestSample("b", true, 1, 93))
}

func TestNimboStickySuccessResetsFailures(t *testing.T) {
	s := nimboTestStart(t)
	for _, second := range []int64{90, 91} {
		nimboTestPick(t, s, second, "a", nimboTestSample("a", false, -1, second))
	}
	nimboTestPick(t, s, 92, "a", nimboTestSample("a", true, 900, 92), nimboTestSample("b", true, 1, 92))
	if s.failures != 0 {
		t.Fatalf("success did not reset failures: %d", s.failures)
	}
	for _, second := range []int64{93, 94} {
		nimboTestPick(t, s, second, "a", nimboTestSample("a", false, -1, second), nimboTestSample("b", true, 1, second))
	}
	nimboTestPick(t, s, 95, "b", nimboTestSample("a", false, -1, 95), nimboTestSample("b", true, 1, 95))
}

func TestNimboStickyUnknownDoesNotCountOrReset(t *testing.T) {
	s := nimboTestStart(t)
	nimboTestPick(t, s, 90, "a", nimboTestSample("a", false, -1, 90))
	unknown := [][]nimboStickySample{
		nil,
		{nimboTestSample("a", false, -1, 1)},
		{nimboTestSample("a", false, -1, 200)},
		{nimboTestSample("a", true, -1, 91)},
		{nimboTestSample("foreign", false, -1, 91)},
		{nimboTestSample("a", true, 1, 92), nimboTestSample("a", false, -1, 92)},
	}
	for _, samples := range unknown {
		nimboTestPick(t, s, 100, "a", append(samples, nimboTestSample("b", true, 1, 100))...)
		if s.failures != 1 {
			t.Fatalf("unknown changed failures: %d", s.failures)
		}
	}
	nimboTestPick(t, s, 101, "a", nimboTestSample("a", false, -1, 101), nimboTestSample("b", true, 1, 101))
	nimboTestPick(t, s, 102, "b", nimboTestSample("a", false, -1, 102), nimboTestSample("b", true, 1, 102))
}

func TestNimboStickyNoAlternateAndUnknownCurrent(t *testing.T) {
	s := nimboTestStart(t)
	for second := int64(90); second <= 200; second++ {
		nimboTestPick(t, s, second, "a", nimboTestSample("a", false, -1, second), nimboTestSample("direct", true, 0, second), nimboTestSample("b", true, -1, second))
	}
	if len(s.cooldown) != 0 {
		t.Fatal("cooldown started without departure")
	}
	if s.failures != 3 {
		t.Fatalf("failure counter must saturate: %d", s.failures)
	}
	// Three historical failures are insufficient when current evidence is unknown.
	for _, samples := range [][]nimboStickySample{
		{nimboTestSample("b", true, 1, 300)},
		{nimboTestSample("a", false, -1, 200), nimboTestSample("b", true, 1, 300)},
		{nimboTestSample("a", false, -1, 301), nimboTestSample("b", true, 1, 300)},
		{nimboTestSample("a", false, -1, 300), nimboTestSample("a", true, 1, 300), nimboTestSample("b", true, 1, 300)},
	} {
		nimboTestPick(t, s, 300, "a", samples...)
	}
	nimboTestPick(t, s, 301, "b", nimboTestSample("a", false, -1, 301), nimboTestSample("b", true, 1, 301))
}

func TestNimboStickyCooldownAndSwitchOscillation(t *testing.T) {
	s := nimboTestStart(t)
	for _, second := range []int64{88, 89} {
		nimboTestPick(t, s, second, "a", nimboTestSample("a", false, -1, second), nimboTestSample("b", true, 1, second))
	}
	nimboTestPick(t, s, 90, "b", nimboTestSample("a", false, -1, 90), nimboTestSample("b", true, 1, 90))
	for _, second := range []int64{180, 181, 182, 389} {
		nimboTestPick(t, s, second, "b", nimboTestSample("a", true, 1, second), nimboTestSample("b", false, -1, second))
	}
	nimboTestPick(t, s, 390, "a", nimboTestSample("a", true, 1, 390), nimboTestSample("b", false, -1, 390))
	for _, second := range []int64{480, 481, 482, 689} {
		nimboTestPick(t, s, second, "a", nimboTestSample("a", false, -1, second), nimboTestSample("b", true, 1, second))
	}
	nimboTestPick(t, s, 690, "b", nimboTestSample("a", false, -1, 690), nimboTestSample("b", true, 1, 690))
}

func TestNimboStickyRemovedCurrentAndBoundedMetadata(t *testing.T) {
	s := nimboTestStart(t)
	for _, second := range []int64{88, 89} {
		nimboTestPick(t, s, second, "a", nimboTestSample("a", false, -1, second))
	}
	nimboTestPick(t, s, 90, "b", nimboTestSample("a", false, -1, 90), nimboTestSample("b", true, 1, 90))
	if len(s.cooldown) != 1 {
		t.Fatal("missing departure cooldown")
	}
	if got := s.pick([]string{"b"}, nil, nimboTestTime(91)); got != "b" {
		t.Fatalf("got %q", got)
	}
	if len(s.cooldown) != 0 {
		t.Fatal("removed candidate retained metadata")
	}
	if got := s.pick([]string{"a"}, []nimboStickySample{nimboTestSample("a", true, 1, 92)}, nimboTestTime(92)); got != "a" {
		t.Fatalf("replacement=%q", got)
	}
	if s.failures != 0 || !s.selectedAt.Equal(nimboTestTime(92)) {
		t.Fatal("replacement did not reset selection evidence")
	}
	if got := s.pick([]string{"c"}, nil, nimboTestTime(93)); got != "" {
		t.Fatalf("unverified replacement=%q", got)
	}
	if got := s.pick(nil, nil, nimboTestTime(94)); got != "" {
		t.Fatalf("empty membership=%q", got)
	}
	if len(s.cooldown) != 0 || s.failures != 0 || s.lastObservation != 0 {
		t.Fatal("empty membership retained metadata")
	}
	// Pools above the app's usual 16 entries remain safe; do not silently drop a member.
	var members []string
	for i := 0; i < 100; i++ {
		members = append(members, fmt.Sprintf("node-%d", i))
	}
	if got := s.pick(members, []nimboStickySample{nimboTestSample("node-99", true, 1, 95)}, nimboTestTime(95)); got != "node-99" {
		t.Fatalf("large pool pick=%q", got)
	}
}

func TestNimboStickyClockRollbackAndFutureEvidence(t *testing.T) {
	s := nimboTestStart(t)
	for _, second := range []int64{90, 91, 100} {
		nimboTestPick(t, s, second, "a", nimboTestSample("a", false, -1, second))
	}
	nimboTestPick(t, s, 99, "a", nimboTestSample("a", false, -1, 99), nimboTestSample("b", true, 1, 99))
	if s.failures != 3 {
		t.Fatal("rollback changed failure evidence")
	}
	nimboTestPick(t, s, 100, "b", nimboTestSample("a", false, -1, 100), nimboTestSample("b", true, 1, 100))
	if got := s.pick([]string{"c"}, []nimboStickySample{nimboTestSample("c", true, 1, 99)}, nimboTestTime(99)); got != "" {
		t.Fatalf("rollback selected removed or new route %q", got)
	}
	if got := s.pick([]string{"c"}, []nimboStickySample{nimboTestSample("c", true, 1, 100)}, nimboTestTime(100)); got != "c" {
		t.Fatalf("catchup=%q", got)
	}
	// Future samples must not poison the timestamp high-water mark.
	s = nimboTestStart(t)
	nimboTestPick(t, s, 90, "a", nimboTestSample("a", false, -1, 10000), nimboTestSample("b", true, 1, 90))
	for _, second := range []int64{91, 92} {
		nimboTestPick(t, s, second, "a", nimboTestSample("a", false, -1, second), nimboTestSample("b", true, 1, second))
	}
	nimboTestPick(t, s, 93, "b", nimboTestSample("a", false, -1, 93), nimboTestSample("b", true, 1, 93))
}

func TestNimboStickySameCandidateConcurrency(t *testing.T) {
	s := nimboTestStart(t)
	var wg sync.WaitGroup
	for worker := 0; worker < 32; worker++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for i := 0; i < 100; i++ {
				if got := s.pick([]string{"a", "b"}, []nimboStickySample{nimboTestSample("a", false, -1, 90), nimboTestSample("b", true, 1, 90)}, nimboTestTime(90)); got != "a" {
					t.Errorf("same probe switched to %q", got)
					return
				}
			}
		}()
	}
	wg.Wait()
	if s.failures != 1 {
		t.Fatalf("concurrent duplicate failures=%d", s.failures)
	}
	nimboTestPick(t, s, 91, "a", nimboTestSample("a", false, -1, 91), nimboTestSample("b", true, 1, 91))
	nimboTestPick(t, s, 92, "b", nimboTestSample("a", false, -1, 92), nimboTestSample("b", true, 1, 92))
}

func TestNimboStickyConcurrentMembershipAndIndependentCores(t *testing.T) {
	s := new(nimboStickyPolicy)
	var wg sync.WaitGroup
	for worker := 0; worker < 16; worker++ {
		wg.Add(1)
		go func(worker int) {
			defer wg.Done()
			tag := fmt.Sprintf("node-%d", worker)
			for i := 0; i < 100; i++ {
				if got := s.pick([]string{tag}, []nimboStickySample{nimboTestSample(tag, true, 1, 0)}, nimboTestTime(0)); got != tag {
					t.Errorf("got foreign selection %q for %q", got, tag)
					return
				}
			}
		}(worker)
	}
	wg.Wait()
	a, b := new(nimboStickyPolicy), new(nimboStickyPolicy)
	nimboTestPick(t, a, 0, "a", nimboTestSample("a", true, 1, 0))
	nimboTestPick(t, b, 0, "b", nimboTestSample("b", true, 1, 0))
	nimboTestPick(t, a, 1, "a", nimboTestSample("b", true, 0, 1))
}
