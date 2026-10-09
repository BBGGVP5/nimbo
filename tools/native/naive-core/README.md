# iOS Naive transport

In-process Chromium Naive CONNECT client via SagerNet cronet-go, pinned to commit `0d28acc44093df24b2526dea3d6ffefd6b0a54f0` (Chromium 150.0.7871.63). No URLSession substitute or child process. The Go bridge is compiled into the existing combined LibXray archive: one Go runtime per executable. Simulator and device libraries are separate modules; checksum verification is mandatory.

Local SOCKS5 requires per-session random credentials and binds only 127.0.0.1. At most 64 sessions; 15-second handshake deadlines; buffers 16 KiB/direction; native HTTP/2 receive window 4 MiB; one engine on iOS. Host/SNI are separate; platform certificate verification is unchanged. Bootstrap DNS only resolves the proxy endpoint; destination names remain inside CONNECT. Stop and network changes retire active sockets.

Standard Naive transports TCP only. Xray converts TUN DNS to TCP through the private SOCKS route. Generic UDP is rejected, never directly bypassed by this adapter. User-created direct routing remains a separate explicit policy. Naive is available for an individually selected share link under Auto/Xray, not injected into mixed Xray balancers or a full Mihomo document.

Tests: `go test ./...`; native macOS `go test -tags with_naive ./...`; native Windows `go test -tags with_naive,with_purego ./...` with the libcronet.dll from the identical pinned `lib/windows_amd64` Go module (not a version-matched release DLL) on the child process PATH. Windows checks do not validate iOS NetworkExtension behavior. Device tests must cover memory pressure, TLS, reconnect, sleep and Wi-Fi/cellular changes before release.

## Upstream notices and corresponding source
- cronet-go: https://github.com/SagerNet/cronet-go/tree/0d28acc44093df24b2526dea3d6ffefd6b0a54f0 — GPL-3.0-or-later; retained LICENSE.cronet-go.
- Chromium/NaiveProxy code in the static library: upstream root BSD-style licenses retained in `iosApp/NativeNotices`, alongside the complete GPL-3.0 text and corresponding source links. Audit transitive Chromium notices before a public release. The pinned Naive submodule is `72a06c9fca0e2d228588c7f3074bf7efff3ff686`.
- These dependencies are not a second independently loaded Go runtime. Neither the runtime nor build scripts log user URLs, credentials or TLS key material.
