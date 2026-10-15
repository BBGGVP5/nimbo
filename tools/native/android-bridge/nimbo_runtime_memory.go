package libXray

import mihomo "nimbo/mihomocore"

// Applies to the single combined Go runtime, not the Java heap.
func NimboConfigureRuntimeMemory(limitMB int64) string {
    return mihomo.ConfigureRuntimeMemory(limitMB)
}
func NimboRuntimeMemoryStats() string { return mihomo.RuntimeMemoryStats() }
