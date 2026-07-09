#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Load local configuration before applying script defaults.
# shellcheck disable=SC1091
source "$PROJECT_ROOT/scripts/load-env.sh"
LOCAL_LLM_HOST="${LOCAL_LLM_HOST:-127.0.0.1}"
LOCAL_LLM_PORT="${LOCAL_LLM_PORT:-8080}"
QWEN_CONTEXT_SIZE="${QWEN_CONTEXT_SIZE:-4096}"
QWEN_THREADS="${QWEN_THREADS:-4}"
PYTHON_BIN="${PYTHON_BIN:-}"
if [[ -z "$PYTHON_BIN" && -x "$PROJECT_ROOT/.venv/bin/python" ]]; then
  PYTHON_BIN="$PROJECT_ROOT/.venv/bin/python"
elif [[ -z "$PYTHON_BIN" ]]; then
  PYTHON_BIN="python3"
fi

resolve_model_path() {
  if [[ -n "${QWEN_MODEL_PATH:-}" ]]; then
    if [[ -f "$QWEN_MODEL_PATH" ]]; then
      printf '%s\n' "$QWEN_MODEL_PATH"
      return 0
    fi
    printf 'QWEN_MODEL_PATH points to a missing file: %s\n' "$QWEN_MODEL_PATH" >&2
    return 1
  fi

  local candidates=(
    "$PROJECT_ROOT/models"
    "$HOME/local-ai-workspace/models"
    "$HOME/local-ia-workspace/models"
  )

  local dir
  for dir in "${candidates[@]}"; do
    if [[ -d "$dir" ]]; then
      local found
      found="$(find "$dir" -type f -name '*.gguf' | sort | head -n 1)"
      if [[ -n "$found" ]]; then
        printf '%s\n' "$found"
        return 0
      fi
    fi
  done

  printf 'No GGUF model was found. Set QWEN_MODEL_PATH or place a .gguf file under ./models, ~/local-ai-workspace/models, or ~/local-ia-workspace/models.\n' >&2
  return 1
}

MODEL_PATH="$(resolve_model_path)"

printf 'Starting local Qwen model: %s\n' "$MODEL_PATH"
printf 'OpenAI-compatible endpoint: http://%s:%s/v1\n' "$LOCAL_LLM_HOST" "$LOCAL_LLM_PORT"

exec "$PYTHON_BIN" -m llama_cpp.server \
  --model "$MODEL_PATH" \
  --host "$LOCAL_LLM_HOST" \
  --port "$LOCAL_LLM_PORT" \
  --n_ctx "$QWEN_CONTEXT_SIZE" \
  --n_threads "$QWEN_THREADS"
