#!/usr/bin/env bash
set -euo pipefail

CALLER_DIR="$PWD"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck disable=SC1091
source "$PROJECT_ROOT/scripts/load-env.sh"
export PROJECT_READER_ROOT="${PROJECT_READER_ROOT:-$CALLER_DIR}"

cd "$PROJECT_ROOT"
exec mvn -q exec:java -Dexec.mainClass="dev.oswaldo.localai.LocalAiCli" -Dexec.args="$*"
