package main

import (
	"context"
	"encoding/json"
	"strings"

	"github.com/google/uuid"
	trusttunnelcore "nimbo/trusttunnelcore"
)

// Chained after any earlier hook (file order makes the Naive hook register
// first): a tt:// share link gets an independent, short-lived TrustTunnel
// runtime so the ordinary Xray diagnostic measures the real endpoint. It never
// touches the managed tunnel's instance.
func init() {
	previous := prepareDiagnosticTransport
	prepareDiagnosticTransport = func(ctx context.Context, request diagnosticRequest) (diagnosticRequest, func(), string) {
		config := strings.TrimSpace(request.Config)
		if request.Format != "share" || len(config) < 5 || !strings.EqualFold(config[:5], "tt://") {
			if previous != nil {
				return previous(ctx, request)
			}
			return request, nil, ""
		}
		return prepareTrustTunnelDiagnostic(ctx, request, config)
	}
}

func prepareTrustTunnelDiagnostic(ctx context.Context, request diagnosticRequest, link string) (diagnosticRequest, func(), string) {
	if _, err := trusttunnelcore.ParseLink(link); err != nil {
		return request, nil, "DIAGNOSTIC_CONFIG"
	}
	if ctx.Err() != nil {
		return request, nil, "DIAGNOSTIC_CANCELLED"
	}
	user, pass := uuid.NewString(), uuid.NewString()+uuid.NewString()
	timeout := request.TimeoutMs
	if timeout <= 0 || timeout > 30000 {
		timeout = 10000
	}
	runtime, err := trusttunnelcore.Start(trusttunnelcore.Request{
		Link: link, SocksUsername: user, SocksPassword: pass, TimeoutMs: timeout,
	}, nil)
	if err != nil {
		return request, nil, "DIAGNOSTIC_SETUP"
	}
	stopCancel := context.AfterFunc(ctx, runtime.Close)
	cleanup := func() { stopCancel(); runtime.Close() }
	raw, err := json.Marshal(map[string]any{"outbounds": []any{map[string]any{
		"tag": "selected-trusttunnel", "protocol": "socks", "settings": map[string]any{"servers": []any{map[string]any{
			"address": "127.0.0.1", "port": runtime.Port(), "users": []any{map[string]any{"user": user, "pass": pass}},
		}}},
	}}})
	if err != nil {
		return request, cleanup, "DIAGNOSTIC_CONFIG"
	}
	request.Format, request.Config = "xray", string(raw)
	return request, cleanup, ""
}
