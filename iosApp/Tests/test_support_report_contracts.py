"""Host-independent integration contracts; --swift runs real Foundation behavior tests."""
import argparse
import os
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8-sig")


class SupportReportContracts(unittest.TestCase):
    def test_preview_and_share_use_same_prepared_snapshot(self):
        view = read("iosApp/Nimbo/DiagnosticsView.swift")
        self.assertIn("Text(preparedReport.preview)", view)
        self.assertIn("ShareLink(item: preparedReport.url)", view)
        self.assertIn(".textSelection(.enabled)", view)
        self.assertIn(".frame(height: 280)", view)
        self.assertIn("if let preparedReport {", view)
        body = view.split("private func prepareExport()", 1)[1]
        self.assertLess(body.index("preparedReport = nil"), body.index("prepareSupportReport()"))
        self.assertIn("try Task.checkCancellation()", body)
        self.assertIn("preparationTask?.cancel()", view)
        self.assertIn("guard !Task.isCancelled", body)
        for forbidden in ["providerDiagnostics", "exportBundle", "Data(contentsOf:", "String(contentsOf:",
                          "JSONEncoder", "JSONDecoder", "localizedDescription", "NimboRedactor", ".record(",
                          "vpn.manager", "URLSession", "connect("]:
            self.assertNotIn(forbidden, view)

    def test_actor_owns_bounded_io_and_exact_file_bytes(self):
        source = read("iosApp/Shared/NimboDiagnostics.swift")
        self.assertIn("actor NimboDiagnostics", source)
        body = source.split("func prepareSupportReport()", 1)[1].split("private func append(", 1)[0]
        self.assertIn("NimboSupportReport.maximumInputBytes", body)
        self.assertIn("handle.read(upToCount: Int(count))", body)
        self.assertIn("remaining -= Int(count)", body)
        self.assertIn("lastIndex(of: 0x0A)", body)
        self.assertIn("omittedInputBytes: omittedBytes", body)
        self.assertIn("Data(rendered.preview.utf8).write(to: url", body)
        self.assertIn("NimboPreparedSupportReport(url: url, preview: rendered.preview)", body)
        self.assertIn("UUID().uuidString", body)
        for forbidden in ["Data(contentsOf:", "additionalSections", "providerDiagnostics", "URLSession", "MainActor", "record("]:
            self.assertNotIn(forbidden, body)
        self.assertNotIn("func exportBundle(", source)

    def test_closed_projection_and_complete_json_byte_limit(self):
        source = read("iosApp/Shared/NimboSupportReport.swift")
        self.assertEqual([line for line in source.splitlines() if line.startswith("import ")], ["import Foundation"])
        event = source.split("struct Event:", 1)[1].split("private struct InputEvent:", 1)[0]
        for forbidden in ["let message", "let metadata", "let process", "let code", "[String: Any]"]:
            self.assertNotIn(forbidden, event)
        self.assertIn("event.process == appProcess", source)
        self.assertIn("maximumEvents = 200", source)
        self.assertIn("maximumJSONBytes = 64 * 1_024", source)
        self.assertIn("decoder.decode(InputEvent.self", source)
        self.assertIn("line.count <= maximumLineBytes", source)
        self.assertIn("let bytes = try encoder.encode(document)", source)
        self.assertIn("if bytes.count <= maximumJSONBytes", source)
        self.assertIn("String(decoding: bytes, as: UTF8.self)", source)
        self.assertNotIn("NimboRedactor", source)
        self.assertIn('let scope = "app_events_only"', source)
        self.assertIn('let providerData = "not_collected"', source)

    def test_app_and_extension_include_foundation_helper(self):
        project = read("iosApp/project.yml")
        for target in ["  Nimbo:", "  NimboPacketTunnel:"]:
            body = project.split(target, 1)[1].split("    dependencies:", 1)[0]
            self.assertIn("- path: Shared", body)
        diagnostics = read("iosApp/Shared/NimboDiagnostics.swift")
        self.assertIn('forInfoDictionaryKey: "NimboDisplayVersion"', diagnostics)
        self.assertIn('forInfoDictionaryKey: "NimboBuildRevision"', diagnostics)
        self.assertIn('forInfoDictionaryKey: "CFBundleVersion"', diagnostics)


def swift_tests():
    compiler = shutil.which("swiftc")
    if compiler is None:
        raise SystemExit("swiftc unavailable: Foundation tests NOT executed; run --swift on a Swift host")
    with tempfile.TemporaryDirectory(prefix="nimbo-support-report-") as directory:
        exe = str(pathlib.Path(directory) / ("SupportReportTests.exe" if os.name == "nt" else "SupportReportTests"))
        subprocess.run([compiler, "-swift-version", "5", "-parse-as-library",
                        "iosApp/Shared/NimboSupportReport.swift", "iosApp/Tests/SupportReportTests.swift",
                        "-o", exe], cwd=ROOT, check=True)
        subprocess.run([exe], check=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--swift", action="store_true")
    args = parser.parse_args()
    result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(SupportReportContracts))
    if not result.wasSuccessful():
        raise SystemExit(1)
    if args.swift:
        swift_tests()
