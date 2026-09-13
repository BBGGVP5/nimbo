"""Run native contract tests inside real macOS app/extension test bundles.

No production guard bypass: an embedded Python host makes the process's actual
main executable an .app or .appex member. Linux uses the ordinary Python runner.
"""
import json
import os
from pathlib import Path
import plistlib
import shutil
import subprocess
import sys
import sysconfig
import tempfile


def python_library():
    candidates = [Path(sys.base_prefix) / "Python"]
    directory = sysconfig.get_config_var("LIBDIR")
    for name in (sysconfig.get_config_var("LDLIBRARY"), sysconfig.get_config_var("INSTSONAME")):
        if directory and name:
            candidates.append(Path(directory) / name)
    framework = sysconfig.get_config_var("PYTHONFRAMEWORK")
    prefix = sysconfig.get_config_var("PYTHONFRAMEWORKPREFIX")
    version = sysconfig.get_config_var("VERSION")
    if prefix and framework and version:
        candidates.append(Path(prefix) / (framework + ".framework") / "Versions" / version / framework)
    for path in candidates:
        if path.is_file() and path.suffix != ".a":
            return path.resolve()
    raise RuntimeError("macOS C ABI tests require an embeddable Python framework/dylib (use setup-python)")


def run_in_app_host_if_needed():
    if sys.platform != "darwin" or os.environ.get("NIMBO_NATIVE_TEST_HOST") == "1":
        return
    with tempfile.TemporaryDirectory(prefix="nimbo-cabi-apple-host-") as directory:
        root = Path(directory)
        source = root / "host.c"
        source.write_text("""
#include <dlfcn.h>
#include <stdio.h>
int main(int argc, char **argv) {
    void *library = dlopen(PYTHON_LIBRARY, RTLD_NOW | RTLD_GLOBAL);
    if (!library) { fprintf(stderr, "Python load: %s\\n", dlerror()); return 80; }
    int (*run)(int, char **) = (int (*)(int, char **))dlsym(library, "Py_BytesMain");
    if (!run) { fprintf(stderr, "Py_BytesMain unavailable\\n"); return 81; }
    return run(argc, argv);
}
""".replace("PYTHON_LIBRARY", json.dumps(str(python_library()))), encoding="utf-8")
        bundle_executables = []
        for extension, package in (("app", "APPL"), ("appex", "XPC!")):
            bundle = root / ("NimboNativeTests." + extension)
            executable = bundle / "Contents/MacOS/NimboNativeTests"
            executable.parent.mkdir(parents=True)
            with (bundle / "Contents/Info.plist").open("wb") as file:
                plistlib.dump({"CFBundleExecutable": executable.name,
                               "CFBundleIdentifier": "com.nimbo.native-test." + extension,
                               "CFBundleName": "NimboNativeTests", "CFBundleVersion": "1",
                               "CFBundlePackageType": package}, file)
            bundle_executables.append(executable)
        subprocess.run(["xcrun", "--sdk", "macosx", "clang", str(source), "-o", str(bundle_executables[0])], check=True)
        shutil.copy2(bundle_executables[0], bundle_executables[1])
        environment = os.environ.copy()
        environment["PYTHONHOME"] = sys.base_prefix
        # This marker prevents recursive test-runner embedding only. It is not
        # consulted anywhere in production C/Go code and cannot disable its guard.
        environment["NIMBO_NATIVE_TEST_HOST"] = "1"
        diagnostic = Path(__file__).with_name("test-libxray-diagnostic.py")
        library = str(Path(sys.argv[1]).resolve())
        for executable, expected in zip(bundle_executables, ("DIAGNOSTIC_UNSUPPORTED", "DIAGNOSTIC_APP_ONLY")):
            subprocess.run([str(executable), str(diagnostic), library, "--guard-only", expected],
                           env=environment, check=True)
        subprocess.run([str(bundle_executables[0]), str(Path(sys.argv[0]).resolve()), *sys.argv[1:]],
                       env=environment, check=True)
    raise SystemExit(0)
