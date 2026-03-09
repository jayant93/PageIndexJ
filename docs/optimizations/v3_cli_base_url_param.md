# v3 — CLI `--base-url` Parameter (Full Pipeline Control)

**Date:** 2026-03-06
**Status:** Complete and tested

---

## Motivation

In v2, the LLM endpoint was configurable via the `LLM_BASE_URL` environment variable or `.env` file.
This required editing a file on disk to switch between OpenAI and a local model server per run.

Goal: wire the endpoint through the full config pipeline so a single command line controls everything —
model, endpoint, and all processing flags — with no file edits between runs.

---

## What Changed

### Files modified

| File | Change |
|------|--------|
| `PageIndexConfig.java` | Added `baseUrl` field (default `""`) and copied it in the copy constructor |
| `ConfigLoader.java` | Added `"base_url"` to `KNOWN_KEYS`; loads it from `config.yaml`; applies user override |
| `OpenAIClient.java` | Renamed no-arg constructor to `OpenAIClient(String baseUrlOverride)` with 3-level resolution; `OpenAIClient()` delegates to it |
| `PageIndex.java` | Passes `opt.baseUrl` to `new OpenAIClient(opt.baseUrl)` |
| `PageIndexMain.java` | Added `--base-url` arg; wires it into `userOpt.baseUrl` for both PDF and Markdown branches |
| `config.yaml` | Added `base_url: ""` with comments |

### Endpoint resolution order (highest → lowest priority)

```
1. --base-url <arg>             (CLI param — this version)
2. LLM_BASE_URL in .env file    (v2)
3. LLM_BASE_URL system env var  (v2)
4. https://api.openai.com/...   (default)
```

---

## Usage

### Run with Ollama — no .env required
```bash
java -jar target/pageindexj-1.0.0-jar-with-dependencies.jar \
  --md_path doc.md \
  --model gemma3:1b \
  --base-url http://localhost:11434/v1/chat/completions
```

### Run with OpenAI — no .env required
```bash
java -jar target/pageindexj-1.0.0-jar-with-dependencies.jar \
  --pdf_path report.pdf \
  --model gpt-4o-2024-11-20 \
  --base-url https://api.openai.com/v1/chat/completions
```

### Switch between providers in one command — no file edits
```bash
# Local
java -jar pageindexj.jar --md_path doc.md --model gemma3:1b \
  --base-url http://localhost:11434/v1/chat/completions

# Cloud
java -jar pageindexj.jar --md_path doc.md --model gpt-4o-2024-11-20 \
  --base-url https://api.openai.com/v1/chat/completions
```

---

## Test Results

**Test:** `README.md` via `--base-url http://localhost:11434/v1/chat/completions`, `.env` file absent

```
Processing markdown file...
LLM endpoint: http://localhost:11434/v1/chat/completions
Extracting nodes from markdown...
Building tree from nodes...
Parsing done, saving to file...
Tree structure saved to: results/README_structure.json
```

**Result:** PASS — `--base-url` took effect, `.env` was not required

---

## Metrics vs v2

| Metric | v2 | v3 |
|--------|----|----|
| Files changed | 1 | 6 |
| Requires .env to use Ollama | Yes | No |
| Endpoint configurable from CLI | No | Yes |
| Config precedence documented | No | Yes |
| Build status | SUCCESS | SUCCESS |
| Test: README.md via --base-url (no .env) | N/A | PASS |
