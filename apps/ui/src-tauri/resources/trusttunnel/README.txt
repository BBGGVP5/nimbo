TrustTunnel bundled client
==========================

Upstream: https://github.com/TrustTunnel/TrustTunnelClient
Version: v1.1.11
License: Apache License 2.0 (see LICENSE)

Official release archive SHA-256 (as published by GitHub for the release):
- trusttunnel_client-v1.1.11-windows-x86_64.zip
  c6db992c1164abef1a19a8289173be23d87c6f8c039360dbe9b7fdef89a9e526
- trusttunnel_client-v1.1.11-windows-aarch64.zip
  8c6b3ad5d90db795e4f00f9545076c06c2bbd8d673c9888b8169bf460d658a85
- trusttunnel_client-v1.1.11-windows-i686.zip
  f464c2fdd6ac5cc79865ba7502152c456ff366e8f533e28d23c6241b3f57945f
- trusttunnel_client-v1.1.11-linux-x86_64.tar.gz
  00e0546a6b35840dbb92b741dff3da6732a74bf2d6c581e044bb4567c0d96825
- trusttunnel_client-v1.1.11-linux-aarch64.tar.gz
  880e9c8afd5cce7ff38c53978f0ea530ff95e679bcfef69c5127b26b3692d110

SHA-256 of the extracted bundled executables (pinned in
src/trusttunnel_runtime.rs and apps/installer):
- windows-x64/trusttunnel_client.exe
  2572f6397ec4a39e018858da593ab278facf9b2e86c7e276753f4845ed78968d
- windows-arm64/trusttunnel_client.exe
  39b8a66af90f8670c912377a46191d0026e7e140569e9075c2c04cee6907bf7a
- windows-x86/trusttunnel_client.exe
  989713b8038e5ba08f312e5e77d788bbb2a602e84be7ef256751b7b04689d744
- linux-x64/trusttunnel_client
  e01411457f65c498063211fa670d55ea710d3a2d35534acd73438bd514df454a
- linux-arm64/trusttunnel_client
  8d1fddc866809d1c76f983578ae5cc68a62c7c63237520ef7cbf9294d2768b08

Provenance checks done when these files were added (2026-10-18):
- Windows executables carry a valid Authenticode signature of
  "Adguard Software Limited" (DigiCert Trusted G4 Code Signing CA1).
- Linux executables verify against their .sig files with AdGuard's release key
  28645AC9776EC4C00BCE2AFC0FE641E7235E2EC6 (see upstream VERIFY_RELEASES.md).

Nimbo runs this client only for TrustTunnel (tt://) servers, with a single
authenticated SOCKS5 listener on 127.0.0.1 and no TUN listener, kill switch,
route or system DNS changes; Xray keeps the tunnel and routing. wintun.dll is
therefore not needed. The credential-bearing TOML is deleted as soon as the
client reports VPN_SS_CONNECTED (or fails).
