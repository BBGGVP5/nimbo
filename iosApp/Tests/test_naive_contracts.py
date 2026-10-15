"""Source checks plus optional portable Swift execution (not device acceptance)."""
from pathlib import Path
import argparse, subprocess, tempfile, unittest
ROOT=Path(__file__).resolve().parents[2]
def read(path): return (ROOT/path).read_text(encoding='utf-8')
class NaiveContracts(unittest.TestCase):
 def test_real_client_and_single_runtime(self):
  body=read('tools/native/naive-core/client_cronet.go')
  self.assertIn('cronet.NewNaiveClient',body)
  self.assertNotIn('InsecureSkipVerify',body)
  self.assertIn('bootstrapResolver(cfg.Host)',body)
  self.assertIn('CGoFree(pointer)',read('iosApp/PacketTunnel/NaiveProxyBridge.swift'))
 def test_lifecycle_wires_cleanup(self):
  provider=read('iosApp/PacketTunnel/PacketTunnelProvider.swift')
  self.assertEqual(provider.count('awg.close()'),provider.count('naive.close()'))
  self.assertIn('naiveDNS: self.naive.isConfigured',provider)
  self.assertIn('self.naive.isRunning',provider)
  self.assertGreaterEqual(provider.count('naive.networkChanged()'),3)
 def test_admission_and_widget(self):
  policy=read('iosApp/Nimbo/NimboCoreSelection.swift')
  self.assertNotIn('naiveUnavailable',policy)
  self.assertIn('profile == .naive && selected == .xray',policy)
  self.assertEqual(policy,read('iosApp/PacketTunnel/NimboCoreSelection.swift'))
  self.assertIn('- path: Shared/NimboNaiveConfiguration.swift',read('iosApp/project.yml'))
 def test_build_links_real_apple_slices(self):
  body=read('scripts/ci/build-libxray-awg-apple.sh')
  for value in ['with_naive','iossimulator','libcronet.a','NimboNaiveStart','NaiveProxyBridge.swift']:
   self.assertIn(value,body)
  self.assertIn('naive-core',read('iosApp/GoBridge/go.mod'))
 def test_virtual_dns_cannot_escape_via_system_lan_resolver(self):
  network=read('iosApp/PacketTunnel/PacketTunnelNetwork.swift')
  self.assertIn('naiveDNS ? ["198.18.0.2"]',network)
  self.assertIn('naiveDNS: engine == .naive',read('iosApp/PacketTunnel/PacketTunnelProvider.swift'))
 def test_offline_ping_uses_independent_native_client(self):
  body=read('iosApp/GoBridge/nimbo_naive_diagnostic.go')
  self.assertIn('naivecore.Start',body)
  self.assertIn('context.AfterFunc(ctx, runtime.Close)',body)
  self.assertNotIn('nimboNaive.runtime',body)
  self.assertNotIn('NimboNaiveStop',body)
  self.assertIn('defer cleanup()',read('iosApp/GoBridge/nimbo_diagnostic.go'))
 def test_notices_are_real_xcode_resources_and_packaging_is_checked(self):
  project=read('iosApp/project.yml')
  self.assertIn('      - path: NativeNotices\n        type: folder\n        buildPhase: resources',project)
  build=read('scripts/ci/build-unsigned-ios.sh')
  self.assertIn('"${APP_PATH}/NativeNotices/${notice}"',build)
 def test_no_credentials_in_source_fixtures(self):
  tests=read('iosApp/Tests/NaiveConfigurationTests.swift')
  self.assertNotIn('sub.connectioncloud',tests)
if __name__=='__main__':
 args=argparse.ArgumentParser();args.add_argument('--swift',action='store_true');opts=args.parse_args()
 result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(NaiveContracts))
 if not result.wasSuccessful():raise SystemExit(1)
 if opts.swift:
  with tempfile.TemporaryDirectory(prefix='nimbo-naive-swift-') as d:
   exe=str(Path(d)/'tests')
   subprocess.run(['swiftc',str(ROOT/'iosApp/Shared/NimboNaiveConfiguration.swift'),str(ROOT/'iosApp/Tests/NaiveConfigurationTests.swift'),'-o',exe],check=True)
   subprocess.run([exe],check=True)
