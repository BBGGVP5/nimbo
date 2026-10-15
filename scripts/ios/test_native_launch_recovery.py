"""Focused Foundation startup gate/journal tests plus source wiring contracts; no iOS rendering claim."""
from pathlib import Path
import argparse
import subprocess
import tempfile
import sys

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--swiftc', default='swiftc')
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='nimbo-native-launch-') as tmp:
        binary = Path(tmp) / 'native-launch-tests'
        subprocess.run([args.swiftc, '-swift-version', '5', '-parse-as-library',
                        str(ROOT / 'iosApp/Nimbo/NimboLaunchModel.swift'),
                        str(ROOT / 'iosApp/Nimbo/NimboLaunchJournal.swift'),
                        str(ROOT / 'iosApp/Tests/NativeLaunchRecoveryTests.swift'),
                        '-o', str(binary)], check=True, timeout=120)
        subprocess.run([str(binary)], check=True, timeout=20)
    subprocess.run([sys.executable, '-B', str(ROOT / 'iosApp/Tests/test_native_launch_contracts.py'), '-v'],
                   check=True, timeout=30)
    subprocess.run([sys.executable, '-B', str(ROOT / 'iosApp/Tests/test_app_startup_contracts.py'), '-v'],
                   check=True, timeout=30)


if __name__ == '__main__':
    main()
