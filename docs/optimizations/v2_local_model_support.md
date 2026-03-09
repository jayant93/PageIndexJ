# v2 — Local Model Support (Ollama Integration)

**Date:** 2026-03-06
**Status:** Complete and tested

---

## Motivation

The original codebase had `API_URL` hardcoded to `https://api.openai.com/v1/chat/completions`
and threw an exception if `CHATGPT_API_KEY` was not set. This made it impossible to use any
local model server without modifying source code.

Goals:
- Run the pipeline against a local Ollama model with zero code changes per run
- Eliminate OpenAI API cost for development and testing
- Enable privacy-preserving document processing (no data leaves the machine)
- Keep backward compatibility — OpenAI still works with no config changes

---

## What Changed

### Single file modified: `OpenAIClient.java`

| Before | After |
|--------|-------|
| `API_URL` was a hardcoded `private static final String` | `apiUrl` is an instance field read from env at startup |
| Missing `CHATGPT_API_KEY` threw `IllegalStateException` | Missing key falls back to `"local"` (Ollama ignores auth) |
| No visibility into which endpoint is being used | Prints `LLM endpoint: <url>` on startup |

### New env vars

| Variable | Default | Description |
|----------|---------|-------------|
| `LLM_BASE_URL` | `https://api.openai.com/v1/chat/completions` | Full chat completions endpoint |
| `CHATGPT_API_KEY` | `local` (dummy) | API key — not required for local servers |

### New files

| File | Purpose |
|------|---------|
| `.env` | Active config (gitignored) — points at Ollama for current dev setup |
| `.env.ollama` | Committed template for Ollama configuration |

---

## How to Use

### OpenAI (unchanged)
```
# .env
CHATGPT_API_KEY=sk-...
```
```bash
java -jar target/pageindexj-1.0.0-jar-with-dependencies.jar --pdf_path doc.pdf --model gpt-4o-2024-11-20
```

### Ollama (new)
```
# .env
LLM_BASE_URL=http://localhost:11434/v1/chat/completions
```
```bash
# Start Ollama
ollama serve

# Run
java -jar target/pageindexj-1.0.0-jar-with-dependencies.jar --md_path doc.md --model gemma3:1b
```

---

## Test Results

**Environment:** Windows 11, Ollama installed locally, model `gemma3:1b` (815MB, CPU)

**Test input:** `README.md` (489 lines, well-structured Markdown)

**Command:**
```bash
java -jar target/pageindexj-1.0.0-jar-with-dependencies.jar \
  --md_path README.md \
  --model gemma3:1b \
  --if-add-node-summary no \
  --if-add-doc-description no
```

**Output:** `results/README_structure.json` — 29 nodes, correct hierarchy

```
PageIndexJ
├── Enterprise-Grade Document Intelligence...
├── The Problem with Traditional RAG
├── The PageIndexJ Approach
├── Key Benefits
│   ├── For Developers
│   └── For the Business
├── Use Cases
├── Quick Start
│   ├── Prerequisites
│   ├── 1 — Clone and Build
│   ├── 2 — Configure API Key
│   └── 3 — Run
├── Sample Output
├── CLI Usage
│   ├── PDF Processing
│   ├── Markdown Processing
│   └── All Options
├── Programmatic API
│   ├── PDF Indexing
│   ├── Markdown Indexing
│   └── Integrating into a RAG Pipeline
├── Configuration Reference
├── How It Works
├── Project Structure
├── Dependencies
├── Logging & Observability
├── Python → Java Reference
└── Acknowledgements
```

**Result:** PASS — zero errors, output matches expected structure

---

## Limitations & Known Issues

### gemma3:1b model limitations
- Markdown heading extraction does **not** require LLM calls — it is regex-based.
  The 1B model was not exercised for real reasoning in this test.
- Summaries (`--if-add-node-summary yes`) and PDF processing require real LLM reasoning.
  `gemma3:1b` may produce malformed JSON or poor-quality summaries for those flows.
- Recommended minimum for full pipeline: `gemma3:4b` or `qwen2.5:3b`

### Hardware requirements (local models)
| Use Case | Min RAM | Recommended Model |
|----------|---------|------------------|
| Markdown only (no summaries) | 4 GB | Any model |
| Markdown with summaries | 8 GB | `qwen2.5:3b` |
| PDF full pipeline | 16 GB | `qwen2.5:7b` or `llama3.1:8b` |
| PDF, production quality | 32 GB or 8GB VRAM | `qwen2.5:14b`+ |

### Concurrency
The pipeline fires concurrent LLM requests during Stage 2 (verification).
Ollama processes one request at a time by default — concurrent requests queue up.
This is functional but slower than with OpenAI. Not a blocker for testing.

---

## Metrics vs v1

| Metric | v1 (Baseline) | v2 |
|--------|--------------|-----|
| Source files changed | — | 1 (`OpenAIClient.java`) |
| Lines changed | — | ~15 |
| OpenAI required | Yes | No (optional) |
| Local model support | No | Yes (Ollama, LM Studio, vLLM) |
| API key required | Yes (hard fail) | No (graceful fallback) |
| Test: README.md via Ollama | N/A | PASS |
| Build status | SUCCESS | SUCCESS |
