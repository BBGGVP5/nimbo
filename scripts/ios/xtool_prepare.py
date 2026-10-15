#!/usr/bin/env python3
"""Prepare an isolated, device-arm64 SwiftPM workspace for xtool 1.22.2.

Requires Python 3.10+ and PyYAML (``python -m pip install PyYAML``). Example::

    python scripts/ios/xtool_prepare.py --repo-root /src/Nimbo \
        --dependencies-root /exports/nimbo-ios --output /work/nimbo-xtool

The dependencies directory must contain real, static NimboShared.xcframework
and LibXray.xcframework, PacketTunnelResources/{geoip,geosite}.dat, NativeNotices
(including every notice in iosApp/NativeNotices), and BrandingResources with
Assets.car, AppIcon*.png, and BrandingPartialInfo.plist from macOS actool.
Nothing is built, downloaded, signed, or patched. Output must not already exist
and must be outside both input trees; its parent directory must already exist.
All validation precedes output creation. An I/O failure leaves a partial output
for inspection; the generator never deletes or replaces a directory.

xtool.yml uses only the pinned upstream v1 schema. Sources remain byte-for-byte
copies, independently selected for each target from project.yml. No Bundle.module
rewrites or raw .xcassets build rules are emitted. Compiled branding, ordinary
resources, notices, and geo files use xtool's per-product root resources.

xtool-prepare.json records target membership, required binary-version checks,
and static-framework resource payloads that the parent packaging script must
copy before final signing. In particular, xtool 1.22.2's generated wrapper
executables share the package deployment version. Per-target compiler/linker
flags and plists encode the intended versions, but the final Mach-O versions
must be verified by the caller. This generator does not certify an installable
IPA or check whether exported branding matches the current source catalogs.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass, field
import hashlib
import json
from pathlib import Path, PurePosixPath
import plistlib
import re
import shutil
import struct
import sys

try:
    import yaml
except ImportError as exc:
    raise SystemExit("xtool_prepare requires PyYAML: python -m pip install PyYAML") from exc


XTOOL_VERSION = "1.22.2"
XTOOL_COMMIT = "65bf3cc5a1240fea53ac0748d29a01878222a6ce"
UPSTREAM = f"https://github.com/xtool-org/xtool/blob/{XTOOL_COMMIT}/"
TARGETS = ("Nimbo", "NimboPacketTunnel", "NimboLiveActivity", "NimboControlWidget")
BINARIES = ("NimboShared", "LibXray")
VARIABLE = re.compile(r"\$\(([^)]+)\)|\$\{([^}]+)\}")


class PreparationError(ValueError):
    """An input cannot be faithfully represented in the isolated workspace."""


class UniqueLoader(yaml.SafeLoader):
    pass


def _unique_mapping(loader, node):
    result = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node)
        if key in result:
            raise PreparationError(f"Duplicate YAML key: {key}")
        result[key] = loader.construct_object(value_node)
    return result


UniqueLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, _unique_mapping)


def relative_path(value: str) -> PurePosixPath:
    if not isinstance(value, str) or not value or "\\" in value:
        raise PreparationError(f"Expected a relative POSIX path: {value!r}")
    path = PurePosixPath(value)
    if path.is_absolute() or ".." in path.parts or ":" in value or path == PurePosixPath("."):
        raise PreparationError(f"Unsafe relative path: {value!r}")
    return path


def checked_path(root: Path, value: str, *, required: bool = True) -> Path:
    relative = relative_path(value)
    path = root.joinpath(*relative.parts)
    for candidate in (path, *path.parents):
        if candidate == root:
            break
        if candidate.is_symlink() or getattr(candidate, "is_junction", lambda: False)():
            raise PreparationError(f"Symlinks/junctions are not supported: {candidate}")
    if not path.resolve().is_relative_to(root.resolve()):
        raise PreparationError(f"Path escapes input directory: {path}")
    if required and not path.exists():
        raise PreparationError(f"Missing required input: {path}")
    return path


def files_in(path: Path) -> list[Path]:
    if path.is_symlink() or getattr(path, "is_junction", lambda: False)():
        raise PreparationError(f"Symlinks/junctions are not supported: {path}")
    if path.is_file():
        return [path]
    if not path.is_dir():
        raise PreparationError(f"Expected a file or directory: {path}")
    return [file for child in sorted(path.iterdir()) for file in files_in(child)]


def nonempty(path: Path) -> None:
    if not path.is_file() or path.stat().st_size == 0:
        raise PreparationError(f"Missing or empty required file: {path}")


def read_plist(path: Path) -> dict:
    try:
        result = plistlib.loads(path.read_bytes())
    except (OSError, ValueError, plistlib.InvalidFileException) as exc:
        raise PreparationError(f"Invalid plist {path}: {exc}") from exc
    if not isinstance(result, dict):
        raise PreparationError(f"Expected a plist dictionary: {path}")
    return result


def merge(base: dict, overlay: dict) -> dict:
    result = dict(base)
    for key, value in overlay.items():
        result[key] = merge(result[key], value) if isinstance(result.get(key), dict) and isinstance(value, dict) else value
    return result


def setting_text(value) -> str:
    if isinstance(value, bool):
        return "YES" if value else "NO"
    if not isinstance(value, (str, int, float)):
        raise PreparationError(f"Unsupported build setting value: {value!r}")
    return str(value)


def substitute(value, settings: dict, stack: tuple = ()):
    if isinstance(value, str):
        def replacement(match):
            key = match.group(1) or match.group(2)
            if key in stack:
                raise PreparationError(f"Cyclic build setting: {' -> '.join((*stack, key))}")
            if key not in settings:
                raise PreparationError(f"Unresolved build setting: {key}")
            return substitute(setting_text(settings[key]), settings, (*stack, key))
        return VARIABLE.sub(replacement, value)
    if isinstance(value, dict):
        return {substitute(k, settings, stack): substitute(v, settings, stack) for k, v in value.items()}
    if isinstance(value, list):
        return [substitute(v, settings, stack) for v in value]
    return value


def swift_string(value: str) -> str:
    escapes = {"\\": "\\\\", '"': '\\"', "\n": "\\n", "\r": "\\r", "\t": "\\t"}
    return '"' + "".join(escapes.get(c, f"\\u{{{ord(c):x}}}" if ord(c) < 32 or ord(c) == 127 else c) for c in value) + '"'


def swift_array(values: list[str]) -> str:
    return "[" + ", ".join(swift_string(value) for value in values) + "]"


def assert_static_archive(path: Path) -> None:
    """Accept ar archives and universal containers whose every slice is ar."""
    nonempty(path)
    with path.open("rb") as stream:
        magic = stream.read(8)
        if magic == b"!<arch>\n" and path.stat().st_size > 8:
            return
        formats = {b"\xca\xfe\xba\xbe": (">", False), b"\xbe\xba\xfe\xca": ("<", False),
                   b"\xca\xfe\xba\xbf": (">", True), b"\xbf\xba\xfe\xca": ("<", True)}
        if magic[:4] not in formats or len(magic) != 8:
            raise PreparationError(f"Expected a static archive (dynamic frameworks are unsupported): {path}")
        endian, wide = formats[magic[:4]]
        count = struct.unpack(endian + "I", magic[4:])[0]
        if not 1 <= count <= 64:
            raise PreparationError(f"Invalid universal static archive: {path}")
        fmt = endian + ("IIQQII" if wide else "IIIII")
        width = struct.calcsize(fmt)
        table = stream.read(count * width)
        if len(table) != count * width:
            raise PreparationError(f"Truncated universal static archive: {path}")
        for row in struct.iter_unpack(fmt, table):
            offset, size = row[2:4]
            if offset < 8 + count * width or size <= 8 or offset + size > path.stat().st_size:
                raise PreparationError(f"Invalid archive slice: {path}")
            stream.seek(offset)
            if stream.read(8) != b"!<arch>\n":
                raise PreparationError(f"Non-static universal archive slice: {path}")


@dataclass
class Workspace:
    copies: dict[str, Path] = field(default_factory=dict)
    generated: dict[str, bytes] = field(default_factory=dict)
    _destinations: dict[str, str] = field(default_factory=dict)

    def reserve(self, destination: str) -> None:
        relative_path(destination)
        folded = destination.casefold()
        if folded in self._destinations:
            raise PreparationError(f"Output path collision: {destination} / {self._destinations[folded]}")
        self._destinations[folded] = destination

    def copy(self, source: Path, destination: str) -> None:
        for file in files_in(source):
            target = destination if source.is_file() else f"{destination}/{file.relative_to(source).as_posix()}"
            if self.copies.get(target) == file:
                continue
            self.reserve(target)
            self.copies[target] = file

    def write(self, destination: str, contents: bytes) -> None:
        self.reserve(destination)
        self.generated[destination] = contents


def validate_binary(root: Path, name: str) -> list[dict]:
    framework = checked_path(root, f"{name}.xcframework")
    files_in(framework)  # Validate the complete copy, including unused slices.
    info = read_plist(checked_path(framework, "Info.plist"))
    libraries = info.get("AvailableLibraries")
    if not isinstance(libraries, list) or not libraries:
        raise PreparationError(f"No AvailableLibraries in {framework}/Info.plist")
    payloads = []
    device_found = False
    for library in libraries:
        identifier = library["LibraryIdentifier"]
        slice_root = checked_path(framework, identifier)
        library_path = checked_path(slice_root, library["LibraryPath"])
        executable = library_path
        if library_path.suffix == ".framework":
            binary_name = read_plist(checked_path(library_path, "Info.plist")).get("CFBundleExecutable", library_path.stem)
            executable = checked_path(library_path, binary_name)
        elif library_path.suffix != ".a":
            raise PreparationError(f"Unsupported static library path: {library_path}")
        assert_static_archive(executable)
        if library.get("SupportedPlatform") != "ios" or library.get("SupportedPlatformVariant"):
            continue
        if "arm64" not in library.get("SupportedArchitectures", []):
            continue
        device_found = True
        if library_path.is_dir():
            for child in sorted(library_path.iterdir()):
                if child.name in {executable.name, "Info.plist", "Headers", "PrivateHeaders", "Modules", "_CodeSignature"}:
                    continue
                children = sorted(child.iterdir()) if child.name == "Resources" and child.is_dir() else [child]
                for resource in children:
                    payloads.append({"binary_target": name,
                                     "source": "Dependencies/" + resource.relative_to(root).as_posix(),
                                     "destination": resource.name})
    if not device_found:
        raise PreparationError(f"Missing iOS device arm64 slice in {framework}")
    return payloads


def document(ios: Path, spec: dict, settings: dict) -> dict:
    path = checked_path(ios, spec["path"], required=False)
    if not path.exists() and not spec.get("properties"):
        raise PreparationError(f"Missing plist and generation properties: {path}")
    return substitute(merge(read_plist(path) if path.exists() else {}, spec.get("properties", {})), settings)


def selected_sources(ios: Path, specs: list) -> tuple[list[Path], list[Path], list[Path]]:
    swift, resources, catalogs = set(), set(), set()

    def visit(path: Path):
        if path.suffix == ".xcassets":
            files_in(path)
            catalogs.add(path)
        elif path.is_dir():
            for child in sorted(path.iterdir()):
                checked_path(ios, child.relative_to(ios).as_posix())
                visit(child)
        elif path.suffix == ".swift":
            nonempty(path)
            swift.add(path)
        elif path.suffix not in {".plist", ".entitlements"}:
            if path.suffix in {".m", ".mm", ".c", ".cc", ".cpp", ".h", ".metal"}:
                raise PreparationError(f"Unsupported source language: {path}")
            resources.add(path)

    for entry in specs:
        spec = {"path": entry} if isinstance(entry, str) else entry
        if set(spec) - {"path", "type", "buildPhase"}:
            raise PreparationError(f"Unsupported source selection options: {spec}")
        if spec.get("type") not in {None, "folder"} or spec.get("buildPhase") not in {None, "sources", "resources"}:
            raise PreparationError(f"Unsupported source selection: {spec}")
        if spec["path"] == "NativeNotices":
            if spec.get("type") != "folder" or spec.get("buildPhase") != "resources":
                raise PreparationError("NativeNotices must remain a folder resource")
            continue  # The complete exported notice payload is authoritative.
        path = checked_path(ios, spec["path"])
        if spec.get("buildPhase") == "resources" and path.suffix != ".xcassets":
            files_in(path)
            resources.add(path)
        else:
            visit(path)
    if not swift:
        raise PreparationError("Target source selection contains no Swift files")
    return sorted(swift), sorted(resources), sorted(catalogs)


def target_settings(project: dict, name: str, target: dict) -> tuple[dict, str]:
    settings = merge(project.get("settings", {}).get("base", {}), target.get("settings", {}).get("base", {}))
    minimum = str(target.get("deploymentTarget", project["options"]["deploymentTarget"])["iOS"])
    explicit = str(settings.get("IPHONEOS_DEPLOYMENT_TARGET", minimum))
    if explicit != minimum or not re.fullmatch(r"\d+\.\d+(?:\.\d+)?", minimum):
        raise PreparationError(f"Inconsistent/invalid iOS deployment target for {name}: {minimum} / {explicit}")
    if substitute(settings.get("PRODUCT_NAME", name), settings) != name:
        raise PreparationError(f"PRODUCT_NAME must match target name for xtool: {name}")
    settings.update(PRODUCT_NAME=name, PRODUCT_MODULE_NAME=name, EXECUTABLE_NAME=name,
                    IPHONEOS_DEPLOYMENT_TARGET=minimum, inherited="")
    bundle_id = substitute(settings["PRODUCT_BUNDLE_IDENTIFIER"], settings)
    if not re.fullmatch(r"[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+", bundle_id):
        raise PreparationError(f"Invalid bundle identifier for {name}: {bundle_id!r}")
    settings["PRODUCT_BUNDLE_IDENTIFIER"] = bundle_id
    return settings, minimum


def package_swift(targets: dict) -> str:
    lines = ["// swift-tools-version: 6.0", "// Generated by xtool_prepare.py; device arm64 only.",
             "import PackageDescription", "", "let package = Package(", '    name: "Nimbo",',
             f'    platforms: [.iOS({swift_string(targets["Nimbo"]["minimum_ios"])})],', "    products: ["]
    for name in TARGETS:
        lines.append(f"        .library(name: {swift_string(name)}, targets: [{swift_string(name)}]),")
    lines += ["    ],", "    targets: ["]
    for binary in BINARIES:
        lines.append(f'        .binaryTarget(name: "{binary}", path: "Dependencies/{binary}.xcframework"),')
    for name, target in targets.items():
        compile_flags = ["-target", target["compile_triple"], "-strict-concurrency=minimal"]
        link_flags = ["-target", target["compile_triple"], *target["other_ldflags"]]
        if name != "Nimbo":
            compile_flags.append("-application-extension")
            link_flags += ["-Xlinker", "-application_extension"]
        lines += ["        .target(", f"            name: {swift_string(name)},",
                  f'            dependencies: {swift_array(target["binary_targets"])},',
                  f'            path: "Sources/{name}",',
                  f'            sources: {swift_array(target["sources"])},',
                  '            swiftSettings: [', '                .swiftLanguageMode(.v5),',
                  f"                .unsafeFlags({swift_array(compile_flags)}),", "            ],",
                  "            linkerSettings: ["]
        for sdk in target["link_sdks"]:
            if sdk.endswith(".framework"):
                lines.append(f"                .linkedFramework({swift_string(sdk[:-10])}),")
            else:
                lines.append(f"                .linkedLibrary({swift_string(sdk[3:-4])}),")
        lines += [f"                .unsafeFlags({swift_array(link_flags)}),", "            ]", "        ),"]
    return "\n".join([*lines, "    ]", ")", ""])


def plan_workspace(repo: Path, dependencies: Path) -> Workspace:
    ios = checked_path(repo, "iosApp")
    project_file = checked_path(ios, "project.yml")
    project = yaml.load(project_file.read_text(encoding="utf-8"), Loader=UniqueLoader)
    if not isinstance(project, dict) or set(project.get("targets", {})) != set(TARGETS):
        raise PreparationError(f"Expected exactly four Nimbo targets: {', '.join(TARGETS)}")
    workspace = Workspace()
    binary_resources = []
    for name in BINARIES:
        binary_resources.extend(validate_binary(dependencies, name))
        workspace.copy(dependencies / f"{name}.xcframework", f"Dependencies/{name}.xcframework")
    notices = checked_path(dependencies, "NativeNotices")
    notice_files = files_in(notices)
    if not notices.is_dir() or not notice_files:
        raise PreparationError(f"Missing/empty NativeNotices directory: {notices}")
    for file in notice_files:
        nonempty(file)
    for required in files_in(checked_path(ios, "NativeNotices")):
        nonempty(checked_path(notices, required.relative_to(ios / "NativeNotices").as_posix()))
    workspace.copy(notices, "Resources/Nimbo/NativeNotices")
    geo_files = []
    for name in ("geoip.dat", "geosite.dat"):
        path = checked_path(dependencies, f"PacketTunnelResources/{name}")
        nonempty(path)
        destination = f"Resources/NimboPacketTunnel/{name}"
        workspace.copy(path, destination)
        geo_files.append(destination)
    branding = checked_path(dependencies, "BrandingResources")
    nonempty(checked_path(branding, "Assets.car"))
    partial = read_plist(checked_path(branding, "BrandingPartialInfo.plist"))
    protected = {"CFBundleExecutable", "CFBundleIdentifier", "CFBundleName", "CFBundlePackageType", "MinimumOSVersion"}
    if protected & partial.keys():
        raise PreparationError("BrandingPartialInfo.plist must not override bundle identity or minimum iOS")
    branding_files = files_in(branding)
    icons = [p for p in branding_files if p.name.startswith("AppIcon") and p.suffix == ".png"]
    if not icons or not partial.get("CFBundleIcons"):
        raise PreparationError("BrandingResources requires AppIcon*.png and CFBundleIcons in BrandingPartialInfo.plist")
    for file in branding_files:
        nonempty(file)
        if file.name != "BrandingPartialInfo.plist" and (file.parent != branding or file.suffix not in {".car", ".png"}):
            raise PreparationError(f"Unexpected compiled branding payload: {file}")
    branding_roots = []
    for file in branding_files:
        if file.name != "BrandingPartialInfo.plist":
            destination = f"Resources/Branding/{file.name}"
            workspace.copy(file, destination)
            branding_roots.append(destination)
    workspace.copy(branding / "BrandingPartialInfo.plist", "Support/BrandingPartialInfo.plist")

    targets = {}
    for name in TARGETS:
        target = project["targets"][name]
        expected_type = "application" if name == "Nimbo" else "app-extension"
        if target.get("type") != expected_type or target.get("platform") != "iOS":
            raise PreparationError(f"Unsupported target type/platform: {name}")
        settings, minimum = target_settings(project, name, target)
        swift, resources, catalogs = selected_sources(ios, target["sources"])
        sources = [file.relative_to(ios).as_posix() for file in swift]
        for file, relative in zip(swift, sources):
            workspace.copy(file, f"Sources/{name}/{relative}")
        roots = list(branding_roots)
        if name == "Nimbo":
            roots.append("Resources/Nimbo/NativeNotices")
        if name == "NimboPacketTunnel":
            roots.extend(geo_files)
        for resource in resources:
            destination = f"Resources/{name}/{resource.name}"
            workspace.copy(resource, destination)
            roots.append(destination)
        basenames = [Path(path).name.casefold() for path in roots]
        if len(basenames) != len(set(basenames)):
            raise PreparationError(f"Colliding root resource names for {name}")
        info = document(ios, target["info"], settings)
        if name == "Nimbo":
            info = merge(info, substitute(partial, settings))
        info.update(CFBundleExecutable=name, CFBundleName=name,
                    CFBundleIdentifier=settings["PRODUCT_BUNDLE_IDENTIFIER"],
                    CFBundlePackageType="APPL" if name == "Nimbo" else "XPC!", MinimumOSVersion=minimum)
        info_path = f"Support/{name}-Info.plist"
        workspace.write(info_path, plistlib.dumps(info, sort_keys=True))
        entitlements_path = None
        if "entitlements" in target:
            entitlements_path = f"Support/{name}.entitlements"
            workspace.write(entitlements_path, plistlib.dumps(document(ios, target["entitlements"], settings), sort_keys=True))
        binaries, sdks, embedded = [], [], []
        for dependency in target.get("dependencies", []):
            if "framework" in dependency:
                binary = Path(dependency["framework"]).stem
                if binary not in BINARIES or dependency.get("embed") is not False:
                    raise PreparationError(f"Unsupported binary dependency: {dependency}")
                binaries.append(binary)
            elif "sdk" in dependency:
                sdk = dependency["sdk"]
                if not re.fullmatch(r"[A-Za-z0-9_+]+\.framework|lib[A-Za-z0-9_+]+\.tbd", sdk):
                    raise PreparationError(f"Unsupported SDK dependency: {sdk}")
                sdks.append(sdk)
            elif "target" in dependency and name == "Nimbo" and dependency.get("embed") is True:
                embedded.append(dependency["target"])
            else:
                raise PreparationError(f"Unsupported target dependency: {dependency}")
        if name == "Nimbo" and set(embedded) != set(TARGETS[1:]):
            raise PreparationError("Nimbo must embed all three extensions")
        ldflags = substitute(settings.get("OTHER_LDFLAGS", ""), settings).split()
        if set(ldflags) - {"-ObjC"}:
            raise PreparationError(f"Unsupported OTHER_LDFLAGS for {name}: {ldflags}")
        if str(settings.get("SWIFT_VERSION", "5.0")) not in {"5", "5.0"}:
            raise PreparationError(f"Unsupported Swift language version for {name}")
        targets[name] = dict(product=name, type=expected_type, bundle_id=settings["PRODUCT_BUNDLE_IDENTIFIER"],
                             minimum_ios=minimum, compile_triple=f"arm64-apple-ios{minimum}", sources=sources,
                             source_asset_catalogs=[p.relative_to(ios).as_posix() for p in catalogs],
                             binary_targets=binaries, link_sdks=sdks, other_ldflags=ldflags,
                             root_resources=roots, info_path=info_path, entitlements_path=entitlements_path)
    config = {"version": 1, "bundleID": targets["Nimbo"]["bundle_id"], "product": "Nimbo",
              "infoPath": targets["Nimbo"]["info_path"], "resources": targets["Nimbo"]["root_resources"],
              "entitlementsPath": targets["Nimbo"]["entitlements_path"], "extensions": []}
    for name in TARGETS[1:]:
        target = targets[name]
        extension = dict(product=name, bundleID=target["bundle_id"], infoPath=target["info_path"], resources=target["root_resources"])
        if target["entitlements_path"]:
            extension["entitlementsPath"] = target["entitlements_path"]
        config["extensions"].append(extension)
    workspace.write("Package.swift", package_swift(targets).encode("utf-8"))
    workspace.write("xtool.yml", yaml.safe_dump(config, allow_unicode=True, sort_keys=False).encode("utf-8"))
    manifest = {"format_version": 1, "xtool_version": XTOOL_VERSION, "xtool_commit": XTOOL_COMMIT,
                "upstream": [UPSTREAM + path for path in ("Sources/PackLib/PackSchema.swift", "Sources/PackLib/Planner.swift",
                                                         "Sources/PackLib/Packer.swift", "Examples/DemoExtensions/xtool.yml")],
                "project_sha256": hashlib.sha256(project_file.read_bytes()).hexdigest(), "targets": targets,
                "binary_resource_payloads": binary_resources,
                "required_postpack_actions": [
                    "Copy each binary_resource_payload to the root of every product that depends on its binary_target; fail on collisions.",
                    "Verify every final Mach-O minimum iOS version against targets[*].minimum_ios before signing/IPA packaging.",
                    "Verify Assets.car, icon plist entries, fonts, notices and tunnel geo files at product roots before final signing."],
                "limitations": [
                    "Device arm64 only; per-target -target flags are not simulator-portable.",
                    "xtool 1.22.2 creates all wrapper executables at the package's app minimum; per-target compile/link flags require final Mach-O verification.",
                    "Static framework resource payloads are not automatically promoted by xtool; the parent packaging script must copy them.",
                    "Exported actool output is required; its freshness against source .xcassets is not verified here."]}
    workspace.write("xtool-prepare.json", (json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8"))
    return workspace


def prepare(repo_root: Path, dependencies_root: Path, output: Path) -> Path:
    repo, dependencies = Path(repo_root).resolve(), Path(dependencies_root).resolve()
    raw_output = Path(output).absolute()
    if raw_output.exists() or raw_output.is_symlink():
        raise PreparationError(f"Output already exists; refusing to overwrite: {raw_output}")
    destination = raw_output.resolve()
    for source in (repo, dependencies):
        if destination.is_relative_to(source) or source.is_relative_to(destination):
            raise PreparationError(f"Output must be disjoint from input trees: {destination} / {source}")
    if not destination.parent.is_dir():
        raise PreparationError(f"Output parent must already exist: {destination.parent}")
    try:
        workspace = plan_workspace(repo, dependencies)
    except (KeyError, TypeError, yaml.YAMLError) as exc:
        raise PreparationError(f"Malformed or unsupported project/dependency metadata: {exc}") from exc
    destination.mkdir()  # Exclusive ownership: no exist_ok, cleanup, or replacement.
    for relative, source in sorted(workspace.copies.items()):
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
    for relative, contents in sorted(workspace.generated.items()):
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open("xb") as stream:
            stream.write(contents)
    return destination


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--repo-root", required=True, type=Path)
    parser.add_argument("--dependencies-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    arguments = parser.parse_args(argv)
    try:
        output = prepare(arguments.repo_root, arguments.dependencies_root, arguments.output)
    except (PreparationError, OSError) as exc:
        print(f"xtool_prepare: {exc}", file=sys.stderr)
        return 1
    print(f"Prepared {output}; packaging requirements: {output / 'xtool-prepare.json'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
