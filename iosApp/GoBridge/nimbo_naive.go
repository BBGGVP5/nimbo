//go:build with_naive

package main

/*
#include <stdlib.h>
#include <string.h>
*/
import "C"
import (
	"encoding/json"
	naivecore "nimbo/naivecore"
	"sync"
)

// Part of the SAME Go archive as Xray/AWG/Mihomo, not another Go runtime.
var nimboNaive struct {
	sync.Mutex
	runtime *naivecore.Runtime
}

func naiveResponse(v any) *C.char {
	b, e := json.Marshal(v)
	if e != nil {
		return C.CString(`{"ok":false,"error":"IOS_NAIVE_RESPONSE"}`)
	}
	return C.CString(string(b))
}

//export NimboNaiveStart
func NimboNaiveStart(p *C.char) *C.char {
	nimboNaive.Lock()
	defer nimboNaive.Unlock()
	if nimboNaive.runtime != nil {
		return naiveResponse(map[string]any{"ok": false, "error": "IOS_NAIVE_ALREADY_RUNNING"})
	}
	var req struct {
		Link     string `json:"link"`
		Listen   string `json:"listen"`
		Username string `json:"username"`
		Password string `json:"password"`
	}
	if p == nil || C.strnlen(p, 32769) > 32768 || json.Unmarshal([]byte(C.GoString(p)), &req) != nil {
		return naiveResponse(map[string]any{"ok": false, "error": "IOS_NAIVE_REQUEST"})
	}
	r, e := naivecore.Start(req.Link, req.Listen, req.Username, req.Password)
	if e != nil {
		return naiveResponse(map[string]any{"ok": false, "error": "IOS_NAIVE_START_FAILED"})
	}
	nimboNaive.runtime = r
	return naiveResponse(map[string]any{"ok": true, "port": r.Port(), "version": naivecore.Version})
}

//export NimboNaiveStop
func NimboNaiveStop() {
	nimboNaive.Lock()
	defer nimboNaive.Unlock()
	if nimboNaive.runtime != nil {
		nimboNaive.runtime.Close()
		nimboNaive.runtime = nil
	}
}

//export NimboNaiveStatus
func NimboNaiveStatus() *C.char {
	nimboNaive.Lock()
	defer nimboNaive.Unlock()
	return naiveResponse(map[string]any{"ok": true, "running": nimboNaive.runtime != nil && nimboNaive.runtime.Running(), "version": naivecore.Version})
}

//export NimboNaiveResetConnections
func NimboNaiveResetConnections() {
	nimboNaive.Lock()
	defer nimboNaive.Unlock()
	if nimboNaive.runtime != nil {
		nimboNaive.runtime.NetworkChanged()
	}
}
