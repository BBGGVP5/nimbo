# Nimbo Android AmneziaWG bridge

Core pinned to `github.com/amnezia-vpn/amneziawg-go/v3 v3.1.20260828`.
The Go and JNI bridge originate from amnezia-vpn/amneziawg-android commit
`5c16489e2cd9ed3a0a7a27c7445bba5238132f86`, `tunnel/tools/libwg-go` (Apache-2.0).
Copyright notices are retained in both sources. The core is MIT licensed.

Nimbo changes: JNI class name, mutex-protected handle registry, duplicated TUN fd
(the caller retains its ParcelFileDescriptor), failure-path device cleanup,
and removal of the filesystem UAPI listener. Only the in-process JNI API is exposed.

On Windows with Go and Android NDK 28.2:

```powershell
./build-android.ps1
```

The script verifies module checksums, builds both supported ABIs, checks all six
JNI exports and stages binaries under `artifacts/native`. Replacement is a separate
step after backup; an incomplete build never overwrites the application binaries.
Compilation/export checks do not replace a real-device tunnel test.
