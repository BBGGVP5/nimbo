package mihomocore

import (
    "encoding/json"
    "fmt"
    "os"
    "runtime"
    "runtime/debug"
)

// Soft Go-runtime budget, not an RSS limit. Kernel buffers, Java/Swift heaps,
// mmap and external allocations are not included. Never force GC on a timer.
func defaultRuntimeMemoryLimit(platform string) int64 {
    switch platform {
    case "ios": return 30 << 20
    case "android": return 96 << 20
    default: return 256 << 20
    }
}

func init() {
    if os.Getenv("GOMEMLIMIT") == "" {
        debug.SetMemoryLimit(defaultRuntimeMemoryLimit(runtime.GOOS))
    }
    // Keep Go's normal pacing. A tiny GOGC saves heap by spending CPU/battery.
    // Explicit operator GOGC is respected rather than silently overwritten.
}

func validateRuntimeMemoryMB(limitMB int64) error {
    if limitMB < 32 || limitMB > 512 {
        return fmt.Errorf("runtime memory budget must be 32..512 MiB")
    }
    return nil
}

// ConfigureRuntimeMemory changes the ONE shared LibXray/Mihomo/AWG Go runtime.
// Trusted in-process Android entry only; never a YAML/provider/API1 operation.
func ConfigureRuntimeMemory(limitMB int64) string {
    if err := validateRuntimeMemoryMB(limitMB); err != nil {
        return `{"success":false,"error":"INVALID_MEMORY_BUDGET"}`
    }
    if runtime.GOOS != "android" {
        return `{"success":false,"error":"ANDROID_ONLY"}`
    }
    debug.SetMemoryLimit(limitMB << 20)
    return `{"success":true}`
}

// Numeric-only support counters, no node/config identifiers or heap dump.
func RuntimeMemoryStats() string {
    var value runtime.MemStats
    runtime.ReadMemStats(&value)
    data, _ := json.Marshal(map[string]uint64{
        "heapAllocBytes": value.HeapAlloc, "heapInuseBytes": value.HeapInuse,
        "heapReleasedBytes": value.HeapReleased,
        "runtimeBytes": value.Sys - value.HeapReleased, "gcCycles": uint64(value.NumGC),
    })
    return string(data)
}
