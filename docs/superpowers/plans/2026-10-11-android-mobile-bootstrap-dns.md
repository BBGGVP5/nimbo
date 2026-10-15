# Android mobile Xray bootstrap DNS implementation plan

> **Withdrawn from runtime on 2026-10-11:** the user reported worse connectivity after the signed APK replacement. The adapter had only JVM/compile verification, not carrier/device verification. Restore the pre-change Xray wiring; do not treat the earlier passing tests as evidence of mobile-network compatibility. Original implementation and tests remain recoverable in commit `d155a0b`.

> **For agentic workers:** Execute this bounded fix task-by-task in the current workspace. Preserve existing platform work and release artifacts.

**Goal:** Resolve Xray connection endpoints through the selected physical Android network without forcing public DNS, looping through the new VPN, or replacing subscription DNS.

**Architecture:** Use the existing LibXray `setDNS` hook with an ephemeral loopback UDP/TCP adapter. Forward raw queries to Android `DnsResolver.rawQuery(network, ...)`, retaining platform DNS/private-DNS behavior and network-scoped caches. Stop Xray before restoring its Go resolver and closing the adapter. No native library rebuild or subscription mutation is required.

**Tech Stack:** Kotlin/JVM sockets and bounded executors, Android API 29+ DnsResolver, existing libXray 26.9.30 AAR, JUnit.

## Evidence

- `XrayManager.connect` registers outbound protection but never configures Go DNS. The pinned wrapper documents Android loopback-resolution failures and provides `setDNS`/`resetDNS`; both exports were verified in the actual production AAR with javap.
- `NimboDns.privacyAware` serves application HTTP, not the native Xray resolver.
- Existing callbacks rebuild the core on physical-network handoff. An explicit Network in each platform DNS request also tracks resolver updates on the same network.

## Task 1 — Regression tests for the local adapter

**Create:** `app/src/test/java/com/danila/nimbo/vpn/BootstrapDnsServerTest.kt`.

- [x] Add real localhost UDP and TCP tests with a fake raw resolver. Assert IDs/questions are preserved, two successive sessions do not share answers, TCP framing works, failed lookups return SERVFAIL, malformed requests never reach the resolver, and close cancels in-flight work and releases listeners.
- [x] Verify missing-adapter compilation fails. Initial Gradle runs hit an incremental-cache error; the expected missing-class failure was then reproduced with the cached Kotlin compiler in an isolated JVM compilation (`wire-red.log`). Gradle passed after disabling incremental compilation; no repository build configuration was changed.

## Task 2 — Implement network-scoped DNS bootstrap and lifecycle

**Create:** `app/src/main/java/com/danila/nimbo/vpn/BootstrapDnsServer.kt`, `AndroidBootstrapDns.kt`.
**Modify:** `app/src/main/java/com/danila/nimbo/vpn/XrayManager.kt`.

- [x] Implement a loopback-only ephemeral adapter with UDP/TCP listeners, bounded worker/queue limits and timeouts. Never log query names. Closing releases sockets, workers and the Android exchange.
- [x] Implement cancellable raw Android DNS requests against a non-null physical Network. Never use a default/VPN Network or hardcoded public upstream; errors are explicit, no silent unprotected fallback.
- [x] After core loading and before establishing TUN, start the adapter and call `LibXray.setDNS` using a VPN-protected loopback dialer. Keep the existing physical-network protection/binding for actual remote sockets.
- [x] Cancel platform lookups before native stop, keeping the loopback listener alive to answer SERVFAIL while the core drains requests. After stop, call `LibXray.resetDNS` and close the adapter. Do not rewrite root DNS, outbound/SNI, routing rules, subscription data or application HTTP DNS.

## Task 3 — Verify and synchronize

- [x] Run adapter regression tests and all Android JVM tests, compiling the real Android app/native bindings.
- [x] Add and compile Android instrumentation coverage for the actual DnsResolver adapter. A read-only isolated emulator was attempted; no usable API 37 image was installed and the installed API 33 guest stayed offline. Stop only the task-owned emulator processes. Do not touch the connected personal phone or its VPN.
- [x] Check diff/whitespace and lifecycle ordering; mirror only owned files after checking matching baselines, commit the verified patch in the existing Git checkout.
- [x] Report that carrier VLESS end-to-end remains a physical-device test; do not claim mobile connectivity proven by localhost/unit tests or upload an unbuilt APK.

## Verification outcome (2026-10-11)

- `./gradlew.bat --no-daemon '-Pkotlin.incremental=false' :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` — successful. **648 tests, 122 suites, zero failures/errors/skips**, including 10 real loopback DNS regression tests.
- `python scripts/ci/test-android-bootstrap-dns.py -v` — **4 passing lifecycle/source contracts** (not a substitute for device networking).
- Debug ARM64, ARMv7, universal and instrumentation APKs packaged successfully. The production AAR was not rebuilt or replaced; its actual `setDNS` and `resetDNS` Java exports were verified before implementing the hook.
- Platform instrumentation is compiled but **not executed**. API 37 constructor is typechecked and guarded; actual carrier/private-DNS/TUN acceptance requires a phone test of the new Android build.
- Provider/root DNS, app HTTP DNS, selected server, SNI, routing and existing default tunnel DNS policy are unchanged. This fix is scoped to native system/bootstrap resolution, not an indiscriminate DNS rewrite.
- No user VPN settings/subscriptions were changed and no APK was uploaded to Beta 1. Android release signing/rebuild remains the user's existing workflow.
- Logs live in `.codex-tmp/android-mobile-dns-20261011/`; final source hashes and APK hashes are recorded in `verification.json` there.

### Manual acceptance on mobile data

1. Rebuild/install the release APK, leave the subscription and DNS preferences unchanged.
2. On mobile data, connect a VLESS/Reality or VLESS/XHTTP node whose endpoint is a domain, check actual browsing, then disconnect/reconnect several times.
3. Repeat after Wi-Fi → mobile handoff and with Android Private DNS enabled. Check that disconnect is not delayed by an outstanding DNS query.
4. Repeat with an IP-literal endpoint: lack of a hostname means bootstrap DNS alone cannot explain its connection failure. Collect a sanitized diagnostic error if that case still fails.

Platform API reference: [DnsResolver.rawQuery](https://developer.android.com/reference/android/net/DnsResolver#rawQuery(android.net.Network,%20byte[],%20int,%20java.util.concurrent.Executor,%20android.os.CancellationSignal,%20android.net.DnsResolver.Callback)) specifies an explicit Network; null would select the default network, which this adapter never uses.
