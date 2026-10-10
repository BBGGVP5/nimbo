package main

import (
	"encoding/json"
	"strings"
)

const diagnosticTag = "nimbo-diagnostic-proxy"
const diagnosticDenyTag = "nimbo-diagnostic-deny"

// Project exactly one proxy and its proven terminal ClientHello fragmenter.
// Ambiguous virtual groups/chains remain unsupported, never first-member tests.
func diagnosticRawOutbounds(raws []json.RawMessage) ([]json.RawMessage, string) {
	var selected map[string]any
	byTag := make(map[string]map[string]any)
	for _, raw := range raws {
		var out map[string]any
		if json.Unmarshal(raw, &out) != nil || out == nil {
			return nil, "DIAGNOSTIC_CONFIG"
		}
		if tag, _ := out["tag"].(string); tag != "" {
			if byTag[tag] != nil || tag == diagnosticTag || tag == diagnosticDenyTag {
				return nil, "DIAGNOSTIC_UNSAFE_ROUTE"
			}
			byTag[tag] = out
		}
		switch out["protocol"] {
		case "freedom", "blackhole", "dns":
			continue
		case "vmess", "vless", "trojan", "shadowsocks", "socks", "http":
		default:
			return nil, "DIAGNOSTIC_UNSUPPORTED"
		}
		if selected != nil {
			return nil, "DIAGNOSTIC_AMBIGUOUS_ROUTE"
		}
		selected = out
	}
	if selected == nil {
		return nil, "DIAGNOSTIC_AMBIGUOUS_ROUTE"
	}
	if bind, exists := selected["sendThrough"]; exists && bind != nil && bind != "" {
		return nil, "DIAGNOSTIC_UNSUPPORTED_BIND"
	}
	if selected["proxySettings"] != nil {
		return nil, "DIAGNOSTIC_UNSAFE_ROUTE"
	}
	var helper map[string]any
	stream, _ := selected["streamSettings"].(map[string]any)
	sockopt, _ := stream["sockopt"].(map[string]any)
	if dialer, exists := sockopt["dialerProxy"]; exists && dialer != nil && dialer != "" {
		tag, ok := dialer.(string)
		if !ok {
			return nil, "DIAGNOSTIC_UNSAFE_ROUTE"
		}
		helper = byTag[tag]
		if !diagnosticTerminalFragmenter(helper) {
			return nil, "DIAGNOSTIC_UNSAFE_ROUTE"
		}
		// Remove only the validated reference while recursively checking all other
		// fields (including XHTTP downloadSettings), then restore the exact value.
		delete(sockopt, "dialerProxy")
		unsafe := diagnosticUnsafeFields(selected)
		sockopt["dialerProxy"] = dialer
		if unsafe {
			return nil, "DIAGNOSTIC_UNSAFE_ROUTE"
		}
	} else if diagnosticUnsafeFields(selected) {
		return nil, "DIAGNOSTIC_UNSAFE_ROUTE"
	}
	selected["tag"] = diagnosticTag
	proxy, err := json.Marshal(selected)
	if err != nil {
		return nil, "DIAGNOSTIC_CONFIG"
	}
	result := []json.RawMessage{json.RawMessage(`{"tag":"nimbo-diagnostic-deny","protocol":"blackhole"}`), proxy}
	if helper != nil {
		raw, err := json.Marshal(helper)
		if err != nil {
			return nil, "DIAGNOSTIC_CONFIG"
		}
		result = append(result, raw)
	}
	return result, ""
}

func diagnosticTerminalFragmenter(out map[string]any) bool {
	if out == nil || out["protocol"] != "freedom" || diagnosticUnsafeFields(out) {
		return false
	}
	for key := range out {
		switch key {
		case "tag", "protocol", "settings", "streamSettings":
		default:
			return false
		}
	}
	settings, ok := out["settings"].(map[string]any)
	fragment, fragmentOK := settings["fragment"].(map[string]any)
	if !ok || !fragmentOK || len(fragment) == 0 {
		return false
	}
	for key := range settings {
		switch key {
		case "fragment", "noises", "domainStrategy":
		default:
			return false
		}
	}
	if raw, exists := out["streamSettings"]; exists {
		stream, ok := raw.(map[string]any)
		if !ok {
			return false
		}
		for key := range stream {
			if key != "sockopt" {
				return false
			}
		}
	}
	return true
}

func diagnosticUnsafeFields(value any) bool {
	switch v := value.(type) {
	case map[string]any:
		for key, child := range v {
			switch strings.ToLower(key) {
			case "certificatefile", "keyfile", "masterkeylog", "secretslog", "dialerproxy", "interface":
				if child != nil && child != "" {
					return true
				}
			}
			if diagnosticUnsafeFields(child) {
				return true
			}
		}
	case []any:
		for _, child := range v {
			if diagnosticUnsafeFields(child) {
				return true
			}
		}
	}
	return false
}
