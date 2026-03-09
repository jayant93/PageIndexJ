# PageIndexJ — Project Context & Optimization Log

This file is the master reference for ongoing optimizations to PageIndexJ.
Each optimization round is documented as a versioned snapshot in `docs/optimizations/`.

---

## Project Overview

**PageIndexJ** is a Java port of [PageIndex](https://github.com/VectifyAI/PageIndex) (Python, VectifyAI).
It is a vectorless, reasoning-based RAG framework that builds hierarchical tree indexes over documents
(PDF + Markdown) using LLM calls, achieving 98.7% accuracy on FinanceBench.

**Repo:** `github.com/jayant93/PageIndexJ`
**License:** MIT
**Branch:** `main`

---

## Architecture

### Entry Points
| File | Role |
|------|------|
| [PageIndexMain.java](src/main/java/ai/pageindex/PageIndexMain.java) | CLI entry point — parses args, calls PageIndex API |
| [PageIndex.java](src/main/java/ai/pageindex/PageIndex.java) | Public programmatic API (`pageIndex()`, `pageIndexMain()`, `toJson()`) |

### Core Engine
| File | Role |
|------|------|
| [PageIndexPdf.java](src/main/java/ai/pageindex/core/PageIndexPdf.java) | Full 3-stage PDF indexing pipeline (TOC detect → verify → tree build + enrich) |
| [PageIndexMd.java](src/main/java/ai/pageindex/core/PageIndexMd.java) | Markdown indexing engine (heading-based tree + optional thinning) |

### Utilities
| File | Role |
|------|------|
| [OpenAIClient.java](src/main/java/ai/pageindex/util/OpenAIClient.java) | OkHttp REST client for OpenAI (sync `call()` + async via `EXECUTOR`) |
| [PdfParser.java](src/main/java/ai/pageindex/util/PdfParser.java) | PDFBox 3 text extraction + JTokkit token counting |
| [TreeUtils.java](src/main/java/ai/pageindex/util/TreeUtils.java) | Tree/JSON manipulation, node ID assignment, text attach/remove |
| [JsonLogger.java](src/main/java/ai/pageindex/util/JsonLogger.java) | Per-run structured JSON audit log to `./logs/` |

### Config
| File | Role |
|------|------|
| [PageIndexConfig.java](src/main/java/ai/pageindex/config/PageIndexConfig.java) | Config POJO with defaults (mutable public fields) |
| [ConfigLoader.java](src/main/java/ai/pageindex/config/ConfigLoader.java) | Loads `config.yaml` + merges user overrides |
| [config.yaml](src/main/resources/config.yaml) | Default configuration file |

---

## Three-Stage PDF Pipeline

```
Stage 1 — Structure Extraction
  Has TOC? → YES → TOC has page numbers? → YES: extract + offset
                                         → NO:  locate sections
         → NO  → LLM generates structure from page groups

Stage 2 — Verification & Correction
  Concurrent LLM checks per section title
  accuracy > 60%  → fix incorrect items (up to 3 retries)
  accuracy <= 60% → fall back to simpler extraction mode

Stage 3 — Tree Building & Enrichment
  Flat list → nested tree (by structure codes 1, 1.1, 1.2…)
  Large nodes recursively re-indexed into sub-trees
  Optional: node IDs · LLM summaries · document description
```

---

## Technology Stack

| Concern | Technology |
|---------|-----------|
| Language | Java 21 LTS |
| Build | Maven 3.8+ |
| HTTP client | OkHttp 4.12 |
| PDF parsing | Apache PDFBox 3.0.3 |
| Token counting | JTokkit 1.1.0 |
| JSON | Jackson 2.17 |
| YAML config | SnakeYAML 2.2 |
| Env vars | dotenv-java 3.0 |
| Logging | SLF4J Simple 2.0 |
| LLM | OpenAI GPT-4o (via REST) |
| Async | `CompletableFuture` + `ExecutorService` |

---

## Test Suite (as of v1)

| Class | Tests |
|-------|-------|
| ConfigLoaderTest | 6 |
| PageIndexConfigTest | 5 |
| TreeUtilsTest | 15 |
| JsonLoggerTest | 9 |
| PdfParserTest | 11 |
| OpenAIClientTest | 8 (1 skipped: needs API key) |
| PageIndexMdTest | 8 |
| PageIndexPdfTest | 7 |
| PageIndexTest | 9 |
| PageIndexMainTest | 3 |
| **Total** | **82 tests, 81 passing** |

Framework: JUnit Jupiter 5.10.0 + Mockito 4.11.0
Coverage estimate: 45–60%

---

## Configuration Defaults

```yaml
model: "gpt-4o-2024-11-20"
toc_check_page_num: 20
max_page_num_each_node: 10
max_token_num_each_node: 20000
if_add_node_id: "yes"
if_add_node_summary: "yes"
if_add_doc_description: "no"
if_add_node_text: "no"
```

---

## Output Format

```json
{
  "doc_name": "report.pdf",
  "doc_description": "...",
  "structure": [
    {
      "title": "Section Title",
      "node_id": "0001",
      "start_index": 5,
      "end_index": 12,
      "summary": "...",
      "nodes": [ ... ]
    }
  ]
}
```

---

## Optimization History

| Version | Date | Description | Document |
|---------|------|-------------|----------|
| v1 | 2026-03-05 | Baseline — Java 21, 82 unit tests, clean build | [v1_baseline.md](docs/optimizations/v1_baseline.md) |
| v2 | 2026-03-06 | Local model support — Ollama/LM Studio via `LLM_BASE_URL` env var, API key optional | [v2_local_model_support.md](docs/optimizations/v2_local_model_support.md) |
| v3 | 2026-03-06 | CLI `--base-url` param wired through full pipeline — no `.env` needed to switch providers | [v3_cli_base_url_param.md](docs/optimizations/v3_cli_base_url_param.md) |

---

## Known Areas for Improvement

- `PageIndexConfig` uses mutable public fields (not idiomatic Java; candidate for record/builder pattern)
- No retry/back-off logic in `OpenAIClient` for rate limit (429) responses
- Token counting in `PdfParser` is per-page but no streaming support for very large PDFs
- Test coverage estimated at 45–60%; integration tests against real PDFs are absent
- `ifAddNodeSummary` / `ifAddNodeId` etc. use `"yes"`/`"no"` strings instead of booleans
- No caching layer — every run re-calls OpenAI for all LLM steps
- `PageIndexConfig` fields are not validated on load
- Logging uses `System.out.println` alongside SLF4J inconsistently

---

## How to Use This File

1. Before starting an optimization session, read this file to understand current state.
2. After completing an optimization, update the **Optimization History** table above.
3. Create a new snapshot in `docs/optimizations/vN_<short_name>.md` documenting:
   - What changed
   - Why (motivation / problem solved)
   - Before/after metrics (build time, test count, coverage, performance, etc.)
   - Files modified
