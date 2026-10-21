package main

/*
#include <stdlib.h>
#include <string.h>
*/
import "C"
import (
	trusttunnelcore "nimbo/trusttunnelcore"
)

// TrustTunnel shares this Go archive with Xray/AWG/Mihomo; it is Mihomo's
// protocol client exposed as an authenticated loopback SOCKS5 for Xray.
// Sockets created by the Packet Tunnel extension never re-enter its own
// tunnel, so iOS needs no protector. Every result is freed with CGoFree.

const trustTunnelMaxRequest = 64 * 1024

func trustTunnelRequest(p *C.char) (string, bool) {
	if p == nil || C.strnlen(p, trustTunnelMaxRequest+1) > trustTunnelMaxRequest {
		return "", false
	}
	return C.GoString(p), true
}

//export NimboTrustTunnelStart
func NimboTrustTunnelStart(p *C.char) *C.char {
	request, ok := trustTunnelRequest(p)
	if !ok {
		return C.CString(`{"ok":false,"error":"REQUEST"}`)
	}
	return C.CString(trusttunnelcore.StartJSON(request, nil))
}

//export NimboTrustTunnelStop
func NimboTrustTunnelStop() { trusttunnelcore.StopJSON() }

//export NimboTrustTunnelStatus
func NimboTrustTunnelStatus() *C.char { return C.CString(trusttunnelcore.StatusJSON()) }

//export NimboTrustTunnelNetworkChanged
func NimboTrustTunnelNetworkChanged() { trusttunnelcore.NetworkChanged() }

// NimboTrustTunnelProbe runs an independent authenticated health check; it never
// touches the running instance.
//
//export NimboTrustTunnelProbe
func NimboTrustTunnelProbe(p *C.char) *C.char {
	request, ok := trustTunnelRequest(p)
	if !ok {
		return C.CString(`{"ok":false,"error":"REQUEST"}`)
	}
	return C.CString(trusttunnelcore.ProbeJSON(request, nil))
}
