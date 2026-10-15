"""Execute portable startup regressions against production Swift policies/store."""
from pathlib import Path
import argparse
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--swiftc', default='swiftc')
    parser.add_argument('--store-only', action='store_true')
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='nimbo-startup-safety-') as temporary:
        work = Path(temporary)
        # Security is unavailable on Linux. Replace that import and the
        # observable-defaults seam only, never the production lock/record code.
        store = (ROOT / 'iosApp/Nimbo/NimboConfigurationStore.swift').read_text()
        portable = work / 'ConfigurationStore.swift'
        portable.write_text(store.replace('import Security\n', '').replace('UserDefaults.', 'LaunchTestDefaults.'))
        binary = work / 'store-tests'
        subprocess.run([args.swiftc, '-swift-version', '5', '-parse-as-library', str(portable),
                        str(ROOT / 'iosApp/Tests/ConfigurationStoreLaunchStubs.swift'),
                        str(ROOT / 'iosApp/Tests/ConfigurationStoreLaunchTests.swift'),
                        '-o', str(binary)], check=True)
        subprocess.run([str(binary)], check=True, timeout=15)
        if not args.store_only:
            binary = work / 'shortcut-tests'
            subprocess.run([args.swiftc, '-swift-version', '5', '-parse-as-library',
                            str(ROOT / 'iosApp/Nimbo/NimboShortcutRegistration.swift'),
                            str(ROOT / 'iosApp/Tests/ShortcutRegistrationTests.swift'),
                            '-o', str(binary)], check=True)
            subprocess.run([str(binary)], check=True, timeout=15)


if __name__ == '__main__':
    main()
