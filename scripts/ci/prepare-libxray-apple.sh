#!/usr/bin/env bash
set -euo pipefail
# The prebuilt upstream archive lacks Nimbo's AWG C exports.
exec bash "$(dirname "${BASH_SOURCE[0]}")/build-libxray-awg-apple.sh"
