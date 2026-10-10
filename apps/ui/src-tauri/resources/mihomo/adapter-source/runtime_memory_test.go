package mihomocore

import (
    "encoding/json"
    "testing"
)

func TestRuntimeMemoryBudget(t *testing.T) {
    for platform, expected := range map[string]int64{"ios":30<<20,"android":96<<20,"windows":256<<20,"darwin":256<<20,"linux":256<<20} {
        if got := defaultRuntimeMemoryLimit(platform); got != expected { t.Fatalf("%s budget: %d", platform, got) }
    }
    for _, value := range []int64{-1,0,31,513,1<<40} {
        if validateRuntimeMemoryMB(value) == nil { t.Fatalf("invalid budget accepted: %d",value) }
    }
    for _, value := range []int64{32,96,160,512} {
        if err := validateRuntimeMemoryMB(value); err != nil { t.Fatal(err) }
    }
}

func TestRuntimeMemoryStatsAreNumericOnly(t *testing.T) {
    var stats map[string]uint64
    if err := json.Unmarshal([]byte(RuntimeMemoryStats()), &stats); err != nil { t.Fatal(err) }
    if len(stats) != 5 || stats["runtimeBytes"] == 0 { t.Fatalf("invalid stats: %v",stats) }
}
