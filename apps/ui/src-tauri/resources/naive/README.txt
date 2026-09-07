NaiveProxy bundled runtime
==========================

Upstream: https://github.com/klzgrad/naiveproxy
Version: v150.0.7871.63-1
License: BSD 3-Clause (see LICENSE)

Official release archive SHA-256:
- windows-x64/naive.exe
  d09e35f9fde6206a775a1b930d7d8252053bee1408ee1c910b5681346c68d1a1
- linux-x64/naive
  0c4f506ce66a7881892fd6932b542c53fc06ac2351987756096c61e753c687bf

SHA-256 of the extracted bundled executables:
- windows-x64/naive.exe
  94f99801c665d29fc071624663c6f7bfa59e8d5efaa84cd08ef5ebb18b46cb62
- linux-x64/naive
  baea1e9b9f8dd879a6374110bd7bdca80c2ecbdca8debc4f84f784a8739eaea7

Nimbo runs this executable as a local SOCKS sidecar only for NaiveProxy
profiles. The credential-bearing runtime configuration is removed after the
local port is ready.
