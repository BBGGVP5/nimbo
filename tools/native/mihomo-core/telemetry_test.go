package mihomocore

import (
	"encoding/json"
	"github.com/metacubex/mihomo/tunnel/statistic"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestTelemetryMeasuresActualDirectRouteAndRejectsStaleGeneration(t *testing.T) {
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte("telemetry-loopback-response"))
	}))
	defer target.Close()
	started, info := startTest(t, simpleConfig)
	if status, _, err := proxyGet(info.MixedAddress, target.URL); err != nil || status != 200 {
		t.Fatalf("loopback request: %d %v", status, err)
	}
	reply := call(t, "telemetry", map[string]any{"generation": started.Generation})
	requireOK(t, reply)
	var got trafficTelemetry
	if err := json.Unmarshal(reply.Data, &got); err != nil {
		t.Fatal(err)
	}
	if !got.RouteAvailable || got.DirectDownload == 0 || got.ProxyDownload != 0 || got.Download == 0 {
		t.Fatalf("actual direct bytes were not classified: %+v", got)
	}
	stale := call(t, "telemetry", map[string]any{"generation": started.Generation + 1})
	if stale.Success || stale.Error == nil || stale.Error.Code != "STALE_GENERATION" {
		t.Fatal("stale telemetry accepted")
	}
	noGeneration := call(t, "telemetry", nil)
	if noGeneration.Success {
		t.Fatal("unbound telemetry accepted")
	}
}

func TestTelemetryIsSessionScopedAndDoesNotResetCounters(t *testing.T) {
	up, down := statistic.DefaultManager.Total()
	s := &session{}
	s.trafficBase = captureTrafficBaseline()
	statistic.DefaultManager.PushUploaded(37)
	statistic.DefaultManager.PushDownloaded(91)
	got := readTelemetry(s)
	if got.Upload != 37 || got.Download != 91 {
		t.Fatalf("wrong session totals: %+v", got)
	}
	next := readTelemetry(s)
	if next.Upload != got.Upload || next.Download != got.Download {
		t.Fatal("read changed counters")
	}
	u, d := statistic.DefaultManager.Total()
	if u != up+37 || d != down+91 {
		t.Fatal("telemetry reset global counters")
	}
	data, err := json.Marshal(got)
	if err != nil {
		t.Fatal(err)
	}
	var fields map[string]any
	if err = json.Unmarshal(data, &fields); err != nil {
		t.Fatal(err)
	}
	for key := range fields {
		if key == "connections" || key == "metadata" || key == "chains" {
			t.Fatal("private connection data leaked")
		}
	}
	fresh := &session{trafficBase: captureTrafficBaseline()}
	if got := readTelemetry(fresh); got.Upload != 0 || got.Download != 0 {
		t.Fatal("previous session leaked")
	}
}

func TestCounterDeltaHandlesRestartWithoutUnderflow(t *testing.T) {
	if counterDelta(4, 12) != 0 || counterDelta(14, 12) != 2 {
		t.Fatal("counter delta wrapped")
	}
}

func TestNativeRouteBytesAreCumulativeAndSurviveConnectionRemoval(t *testing.T) {
	routes, ok := any(statistic.DefaultManager).(interface {
		routeTotals
		PushRouteUploaded(bool, int64)
		PushRouteDownloaded(bool, int64)
	})
	if !ok {
		t.Fatal("pinned route-counter source patch missing")
	}
	s := &session{trafficBase: captureTrafficBaseline()}
	routes.PushRouteUploaded(false, 400)
	routes.PushRouteDownloaded(false, 800)
	routes.PushRouteUploaded(true, 30)
	routes.PushRouteDownloaded(true, 60)
	got := readTelemetry(s)
	if !got.RouteAvailable || got.ProxyUpload != 400 || got.ProxyDownload != 800 || got.DirectUpload != 30 || got.DirectDownload != 60 {
		t.Fatalf("route counters: %+v", got)
	}
	// There are no live trackers, but closed-connection bytes remain counted.
	again := readTelemetry(s)
	if again.ProxyUpload != 400 || again.DirectDownload != 60 {
		t.Fatal("route totals depended on current connections")
	}
}
