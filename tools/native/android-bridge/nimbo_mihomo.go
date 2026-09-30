package libXray

import (
	"encoding/json"

	mihomo "nimbo/mihomocore"
)

// NimboMihomoInvoke uses the same Go runtime and JNI library as LibXray.
// API versions are independent: LibXray remains API 3, Mihomo remains API 1.
func NimboMihomoInvoke(request string) string {
	return mihomo.Invoke(request)
}

// NimboMihomoCancel cancels the named request without waiting for the operation
// lock. The request ID is encoded, not interpolated into caller-provided JSON.
func NimboMihomoCancel(requestID string) string {
	request, _ := json.Marshal(map[string]any{
		"apiVersion": 1, "requestId": "android-cancel", "operation": "cancel",
		"targetRequestId": requestID,
	})
	return mihomo.Invoke(string(request))
}
