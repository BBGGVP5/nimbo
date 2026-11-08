//go:build with_naive

package main

import (
	"context"
	"encoding/json"
	"strings"

	"github.com/google/uuid"
	naivecore "nimbo/naivecore"
)

func init() { prepareDiagnosticTransport = prepareNaiveDiagnostic }

// An independent app-process client: never replace or stop the managed tunnel.
// The ordinary native diagnostic gate serializes probes and owns cancellation.
func prepareNaiveDiagnostic(ctx context.Context, request diagnosticRequest) (diagnosticRequest, func(), string) {
	scheme := strings.ToLower(strings.SplitN(strings.TrimSpace(request.Config), "://", 2)[0])
	if request.Format != "share" || (scheme != "naive" && scheme != "naive+https" && scheme != "naive+quic") {
		return request, nil, ""
	}
	if _, err := naivecore.Parse(request.Config); err != nil {
		return request, nil, "DIAGNOSTIC_CONFIG"
	}
	if ctx.Err() != nil {
		return request, nil, "DIAGNOSTIC_CANCELLED"
	}
	user, pass := uuid.NewString(), uuid.NewString()+uuid.NewString()
	runtime, err := naivecore.Start(request.Config, "127.0.0.1:0", user, pass)
	if err != nil {
		return request, nil, "DIAGNOSTIC_SETUP"
	}
	stopCancel := context.AfterFunc(ctx, runtime.Close)
	cleanup := func() { stopCancel(); runtime.Close() }
	raw, err := json.Marshal(map[string]any{"outbounds": []any{map[string]any{
		"tag": "selected-naive", "protocol": "socks", "settings": map[string]any{"servers": []any{map[string]any{
			"address": "127.0.0.1", "port": runtime.Port(), "users": []any{map[string]any{"user": user, "pass": pass}},
		}}},
	}}})
	if err != nil {
		return request, cleanup, "DIAGNOSTIC_CONFIG"
	}
	request.Format, request.Config = "xray", string(raw)
	return request, cleanup, ""
}
