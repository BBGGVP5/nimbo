import tempfile
import unittest
import json
import subprocess
from unittest.mock import patch
from pathlib import Path

from stage_gomobile_memory_limits import stage_memory_bounded_modules, _resolve_module


class GomobileMemoryLimitTests(unittest.TestCase):
    @patch("stage_gomobile_memory_limits.subprocess.run")
    def test_undownloaded_tool_cannot_resolve_to_working_directory(self, run):
        run.return_value = subprocess.CompletedProcess([], 0, '{"Path":"golang.org/x/tools"}', "")
        with self.assertRaisesRegex(RuntimeError, "did not resolve source"):
            _resolve_module(Path("go"), Path.cwd(), "golang.org/x/tools")

    @patch("stage_gomobile_memory_limits.subprocess.run")
    def test_download_uses_locked_graph_and_explicit_directory(self, run):
        with tempfile.TemporaryDirectory() as temp:
            run.return_value = subprocess.CompletedProcess([], 0, json.dumps({"Path":"golang.org/x/tools", "Dir":temp}), "")
            self.assertEqual(_resolve_module(Path("go"), Path.cwd(), "golang.org/x/tools"), Path(temp))
            self.assertEqual(run.call_args.args[0], ["go", "mod", "download", "-json", "golang.org/x/tools"])

    def test_stages_parser_limit_and_serial_abi_loop(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            tools = root / "tools"
            mobile = root / "mobile"
            (tools / "go" / "packages").mkdir(parents=True)
            (tools / "go.mod").write_text("module golang.org/x/tools\n")
            (tools / "go" / "packages" / "packages.go").write_text(
                "package packages\n" + "\tvar g errgroup.Group\n"
                "\tfor i, filename := range filenames {\n"
            )
            bind = mobile / "cmd" / "gomobile" / "bind_androidapp.go"
            bind.parent.mkdir(parents=True)
            (mobile / "go.mod").write_text("module golang.org/x/mobile\n")
            bind.write_text(
                'import (\n\t"golang.org/x/sync/errgroup"\n)\n'
                + "\tvar wg errgroup.Group\n"
                + "\tfor _, t := range targets {\n"
                + "\t\tt := t\n"
                + "\t\twg.Go(func() error {\n"
                + "\t\t\treturn buildAndroidSO(androidDir, t.arch)\n"
                + "\t\t})\n"
                + "\t}\n"
                + "\tif err := wg.Wait(); err != nil {\n"
                + "\t\treturn err\n\t}\n"
            )

            staged = stage_memory_bounded_modules(
                {"golang.org/x/tools": tools, "golang.org/x/mobile": mobile},
                root / "stage",
            )

            self.assertIn("g.SetLimit(4)", (staged["golang.org/x/tools"] / "go/packages/packages.go").read_text())
            staged_bind = (staged["golang.org/x/mobile"] / "cmd/gomobile/bind_androidapp.go").read_text()
            self.assertIn("for _, t := range targets", staged_bind)
            self.assertNotIn("wg.Go", staged_bind)
            self.assertNotIn("x/sync/errgroup", staged_bind)
            self.assertNotIn("g.SetLimit(4)", (tools / "go/packages/packages.go").read_text())

    def test_rejects_changed_upstream_layout(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            tools = root / "tools"
            mobile = root / "mobile"
            (tools / "go" / "packages").mkdir(parents=True)
            (tools / "go.mod").write_text("module golang.org/x/tools\n")
            (tools / "go" / "packages" / "packages.go").write_text("package packages\n")
            mobile.mkdir(parents=True)
            (mobile / "go.mod").write_text("module golang.org/x/mobile\n")
            (mobile / "cmd" / "gomobile").mkdir(parents=True)
            (mobile / "cmd" / "gomobile" / "bind_androidapp.go").write_text("package main\n")
            with self.assertRaisesRegex(RuntimeError, "layout changed"):
                stage_memory_bounded_modules(
                    {"golang.org/x/tools": tools, "golang.org/x/mobile": mobile},
                    root / "stage",
                )


if __name__ == "__main__":
    unittest.main()
