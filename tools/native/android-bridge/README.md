# Android single-runtime Mihomo bridge

These files are source overlays for the **root package of pinned LibXray
26.9.9**, not a second shared library. The staged gomobile build exposes the
existing LibXray API 3 plus Mihomo API 1 through one `libgojni.so` per ABI.

## Build and verify

From the repository root on Windows, with installed Go 1.27.1, Android SDK,
NDK 28.2.13676358 and Android Studio JBR:

```powershell
./scripts/ci/build-libxray-mihomo-android.ps1          # source verification + combined host tests
./scripts/ci/build-libxray-mihomo-android.ps1 -Build   # also stage four-ABI AAR
```

The script verifies the original LibXray source archive, uses the same merged
module lock as the Apple bridge, verifies all protobuf fork files and preserves
Xray/AWG pins. It invokes the pinned upstream builder with a fixed gomobile
version, not `latest`. Builds use `with_gvisor` for the native Android adapter.

Output lives in a fresh `artifacts/mihomo-android-native-*` directory. **The
script never replaces `app/libs/libxray.aar` and never installs an APK.** The
AAR verifier checks all four architectures, exactly one Go JNI library per ABI,
16 KiB load alignment, preservation of existing public Java APIs and the new
Mihomo exports. Host tests do not demonstrate Android TUN functionality.

## Bridge contract

- `NimboMihomoInvoke`: original versioned native JSON requests/responses, including
  original YAML, generation and explicit error codes. No lossy server conversion.
- `NimboMihomoCancel`: encodes the supplied request ID and requests cancellation
  without waiting for the lifecycle lock.
- `NimboMihomoSetSocketProtector`: retains the existing gomobile
  `DialerController`; the callback must protect the socket and must not reenter
  a lifecycle operation. Replace/unregister only after native stop completes.
- `NimboMihomoAndroidTunPlan`: compiled capabilities, not a connected state.
- `NimboMihomoStartAndroid`: trusted process-local request plus borrowed TUN FD.
  Native code validates and duplicates it; Android owns the original until stop
  returns. Neither remote JSON nor YAML can choose an FD.

## App integration and capability limits

Building this bridge alone is not proof of complete Android VPN support. The
application integration lives under `app/src/main` and `shared/src`; device
verification is separate from native compilation. The adapter's explicit capability restrictions
must never be bypassed by silently rewriting a subscription. See
`../mihomo-core/ANDROID-MILESTONE.md` for its actual admitted network model.

## Historical build verification — 2026-09-24

The frozen merged AAR built successfully for arm64-v8a, armeabi-v7a, x86 and
x86_64. The verifier confirmed existing public Java API preservation, all five
bridge exports, pinned Go/core metadata, one Go shared library per ABI, and
16-KiB ELF load-segment alignment. SHA-256:
`79861ccc33e78255e5242460740337020609d4fbbe9fd1813e751dbe78839030`.
Evidence: `artifacts/mihomo-android-native-20260924-064218-ef35750b/aar-verification.json`
and `aar-build-resumed.log`. Inputs matched the source freeze after compilation.
At that verification milestone the AAR remained staged: production
`app/libs/libxray.aar` was not replaced and no device runtime test or installation
was performed. This records that build only, not the current application status.
