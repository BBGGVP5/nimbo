package observatory

import (
	"context"
	"sync"
	"testing"
)

func TestNimboStickyObservationSnapshot(t *testing.T) {
	o := &Observer{}
	o.updateStatusForResult("proxy/0", &ProbeResult{Alive: true, Delay: 12})
	report, err := o.GetObservation(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	snapshot := report.(*ObservationResult)
	o.updateStatusForResult("proxy/0", &ProbeResult{Alive: false})
	if !snapshot.Status[0].Alive || snapshot.Status[0].Delay != 12 {
		t.Fatal("snapshot aliases live status")
	}
	snapshot.Status[0].OutboundTag = "mutated"
	next, _ := o.GetObservation(context.Background())
	if next.(*ObservationResult).Status[0].OutboundTag != "proxy/0" {
		t.Fatal("reader mutated observer")
	}
}

func TestNimboStickyObservationConcurrentRead(t *testing.T) {
	o := &Observer{}
	var wg sync.WaitGroup
	for worker := 0; worker < 4; worker++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for i := 0; i < 100; i++ {
				o.updateStatusForResult("proxy/0", &ProbeResult{Alive: i%2 == 0, Delay: int64(i)})
				report, _ := o.GetObservation(context.Background())
				for _, status := range report.(*ObservationResult).Status {
					_ = status.Alive
				}
			}
		}()
	}
	wg.Wait()
}
