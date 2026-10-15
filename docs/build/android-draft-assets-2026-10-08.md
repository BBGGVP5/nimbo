# Android APK for Beta 1 draft — 2026-10-08

The user's existing release APKs from `app/release` were inspected and staged unchanged. Android was not rebuilt, re-signed, or installed during this task.

| APK | Size (bytes) | SHA-256 |
|---|---:|---|
| `Nimbo_v1.3.0-beta.1_arm64_v8a.apk` | 46945987 | `8cc807ab5e83542ef9dc1647862faea8783eba9617dca605bfe8ee01b76af86e` |
| `Nimbo_v1.3.0-beta.1_armeabi_v7a.apk` | 47276309 | `af6acf239391b1cc244bde7ed115579db87e3c40fcecb3aa7797dea8b3075f6c` |
| `Nimbo_v1.3.0-beta.1_universal.apk` | 135352020 | `f253e477a69c6cffa87a0562eba3265633e7f0fb43b5f78dd92b78824f8fa0a0` |

## Verification

- Android SDK 37 `aapt2 dump badging`: package `com.danila.nimbo`, versionName `1.3.0-beta.1`, versionCode `18`, minSdk `29`, targetSdk `37`, no `application-debuggable` flag.
- Split ABI sets are respectively `arm64-v8a` and `armeabi-v7a`; universal includes `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`.
- `apksigner verify --verbose --print-certs` succeeded on all three APKs. APK Signature Scheme v2 verifies; each has one common non-debug signer. Certificate SHA-256: `e99f7c5ad9b25798fec22670b8108f2692771d4ca347de40881feb3487cdc06d`.
- ZIP integrity passed. AndroidManifest.xml, classes.dex, resources.arsc and `libgojni.so` for every advertised ABI are present.
- Source APK hashes were checked before and after copying into ignored staging; standard `.apk.sha256` sidecars were generated. User originals remain untouched.

## Delivery boundaries

The existing `v1.3.0-beta.1` draft received three APKs and three checksum files without overwriting prior assets. GitHub verified all six new digests and retained the previous 22 assets unchanged. Desktop/iOS compiled target stays `a1a16fa80fe4390274927cb80b6b7e4db8ffc12f`. The user subsequently authorized publication on 9 October 2026 (Europe/Samara). The release is now published as a prerelease with 28 assets; no PR merge or binary replacement was performed.

These are artifact/signature checks, not an independently reproduced Android build or device/runtime acceptance. Exact APK source revision and compatibility with the signing certificate of earlier published APKs were not independently attested. No private keystore or signing password was read or published.
