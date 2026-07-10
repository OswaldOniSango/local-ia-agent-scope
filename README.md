# Local AI Assistant AgentScope Java

A local AI assistant built with Alibaba AgentScope Runtime Java. It runs an AgentScope A2A server and sends model calls to a local Qwen GGUF model exposed through an OpenAI-compatible endpoint.

## How It Works

```text
User / A2A client
  -> AgentScope AgentApp
  -> LocalAiAgentHandler
  -> ReActAgent
  -> OpenAI-compatible local endpoint
  -> Qwen GGUF model
```

The Java app does not call a cloud model by default. The model must be running locally before AgentScope Java can answer.

## Configuration

Local configuration lives in `.env`. This file is ignored by git. Start from the example if needed:

```bash
cp .env.example .env
```

Default local config:

```bash
QWEN_MODEL_PATH=/Users/oswaldohernandez/local-ai-workspace/models/qwen2.5-3b/qwen2.5-3b-instruct-q5_k_m.gguf
QWEN_CONTEXT_SIZE=4096
QWEN_THREADS=4
QWEN_MAX_TOKENS=256
WEB_CONTEXT_MAX_CHARS=6000
PROJECT_CONTEXT_MAX_CHARS=7000
LOCAL_LLM_BASE_URL=http://127.0.0.1:8080/v1
LOCAL_LLM_MODEL=qwen-local
LOCAL_LLM_API_KEY=local
AGENTSCOPE_PORT=10001
```

## Requirements

- Java 17+
- Maven 3.6+
- Python with `llama-cpp-python` installed
- A local `.gguf` Qwen model

Install the local model server dependencies inside this project:

```bash
./scripts/setup-local-model-server.sh
```

This creates `.venv` locally and avoids modifying the system Python installation.

## 1. Start The Local Model

From this project directory:

```bash
cd /Users/oswaldohernandez/personal-project/local-ai-assistant-agentscope-java
./scripts/setup-local-model-server.sh
./scripts/start-local-qwen.sh
```

Optional tuning:

```bash
export QWEN_CONTEXT_SIZE=4096
export QWEN_THREADS=4
QWEN_MAX_TOKENS=256
WEB_CONTEXT_MAX_CHARS=6000
PROJECT_CONTEXT_MAX_CHARS=7000
export LOCAL_LLM_HOST=127.0.0.1
export LOCAL_LLM_PORT=8080
```

If `QWEN_MODEL_PATH` is not set, the script searches for a `.gguf` file under:

- `./models`
- `~/local-ai-workspace/models`
- `~/local-ia-workspace/models`

The default model endpoint is:

```text
http://127.0.0.1:8080/v1
```

## 2. Smoke Test The Model Endpoint

In another terminal:

```bash
curl http://127.0.0.1:8080/v1/models
```

Then test a chat completion directly:

```bash
curl http://127.0.0.1:8080/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer local' \
  -d '{
    "model": "qwen-local",
    "messages": [
      { "role": "user", "content": "Responde en español: que eres?" }
    ],
    "temperature": 0.2,
    "max_tokens": 128
  }'
```

If this fails, fix the local model server before starting the Java app.

## 3. Start AgentScope Java

In another terminal:

```bash
cd /Users/oswaldohernandez/personal-project/local-ai-assistant-agentscope-java
./scripts/start-agentscope.sh
```

Defaults:

- `LOCAL_LLM_BASE_URL=http://127.0.0.1:8080/v1`
- `LOCAL_LLM_MODEL=qwen-local`
- `LOCAL_LLM_API_KEY=local`
- `AGENTSCOPE_PORT=10001`

## CLI Usage

The CLI is the Java equivalent of the Python MVP commands. The local model server must be running first.

```bash
./scripts/assistant.sh chat "Explain what Snowflake is"
./scripts/assistant.sh ask "Is Aaron Judge injured right now?"
./scripts/assistant.sh search "Who is Aaron Judge?"
./scripts/assistant.sh search-answer "Who is Aaron Judge?"
./scripts/assistant.sh project-answer "Explain this project"
```

For `project-answer`, run `assistant.sh` from the project you want to inspect. The script captures that directory as `PROJECT_READER_ROOT`. You can also set `PROJECT_READER_ROOT=/path/to/project` explicitly.

## 4. Test AgentScope A2A

```bash
curl --location --request POST 'http://localhost:10001/a2a/' \
  --header 'Content-Type: application/json' \
  --data-raw '{
    "method": "message/stream",
    "id": "2d2b4dc8-8ea2-437b-888d-3aaf3a8239dc",
    "jsonrpc": "2.0",
    "params": {
      "configuration": { "blocking": false },
      "message": {
        "role": "user",
        "kind": "message",
        "metadata": {
          "userId": "me",
          "sessionId": "local-test"
        },
        "parts": [
          {
            "kind": "text",
            "text": "Hola, responde en español y dime que modelo estás usando."
          }
        ],
        "messageId": "local-test-message-1"
      }
    }
  }'
```

You should see streamed A2A events with the assistant response.

## 5. Run Tests

```bash
mvn test
```

## Current Features

- CLI commands: `chat`, `ask`, `search`, `search-answer`, and `project-answer`.
- Local model client for OpenAI-compatible endpoints.
- AgentScope Runtime Java A2A server.
- Agent handler using official AgentScope abstractions.
- ReAct agent backed by a local OpenAI-compatible Qwen endpoint.
- Web search RAG with model-generated search queries.
- Project file reader RAG.
- Local `.env` configuration.
- In-memory state, session history, and memory services.
- System prompt that answers in the same language as the user.

## Not Implemented Yet

- Direct GGUF loading inside the JVM. The current local path uses `llama_cpp.server` as the model runtime.
- Full AgentScope tools wired into `Toolkit`; current CLI pipelines run as regular Java services.
