# Mihomo V1 in the single LibXray/AWG Go runtime

The public iOS packet-flow owner is now integrated with NetworkExtension in
source: bounded raw IPv4/IPv6 packets, native Mihomo rules/groups/DNS and physical
interface socket binding. The first Apple archive/provider link passed; corrected
full IPA and real-phone traffic/leak/path-change acceptance are separate gates.
The deprecated borrowed-FD `StartIOS` remains denied (`PLATFORM_UNAVAILABLE` or
`INVALID_FD`). It is not the new packet-flow entry; no utun FD scan is used.

## C interface and ownership

All exports are additive to upstream API 3 and existing AWG/diagnostic exports.
The Apple build copies these Go sources beside the real upstream
`cgo_bridge/main.go`, then builds exactly one C archive per architecture. It does
not copy Mihomo's standalone `cmd/cbridge/main.go` or link a second Go runtime.
Generated declarations in `libXray.h` are authoritative; no generated header is
checked into the repository.

```c
char *NimboMihomoInvokeV1(char *requestJSON);
char *NimboMihomoCancelV1(char *requestJSON);
char *NimboMihomoStartIOSV1(char *requestJSON, int64_t borrowedFD); // denied legacy ABI
char *NimboMihomoStartIOSPacketFlowV1(char *requestJSON);
int NimboMihomoWriteIOSPacketV1(uint64_t generation, void *input, int length);
int NimboMihomoReadIOSPacketV1(uint64_t generation, void *output,
                             int capacity, int timeoutMs);
typedef int (*NimboMihomoSocketProtectorV1)(int64_t fd, void *context);
char *NimboMihomoSetSocketProtectorV1(NimboMihomoSocketProtectorV1 callback,
                                    void *context);
void NimboMihomoFreeV1(char *response);
```

Every returned non-null string is caller-owned. Free it **once**, with either
unchanged upstream `CGoFree` or its `NimboMihomoFreeV1` alias. Input strings remain
caller-owned and need only live for the call. Input scanning is bounded to 8 MiB;
null/oversized input produces a native failure envelope. Inspect responses can
contain the full original configuration and credentials: never log them.

`InvokeV1` returns the adapter's API 1 JSON envelope without translating source,
groups, generation or errors. It supports inspect/validate and real managed
runtime commands from [native API.md](../../tools/native/mihomo-core/API.md).
`CancelV1` accepts the same envelope **only** with `operation:"cancel"` and
`targetRequestId`; it cannot accidentally dispatch another runtime operation.
Cancellation bypasses the native operation lock and uses unique request IDs.
Managed desktop-proxy readiness is never represented as iOS TUN readiness.

Socket registration delegates to the real native protection hook and is allowed
only while stopped. The callback must synchronously protect/bind the borrowed
outgoing FD before returning exactly `1`; every other return value fails closed.
Never close that FD, reenter commands, throw across the C boundary or block
indefinitely. Retain the function/context until stop and successful unregister
(`callback = NULL`). The wrapper drains active callbacks and disables retained
old callback objects before a successful replacement/unregister returns. BUSY
leaves the original callback alive. Nil is supported only for native managed
desktop-proxy mode; it is not evidence of mobile egress protection coverage.

The host must stop/join its current engine before changing ownership. This
adapter does not add a shared lifecycle lock to the unchanged upstream Xray or
AWG ABI, and it does not claim multi-engine mobile TUN concurrency is safe.

## Root dependency graph and build

`go.mod`/`go.sum` now include `nimbo/mihomocore v0.0.0` and Mihomo v1.19.31,
commit `ab405bad5beeeac8b003bb01f60f134f6df54471`. Xray-core, AWG and canonical
gVisor remain at their previous pins. Minimal version selection increases
Brotli 1.0.6 → 1.1.1, compress 1.17.4 → 1.17.9, and x/exp from its 2024-05 pin
to `v0.0.0-20240904232852-e7e105dedf7e`; unused direct `kr/text` is removed.

Go ignores dependency-module `replace` directives, so the root graph contains:

```text
replace nimbo/awgcore => ../../tools/native/awg-core
replace nimbo/mihomocore => ../../tools/native/mihomo-core
replace google.golang.org/protobuf => ../../tools/native/mihomo-core/.build/protobuf
```

`scripts/ci/prepare-mihomo-merged.py` rewrites the latter two paths in a temporary
extracted LibXray tree. It verifies Mihomo/protobuf module sums, source ZIPs,
every module-cache source file and every staged protobuf file. The existing
documented fork patch changes only `go 1.20` to `go 1.22`. The effective protobuf
code is the pinned MetaCubeX fork (1.36.11-devel), **not** upstream v1.36.12,
despite that retained root requirement. There is no conflict-policy suppression,
module-cache mutation or extra dependency substitution.

The helper uses an already installed Go 1.27.1 and authenticated public source
modules. It does not fetch executable tools or update versions. Apple builds
check immutable `go.sum`, verify the external graph separately from local
replacements, execute merged Go and C ABI tests, then check every legacy/new
export in the generated archive/header. Source-verification metadata is retained
beside the generated framework. Existing license/patch notices remain under the
native source module; the generated framework alone is not a source distribution.

The Android owner can reuse this root lock with its own root-package adapter.
Apple `cgo_bridge` files are not the gomobile/root-package adapter. The pinned
upstream Android builder is `build/main.py android`; its gomobile version must
be explicitly pinned and its temporary graph edits reviewed by that owner.

## Verification and remaining gates

Windows Go 1.27.1 with CGO disabled exercised the **real SHA-verified extracted
LibXray source**, this merged graph and a recorded snapshot of the native adapter:

- Real API 3 invocation/config validation and Mihomo inspect/validate in one process.
- Byte-exact source, managed localhost start/snapshot/select/stop, cancellation,
  malformed/oversized input and explicit iOS-unavailable responses.
- Callback registration rejection/retention and drain-before-context-release policy.
- Existing merged AWG and native Mihomo test packages.

Offline source tests: `python scripts/ci/test-ios-mihomo-source.py`. Real native
C tests: `python scripts/ci/test-libxray-mihomo-cabi.py <merged-library>` alongside
the existing API3/AWG/diagnostic suite. The Apple build runs both automatically.
The new C suite verifies allocation/free aliases, bounded inputs, unavailable
iOS TUN, real lifecycle commands, and a denied outgoing socket callback against
a localhost HTTP fixture.

**C ABI execution, Apple C archive/Swift linking, XCFramework/IPA builds and
device traffic are not claimed on the Windows host.** CGO-disabled Go tests do
not compile C exports. Safe Darwin borrowed-FD duplication/ownership, AF framing,
native stack-ready acknowledgement, DNS/UDP protection and mobile lifecycle
remain separate acceptance gates. Source/lock hashes and exact commands are in
`.codex-tmp/mihomo-ios-gobridge/` at the repository root.

## Public packet-flow lifecycle

Production `MihomoPacketBridge.swift` starts a physical-interface path monitor
before NE route installation, retains the socket callback context, and connects
public readPackets/writePackets to the generation-owned native device. No second
Go archive, loopback SOCKS translation, Internet startup gate or FD discovery is
used. The adapter forces process classification off and explicitly rejects rules
that need unavailable app/UID ownership, custom host routes and classical remote
rule providers. Source DNS must be enabled; physical system-DNS discovery is not
implemented, so system placeholders requiring it fail rather than use public DNS.

Stop clears Swift generation before native cancellation and pump joining; late
callbacks cannot submit to the replacement session. Packet read timeout is at
most one second, output batching at most 32 packets, native queue 128 packets,
MTU 1500. Binary buffers are caller-owned; input is copied, output is filled
synchronously without malloc/base64/JSON per packet. See API.md for exact limits.
The app preserves the entire YAML, routes live group commands to the selected
provider, and persists choices only after native current-member readback.
