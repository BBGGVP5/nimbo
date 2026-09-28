#!/usr/bin/env python3
"""Stage memory-bounded gomobile build tools without changing shipped code."""

from __future__ import annotations

import argparse
import shutil
import subprocess
from pathlib import Path


TOOLS_NEEDLE = "\tvar g errgroup.Group\n\tfor i, filename := range filenames {"
TOOLS_REPLACEMENT = "\tvar g errgroup.Group\n\tg.SetLimit(4)\n\tfor i, filename := range filenames {"
MOBILE_NEEDLE = (
    "\tvar wg errgroup.Group\n"
    "\tfor _, t := range targets {\n"
    "\t\tt := t\n"
    "\t\twg.Go(func() error {\n"
    "\t\t\treturn buildAndroidSO(androidDir, t.arch)\n"
    "\t\t})\n"
    "\t}\n"
    "\tif err := wg.Wait(); err != nil {\n"
    "\t\treturn err\n"
    "\t}\n"
)
MOBILE_REPLACEMENT = (
    "\tfor _, t := range targets {\n"
    "\t\tif err := buildAndroidSO(androidDir, t.arch); err != nil {\n"
    "\t\t\treturn err\n"
    "\t\t}\n"
    "\t}\n"
)


def _copy_module(module_dir: Path, destination: Path) -> Path:
    if not (module_dir / "go.mod").is_file():
        raise RuntimeError(f"Go module metadata is missing: {module_dir}")
    if destination.exists():
        raise RuntimeError(f"staged module destination already exists: {destination}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    # Go's module cache marks source files read-only. copyfile preserves bytes
    # but leaves the staged source writable for the build-only patch below.
    shutil.copytree(module_dir, destination, copy_function=shutil.copyfile)
    return destination


def _replace_once(path: Path, old: str, new: str, label: str) -> None:
    content = path.read_text(encoding="utf-8")
    if (new and new in content) or old not in content:
        raise RuntimeError(f"{label} source layout changed; refusing an unverified patch: {path}")
    path.write_text(content.replace(old, new, 1), encoding="utf-8", newline="")


def stage_memory_bounded_modules(module_dirs: dict[str, Path], stage_dir: Path) -> dict[str, Path]:
    """Copy pinned x/tools and x/mobile sources and apply tool-only limits."""
    tools_dir = _copy_module(module_dirs["golang.org/x/tools"], stage_dir / "build-tools" / "x-tools")
    packages_go = tools_dir / "go" / "packages" / "packages.go"
    if not packages_go.is_file():
        raise RuntimeError(f"x/tools go/packages source is missing: {packages_go}")
    _replace_once(packages_go, TOOLS_NEEDLE, TOOLS_REPLACEMENT, "x/tools parser")

    mobile_dir = _copy_module(module_dirs["golang.org/x/mobile"], stage_dir / "build-tools" / "x-mobile")
    bind_android = mobile_dir / "cmd" / "gomobile" / "bind_androidapp.go"
    _replace_once(bind_android, MOBILE_NEEDLE, MOBILE_REPLACEMENT, "gomobile ABI builder")
    _replace_once(
        bind_android,
        '\t"golang.org/x/sync/errgroup"\n',
        "",
        "gomobile errgroup import",
    )
    return {"golang.org/x/tools": tools_dir, "golang.org/x/mobile": mobile_dir}


def _resolve_module(go: Path, source_dir: Path, module_path: str) -> Path:
    result = subprocess.run(
        [str(go), "list", "-m", "-f", "{{.Dir}}", module_path],
        cwd=source_dir,
        capture_output=True,
        text=True,
        check=True,
    )
    module_dir = Path(result.stdout.strip())
    if not module_dir.is_dir():
        raise RuntimeError(f"Go resolved a missing module directory for {module_path}: {module_dir}")
    return module_dir


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--go", required=True, type=Path)
    parser.add_argument("--source-dir", required=True, type=Path)
    parser.add_argument("--stage-dir", required=True, type=Path)
    args = parser.parse_args()

    modules = {
        module: _resolve_module(args.go, args.source_dir, module)
        for module in ("golang.org/x/tools", "golang.org/x/mobile")
    }
    staged = stage_memory_bounded_modules(modules, args.stage_dir)
    replacements = [
        f"-replace={module}={path}"
        for module, path in staged.items()
    ]
    subprocess.run(
        [str(args.go), "mod", "edit", *replacements],
        cwd=args.source_dir,
        check=True,
    )

    # gomobile is otherwise installed with `@version`, which deliberately
    # ignores the root module's local replacement directives. Install the
    # selected (tool-directive-pinned) module from this staged graph instead.
    build_py = args.source_dir / "build" / "app" / "build.py"
    _replace_once(
        build_py,
        'f"golang.org/x/mobile/cmd/gomobile@{version}",',
        '"golang.org/x/mobile/cmd/gomobile",',
        "gomobile installer",
    )
    print("Staged gomobile with parser limit 4 and sequential Android ABI builds.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
