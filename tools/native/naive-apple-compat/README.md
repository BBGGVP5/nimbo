# Pinned iOS Cronet feature-init compatibility

This object is only used with the three SHA-256-verified iOS archives from cronet-go `0d28acc44093df24b2526dea3d6ffefd6b0a54f0`. Never add it globally to Go, Android or macOS. A dependency upgrade deliberately fails the guard until reviewed.

## Source evidence

All source references below are the Naive submodule `72a06c9fca0e2d228588c7f3074bf7efff3ff686`:

- [base/BUILD.gn](https://github.com/SagerNet/naiveproxy/blob/72a06c9fca0e2d228588c7f3074bf7efff3ff686/src/base/BUILD.gn): for iOS, `is_cronet_build` selects `message_pump_io_ios.cc`, excluding `message_pump_kqueue.cc`.
- [base/features.cc](https://github.com/SagerNet/naiveproxy/blob/72a06c9fca0e2d228588c7f3074bf7efff3ff686/src/base/features.cc): its Apple `InitializeFeatures` call is guarded only by `!IS_IOS || !USE_BLINK`. That does not exclude this non-Blink Cronet build.
- [message_pump_kqueue.cc](https://github.com/SagerNet/naiveproxy/blob/72a06c9fca0e2d228588c7f3074bf7efff3ff686/src/base/message_loop/message_pump_kqueue.cc): `InitializeFeatures()` only stores the `kTimerSlackMac` feature into `g_timer_slack`, a translation-unit-private atomic used exclusively by the absent kqueue pump.

All three shipped archives contain exactly one undefined kqueue symbol (this initializer), no kqueue implementation or other references, and the real `MessagePumpIOSForIO` constructor/implementation. `check_archive.py` verifies the exact archives and this symbol invariant before every build.

The compatibility initializer consequently has no state to initialize. It is equivalent to excluding this unused call in features.cc for Cronet iOS; it does not replace a functioning event pump or bypass network/TLS checks. All other Chromium feature initialization is unchanged. The object uses an explicit linker symbol rather than redeclaring Chromium's C++ class.

## Verification and removal

Run `python3 tools/native/naive-apple-compat/test_check_archive.py`. Each device/simulator slice is then compiled with its matching Xcode target and linked against the production Swift bridge. The macOS native transport and C-ABI tests do not use this object. Real iPhone network/memory acceptance remains required.

Prefer an upstream source fix aligning features.cc with BUILD.gn; remove this directory and merge step once verified fixed archives are available. Do not widen the hash allowlist without reviewing the corresponding source.
