# v1 — Baseline

**Date:** 2026-03-05
**Status:** Reference baseline — no changes made

---

## What This Version Is

This is the initial Java port of PageIndex (Python/VectifyAI), as committed on the `main` branch.
It establishes the baseline against which all future optimizations are measured.

---

## State of the Codebase

### Build
- Tool: Maven 3.8+
- Java target: 21 LTS
- Artifact: `target/pageindexj-1.0.0-jar-with-dependencies.jar`
- Build status: SUCCESS (clean)

### Source Files (10 classes)

| Package | Class | Lines (approx) |
|---------|-------|----------------|
| `ai.pageindex` | PageIndex.java | 148 |
| `ai.pageindex` | PageIndexMain.java | ~80 |
| `ai.pageindex.config` | PageIndexConfig.java | 32 |
| `ai.pageindex.config` | ConfigLoader.java | ~60 |
| `ai.pageindex.core` | PageIndexPdf.java | ~600 |
| `ai.pageindex.core` | PageIndexMd.java | ~300 |
| `ai.pageindex.util` | OpenAIClient.java | ~150 |
| `ai.pageindex.util` | PdfParser.java | ~120 |
| `ai.pageindex.util` | TreeUtils.java | ~200 |
| `ai.pageindex.util` | JsonLogger.java | ~60 |

### Tests
- Framework: JUnit Jupiter 5.10.0 + Mockito 4.11.0
- Total tests: 82
- Passing: 81
- Skipped: 1 (OpenAIClientTest — real API key needed)
- Failed: 0
- Coverage estimate: 45–60%

### Dependencies
| Library | Version |
|---------|---------|
| OkHttp | 4.12 |
| PDFBox | 3.0.3 |
| JTokkit | 1.1.0 |
| Jackson | 2.17 |
| SnakeYAML | 2.2 |
| dotenv-java | 3.0 |
| SLF4J Simple | 2.0 |

---

## Known Issues / Improvement Opportunities

1. **Config design** — `PageIndexConfig` uses mutable public fields. No validation on load.
   `ifAddNodeSummary` etc. use `"yes"`/`"no"` strings instead of booleans.

2. **Error handling in OpenAIClient** — No retry or exponential back-off for HTTP 429 (rate limit)
   or transient 5xx errors. A failed LLM call propagates as an exception with no recovery.

3. **Logging inconsistency** — `System.out.println` used alongside SLF4J. Should be unified.

4. **No caching** — Every run re-calls OpenAI for all LLM steps (TOC, verification, summaries).
   Re-indexing the same document costs the same as a first run.

5. **Token counting scope** — Token counting happens per-page at parse time. No streaming
   or chunked processing for very large PDFs.

6. **Integration tests missing** — All tests mock LLM calls. No end-to-end test against
   real PDFs to validate accuracy regressions.

7. **Thread pool sizing** — `OpenAIClient.EXECUTOR` uses a fixed thread pool. No config
   to tune concurrency based on API rate limits or host resources.

8. **Output file location hardcoded** — `results/` directory is hardcoded with no CLI override.

---

## Metrics (Baseline)

| Metric | Value |
|--------|-------|
| Source classes | 10 |
| Test classes | 10 |
| Total tests | 82 |
| Test pass rate | 98.8% (1 conditional skip) |
| Estimated coverage | 45–60% |
| Build time (approx) | ~15–20s (with tests) |
| LLM calls per PDF run | varies (~5–20 depending on doc size and flags) |
