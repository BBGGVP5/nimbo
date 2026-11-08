NaiveProxy bundled runtime
==========================

Upstream: https://github.com/klzgrad/naiveproxy
Version: v154.0.8037.49-5
License: BSD 3-Clause (see LICENSE)

Official release archive SHA-256:
- windows-x64/naive.exe
  36d14c117ed052c8d04d10a30db083423ad4e423c012c2ef2ca7286ec06afbbc
- linux-x64/naive
  80214dfea1767f6ecf63a3a79dc9f48b203460f51abc609357fff48dd5844c7d

SHA-256 of the extracted bundled executables:
- windows-x64/naive.exe
  ecf6f56a834a3e945e703ddb766b32c8c474e9767d472bb0b45177c33300aa79
- linux-x64/naive
  f86c52be454eebc9134aad476f3fe91fcf90df7200c3df262a4d0a3d2de85464

Nimbo runs this executable as a local SOCKS sidecar only for NaiveProxy
profiles. The credential-bearing runtime configuration is removed after the
local port is ready.
