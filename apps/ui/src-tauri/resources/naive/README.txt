NaiveProxy bundled runtime
==========================

Upstream: https://github.com/klzgrad/naiveproxy
Version: v154.0.8037.49-4
License: BSD 3-Clause (see LICENSE)

Official release archive SHA-256:
- windows-x64/naive.exe
  120b99474848d2f737515c1043249b0998e235fda750f61893d774b317bf1870
- linux-x64/naive
  9d765620b90f7c60eb40c7c68b2f82537757cc52a8693dee7a00f8ba8b13dfd0

SHA-256 of the extracted bundled executables:
- windows-x64/naive.exe
  71b1bc593a1470f3fbaf216469edde58b06a97449558aa3bc70cb265591635b7
- linux-x64/naive
  9fba072aceb445d0401bb26273584726c479f5c4bc7f911aebf293c90136b044

Nimbo runs this executable as a local SOCKS sidecar only for NaiveProxy
profiles. The credential-bearing runtime configuration is removed after the
local port is ready.
