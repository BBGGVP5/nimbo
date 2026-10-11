"""Lifecycle wiring checks; real wire exchanges are covered by Kotlin tests."""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
VPN = ROOT / "app/src/main/java/com/danila/nimbo/vpn"


class AndroidBootstrapDnsContracts(unittest.TestCase):
    def test_install_precedes_tun_and_run(self):
        source = (VPN / "XrayManager.kt").read_text(encoding="utf-8")
        connect = source.split("suspend fun connect(", 1)[1].split("fun disconnect()", 1)[0]
        self.assertLess(connect.index("LibXray.setDNS("), connect.index("establishTun(vpnService"))
        self.assertLess(connect.index("establishTun(vpnService"), connect.index("LibXray.invoke(XrayCoreProtocol.runXray"))
        self.assertIn("requireNotNull(underlyingNetwork)", connect)
        self.assertIn("vpnService.protect(fd.toInt())", connect)

    def test_restore_follows_core_stop_and_all_failures_disconnect(self):
        source = (VPN / "XrayManager.kt").read_text(encoding="utf-8")
        stop = source.split("fun disconnect()", 1)[1].split("fun recordConnectionFailure", 1)[0]
        self.assertLess(stop.index("bootstrapDns?.stopResolving()"), stop.index("stopXray()"))
        self.assertLess(stop.index("stopXray()"), stop.index("LibXray.resetDNS()"))
        self.assertLess(stop.index("LibXray.resetDNS()"), stop.index("dns.close()"))
        self.assertIn("bootstrapDns = null", stop)
        connect = source.split("suspend fun connect(", 1)[1].split("fun disconnect()", 1)[0]
        self.assertIn("catch (e: CancellationException) {\n            disconnect()", connect)
        self.assertIn("connectionError = e.message ?: e.toString()", connect)

    def test_platform_dns_is_explicitly_network_scoped_without_public_fallback(self):
        source = (VPN / "AndroidBootstrapDns.kt").read_text(encoding="utf-8")
        self.assertIn("NET_CAPABILITY_NOT_VPN", source)
        self.assertIn("resolver.rawQuery(\n                network, query", source)
        for forbidden in ("1.1.1.1", "8.8.8.8", "Dns.SYSTEM", "null, query", "JSONObject", "bindProcessToNetwork"):
            self.assertNotIn(forbidden, source)
        self.assertIn("HandlerThread", source)  # API 37 resolver is never owned by the UI looper.

    def test_queries_are_bounded_and_cancelled_on_close(self):
        source = (VPN / "AndroidBootstrapDns.kt").read_text(encoding="utf-8")
        self.assertIn("result.get(5, TimeUnit.SECONDS)", source)
        self.assertIn("pending.remove(cancellation)", source)
        self.assertIn("cancellation.cancel()", source)
        self.assertIn("resolverThread?.quitSafely()", source)
        server = (VPN / "BootstrapDnsServer.kt").read_text(encoding="utf-8")
        self.assertIn("ArrayBlockingQueue(16)", server)
        self.assertIn('InetAddress.getByName("127.0.0.1")', server)
        self.assertIn("workers.shutdownNow()", server)


if __name__ == "__main__":
    unittest.main()
