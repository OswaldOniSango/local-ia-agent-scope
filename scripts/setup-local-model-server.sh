#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VENV_DIR="${LOCAL_AI_VENV:-$PROJECT_ROOT/.venv}"

python3 -m venv "$VENV_DIR"
"$VENV_DIR/bin/python" -m pip install --upgrade pip
"$VENV_DIR/bin/python" -m pip install "llama-cpp-python[server]"

printf 'Local model server environment is ready: %s\n' "$VENV_DIR"
printf 'Start it with: ./scripts/start-local-qwen.sh\n'
