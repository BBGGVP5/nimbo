package main

/*
#include <stdlib.h>
*/
import "C"

import (
	"encoding/json"
	"strings"
	"sync"

	awgcore "nimbo/awgcore"
)

// Compiled alongside upstream cgo_bridge/main.go, never into another archive.
// CGoFree (the unchanged upstream ABI) frees every returned C string.
var nimboAWG struct {
	sync.Mutex
	runtime *awgcore.Runtime
}

func awgResponse(value any) *C.char {
	data, err := json.Marshal(value)
	if err != nil {
		return C.CString(`{"ok":false,"error":"IOS_AWG_RESPONSE"}`)
	}
	return C.CString(string(data))
}

//export NimboAWGStart
func NimboAWGStart(requestJSON *C.char) *C.char {
	nimboAWG.Lock()
	defer nimboAWG.Unlock()
	if nimboAWG.runtime != nil {
		return awgResponse(map[string]any{"ok": false, "error": "IOS_AWG_ALREADY_RUNNING"})
	}
	var request struct {
		Config   string `json:"config"`
		Listen   string `json:"listen"`
		Username string `json:"username"`
		Password string `json:"password"`
	}
	if requestJSON == nil || json.Unmarshal([]byte(C.GoString(requestJSON)), &request) != nil ||
		len(request.Config) == 0 || len(request.Config) > 128*1024 ||
		len(request.Username) == 0 || len(request.Password) == 0 ||
		!strings.HasPrefix(request.Listen, "127.0.0.1:") {
		return awgResponse(map[string]any{"ok": false, "error": "IOS_AWG_REQUEST"})
	}
	runtime, err := awgcore.Start(request.Config, request.Listen, request.Username, request.Password)
	if err != nil {
		// Parser/network errors can carry keys, endpoints or raw INI values.
		return awgResponse(map[string]any{"ok": false, "error": "IOS_AWG_START_FAILED"})
	}
	nimboAWG.runtime = runtime
	return awgResponse(map[string]any{"ok": true, "port": runtime.Port(), "version": awgcore.Version})
}

//export NimboAWGStop
func NimboAWGStop() {
	nimboAWG.Lock()
	defer nimboAWG.Unlock()
	if nimboAWG.runtime != nil {
		nimboAWG.runtime.Close()
		nimboAWG.runtime = nil
	}
}

//export NimboAWGStats
func NimboAWGStats() *C.char {
	nimboAWG.Lock()
	defer nimboAWG.Unlock()
	if nimboAWG.runtime == nil {
		return awgResponse(map[string]any{"ok": true, "running": false, "version": awgcore.Version})
	}
	stats, err := nimboAWG.runtime.Stats()
	if err != nil {
		return awgResponse(map[string]any{"ok": false, "error": "IOS_AWG_STATS_FAILED"})
	}
	result := safeAWGCounters(stats)
	result["ok"] = true
	result["running"] = true
	result["version"] = awgcore.Version
	return awgResponse(result)
}
