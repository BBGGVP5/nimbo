# Apple TUN descriptor ownership

The pinned upstream iOS path wraps NetworkExtension's borrowed descriptor in
`os.NewFile`, but `Close` skips that file because NE owns the original. This
leaves parked Go readers alive and allows the file finalizer to close NE's fd.
The next session can compete with a retired reader. This is a concrete lifecycle
defect; it is not proof that it caused every reported freeze.

The Apple build stages a checksum-verified copy of Xray, duplicates the fd before
wrapping it and always closes its owned file. `ownsFd` remains false: Nimbo must
not install/remove macOS system routes in the iOS provider. No shared module
cache or other platform build is modified.

The Darwin socketpair regression repeats start/read/stop/GC twelve times and
checks old readers exit and the borrowed descriptor remains usable. Apple CI
runs it with the race detector. Physical NE reconnect remains a device test.
