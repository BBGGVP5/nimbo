//go:build ios

package memory

import (
    "os"
    "runtime/debug"
    "sync"
)

var initForceFreeOnce sync.Once

// Keep upstream API intact without an immortal one-second forced-GC goroutine.
// This budget is for the combined runtime; it is not a guaranteed NE RSS cap.
func InitForceFree() {
    initForceFreeOnce.Do(func() {
        if os.Getenv("GOGC") == "" { debug.SetGCPercent(100) }
        if os.Getenv("GOMEMLIMIT") == "" { debug.SetMemoryLimit(30 << 20) }
    })
}
