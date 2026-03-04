<div align="center">

# PageIndexJ

### Enterprise-Grade Document Intelligence for the Java Ecosystem

**Vectorless · Reasoning-Based · Production-Ready**

[![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.8%2B-C71A36?style=for-the-badge&logo=apache-maven&logoColor=white)](https://maven.apache.org/)
[![OpenAI](https://img.shields.io/badge/OpenAI-GPT--4o-412991?style=for-the-badge&logo=openai&logoColor=white)](https://openai.com/)
[![License](https://img.shields.io/badge/License-MIT-22C55E?style=for-the-badge)](LICENSE)
[![Port of PageIndex](https://img.shields.io/badge/Port%20of-PageIndex-3B82F6?style=for-the-badge)](https://github.com/VectifyAI/PageIndex)

<br/>

> **PageIndexJ** brings the power of [PageIndex](https://github.com/VectifyAI/PageIndex) — the vectorless, reasoning-based RAG framework that achieved **98.7% accuracy on FinanceBench** — to the Java enterprise ecosystem.

<br/>

[Getting Started](#-quick-start) · [Documentation](#-cli-usage) · [API Reference](#-programmatic-api) · [Use Cases](#-use-cases) · [Architecture](#-how-it-works)

</div>

---

## The Problem with Traditional RAG

Most enterprise document pipelines rely on vector similarity search:

```
Document → Chunk → Embed → Store in Vector DB → Retrieve by cosine similarity
```

This works for simple lookups. But **similarity ≠ relevance** — especially for:

- 📋 **Financial reports** with cross-referenced figures and footnotes
- ⚖️ **Legal contracts** with defined terms and nested clauses
- 🏥 **Technical manuals** with deeply hierarchical structure
- 📚 **Regulatory filings** requiring domain expertise to navigate

**Result:** Hallucinations, missed context, and opaque "vibe retrieval" that cannot be audited.

---

## The PageIndexJ Approach

```
Document → Build Hierarchical Tree Index → Reason over the Index → Retrieve exact section
```

PageIndexJ builds a **smart Table of Contents** from your document and uses an LLM to *think* its way to the right section — exactly as a human expert would flip through a book.

<br/>

<div align="center">

```
┌─────────────────────────────────────────────────────────┐
│                    INPUT DOCUMENT                        │
│          Annual Report · Legal Contract · Manual         │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│                PAGEINDEXJ ENGINE                         │
│                                                          │
│  ┌─────────────┐    ┌──────────────┐    ┌────────────┐  │
│  │ TOC Detect  │───▶│ Tree Builder │───▶│  Verifier  │  │
│  │  (LLM)      │    │  (LLM)       │    │  (LLM)     │  │
│  └─────────────┘    └──────────────┘    └────────────┘  │
│         │                  │                   │         │
│         └──────────────────▼───────────────────┘         │
│                    ┌──────────────┐                       │
│                    │  Enrichment  │ IDs · Summaries       │
│                    └──────────────┘                       │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│                  JSON TREE OUTPUT                        │
│   { title, node_id, start_index, end_index, summary }   │
└─────────────────────────────────────────────────────────┘
```

</div>

---

## Key Benefits

<table>
<tr>
<td width="50%">

### For Developers
- Drop-in Java library — Maven dependency ready
- Async concurrent LLM calls out of the box
- Clean public API with sensible defaults
- Full CLI for pipeline integration
- Structured JSON output compatible with any downstream system

</td>
<td width="50%">

### For the Business
- **98.7% accuracy** on FinanceBench vs ~60–70% for vector RAG
- Traceable retrieval — every answer cites exact page numbers
- No vector database infrastructure to manage or scale
- Works on documents that exceed LLM context windows
- Audit-ready: reasoning chain is logged per run

</td>
</tr>
</table>

---

## Use Cases

| Industry | Document Type | What PageIndexJ Enables |
|---|---|---|
| **Financial Services** | SEC filings, earnings reports, prospectuses | Precise figure extraction, cross-section Q&A |
| **Legal** | Contracts, regulatory filings, case law | Clause location, defined-term lookup, compliance checks |
| **Healthcare** | Clinical manuals, drug labels, research papers | Protocol retrieval, drug interaction lookup |
| **Engineering** | Technical specs, API docs, maintenance manuals | Step-by-step procedure retrieval, spec lookup |
| **Insurance** | Policy documents, coverage terms | Coverage determination, exclusion identification |
| **Consulting** | Strategy reports, research documents | Insight extraction, cross-document synthesis |

---

## Quick Start

### Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| Java | **17+** | Required for records and text blocks |
| Maven | **3.8+** | Build tool |
| OpenAI API Key | GPT-4o access | Set in `.env` file |

### 1 — Clone and Build

```bash
git clone https://github.com/jayant93/PageIndexJ.git
cd PageIndexJ
mvn package -q
```

Produces a single executable JAR:
```
target/pageindexj-1.0.0-jar-with-dependencies.jar
```

### 2 — Configure API Key

Create a `.env` file in the directory you run the JAR from:

```env
CHATGPT_API_KEY=sk-...your-key-here...
```

### 3 — Run

```bash
# Index a PDF
java -jar target/pageindexj-1.0.0-jar-with-dependencies.jar \
  --pdf_path /path/to/annual-report.pdf

# Index a Markdown file
java -jar target/pageindexj-1.0.0-jar-with-dependencies.jar \
  --md_path /path/to/documentation.md
```

Output saved to `results/<filename>_structure.json`.

---

## Sample Output

Running PageIndexJ on a financial report produces:

```json
{
  "doc_name": "2023-annual-report.pdf",
  "doc_description": "Federal Reserve 2023 Annual Report covering monetary policy, financial stability, and regulatory activities.",
  "structure": [
    {
      "title": "Financial Stability",
      "node_id": "0006",
      "start_index": 21,
      "end_index": 22,
      "summary": "Covers the Federal Reserve's framework for monitoring systemic risk and coordinating with domestic and international regulators.",
      "nodes": [
        {
          "title": "Monitoring Financial Vulnerabilities",
          "node_id": "0007",
          "start_index": 22,
          "end_index": 28,
          "summary": "Details the tools and indicators used to track leverage, liquidity, and interconnectedness across the financial system."
        },
        {
          "title": "Domestic and International Cooperation",
          "node_id": "0008",
          "start_index": 28,
          "end_index": 31,
          "summary": "Describes collaborative efforts with the FSB, BIS, and domestic agencies to address cross-border systemic risks."
        }
      ]
    }
  ]
}
```

> `start_index` / `end_index` are physical PDF page numbers — use them to retrieve exact page content for RAG.

---

## CLI Usage

### PDF Processing

```bash
java -jar pageindexj-1.0.0-jar-with-dependencies.jar --pdf_path <file> [options]
```

### Markdown Processing

```bash
java -jar pageindexj-1.0.0-jar-with-dependencies.jar --md_path <file> [options]
```

### All Options

| Flag | Default | Description |
|---|---|---|
| `--pdf_path` | — | Path to the PDF file |
| `--md_path` | — | Path to the Markdown file |
| `--model` | `gpt-4o-2024-11-20` | OpenAI model |
| `--toc-check-pages` | `20` | Pages scanned for existing TOC |
| `--max-pages-per-node` | `10` | Max pages per node before recursive split |
| `--max-tokens-per-node` | `20000` | Max tokens per node before recursive split |
| `--if-add-node-id` | `yes` | Assign sequential node IDs |
| `--if-add-node-summary` | `yes` | Generate LLM summary per node |
| `--if-add-doc-description` | `no` | Generate one-sentence document description |
| `--if-add-node-text` | `no` | Include raw page text in output |
| `--if-thinning` | `no` | *(Markdown)* Collapse nodes below token threshold |
| `--thinning-threshold` | `5000` | *(Markdown)* Min tokens per node |
| `--summary-token-threshold` | `200` | *(Markdown)* Skip summary for short nodes |

---

## Programmatic API

### PDF Indexing

```java
import ai.pageindex.PageIndex;
import ai.pageindex.config.PageIndexConfig;
import ai.pageindex.config.ConfigLoader;

// Simplest usage — all defaults
Map<String, Object> result = PageIndex.pageIndex("/path/to/document.pdf");

// Custom configuration
PageIndexConfig config = new ConfigLoader().load();
config.model                = "gpt-4o-2024-11-20";
config.ifAddNodeSummary     = "yes";
config.ifAddDocDescription  = "yes";
config.maxPageNumEachNode   = 15;

Map<String, Object> result = PageIndex.pageIndexMain("/path/to/document.pdf", config);

// Export to JSON string
String json = PageIndex.toJson(result);
```

### Markdown Indexing

```java
import ai.pageindex.core.PageIndexMd;
import ai.pageindex.util.OpenAIClient;

OpenAIClient ai = new OpenAIClient();
PageIndexMd indexer = new PageIndexMd(ai);

Map<String, Object> result = indexer.mdToTree(
    "/path/to/documentation.md",
    false,                 // ifThinning
    5000,                  // thinningThreshold (tokens)
    "yes",                 // ifAddNodeSummary
    200,                   // summaryTokenThreshold
    "gpt-4o-2024-11-20",   // model
    "no",                  // ifAddDocDescription
    "no",                  // ifAddNodeText
    "yes"                  // ifAddNodeId
);
```

### Integrating into a RAG Pipeline

```java
// 1. Build the index once and cache the JSON
Map<String, Object> index = PageIndex.pageIndex("/path/to/report.pdf");

// 2. At query time — pass the index tree to your LLM to reason over
//    The LLM selects the relevant node (title + summary)
//    Then fetch pages [start_index, end_index] as grounding context

// 3. Use start_index / end_index to retrieve the exact page text
String pageText = PdfParser.getTextOfPages(
    "/path/to/report.pdf",
    selectedNode.startIndex,
    selectedNode.endIndex,
    false
);

// 4. Pass pageText as context to your final LLM call
```

---

## Configuration Reference

Default settings in [`src/main/resources/config.yaml`](src/main/resources/config.yaml):

```yaml
model: "gpt-4o-2024-11-20"      # OpenAI model for all LLM calls
toc_check_page_num: 20           # Pages scanned for TOC detection
max_page_num_each_node: 10       # Trigger recursive split above this
max_token_num_each_node: 20000   # Trigger recursive split above this
if_add_node_id: "yes"            # Assign breadth-first node IDs (0001, 0002…)
if_add_node_summary: "yes"       # LLM-generated summary per node
if_add_doc_description: "no"     # One-sentence document description
if_add_node_text: "no"           # Include raw page text in output JSON
```

---

## How It Works

PageIndexJ uses a **three-stage pipeline** with automatic fallback:

```
┌──────────────────────────────────────────────────────────────┐
│  STAGE 1 · STRUCTURE EXTRACTION                              │
│                                                              │
│  ┌──────────┐   Has TOC?   ┌─────────────────────────────┐  │
│  │  Scan    │──── YES ────▶│ TOC with page numbers?      │  │
│  │  First   │              │  YES → extract + offset     │  │
│  │  N pages │              │  NO  → locate sections      │  │
│  └──────────┘              └─────────────────────────────┘  │
│       │                                                      │
│       └──── NO ──────────▶  LLM generates structure         │
│                             from scratch (page groups)       │
└──────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌──────────────────────────────────────────────────────────────┐
│  STAGE 2 · VERIFICATION & CORRECTION                         │
│                                                              │
│  Concurrent LLM checks: does each section title appear on    │
│  its claimed page?                                           │
│                                                              │
│  accuracy > 60%  →  fix incorrect items (up to 3 retries)   │
│  accuracy ≤ 60%  →  fall back to simpler extraction mode     │
└──────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌──────────────────────────────────────────────────────────────┐
│  STAGE 3 · TREE BUILDING & ENRICHMENT                        │
│                                                              │
│  Flat list → Nested tree (by structure codes 1, 1.1, 1.2…)  │
│  Large nodes recursively re-indexed into sub-trees           │
│  Optional: node IDs · LLM summaries · document description  │
└──────────────────────────────────────────────────────────────┘
```

---

## Project Structure

```
PageIndexJ/
├── pom.xml                                      # Maven build file
├── .env                                         # API key (create this, not committed)
└── src/main/
    ├── resources/
    │   └── config.yaml                          # Default configuration
    └── java/ai/pageindex/
        │
        ├── PageIndex.java                       # ★ Public API entry point
        ├── PageIndexMain.java                   # CLI entry point
        │
        ├── config/
        │   ├── PageIndexConfig.java             # Configuration data class
        │   └── ConfigLoader.java                # YAML loader with override merging
        │
        ├── util/
        │   ├── OpenAIClient.java                # OpenAI REST API (sync + async)
        │   ├── PdfParser.java                   # PDF text extraction + token counting
        │   ├── TreeUtils.java                   # Tree/JSON manipulation
        │   └── JsonLogger.java                  # Per-run JSON audit log
        │
        └── core/
            ├── PageIndexPdf.java                # Full PDF indexing engine
            └── PageIndexMd.java                 # Markdown indexing engine
```

---

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| `com.squareup.okhttp3:okhttp` | 4.12 | HTTP client for OpenAI REST API |
| `org.apache.pdfbox:pdfbox` | 3.0.3 | PDF text extraction |
| `com.knuddels:jtokkit` | 1.1.0 | Tiktoken-compatible token counting |
| `com.fasterxml.jackson.core:jackson-databind` | 2.17 | JSON parsing and serialization |
| `org.yaml:snakeyaml` | 2.2 | YAML config loading |
| `io.github.cdimascio:dotenv-java` | 3.0 | `.env` file support |
| `org.slf4j:slf4j-simple` | 2.0 | Logging facade |

---

## Logging & Observability

Every run writes a structured JSON audit log to `./logs/<filename>_<timestamp>.json`:

```json
[
  { "total_page_number": 87 },
  { "total_token": 142350 },
  { "toc_content": "...", "page_index_given_in_toc": "yes" },
  { "mode": "process_toc_with_page_numbers", "accuracy": 0.94, "incorrect_results": [...] },
  { "message": "Fixing 3 incorrect results" },
  { "message": "Maximum fix attempts reached" }
]
```

This enables full **auditability** of how the index was built — important for regulated industries.

---

## Python → Java Reference

| Python (original) | Java (this port) |
|---|---|
| `openai` SDK | OkHttp REST calls to OpenAI API |
| `PyPDF2` / `pymupdf` | Apache PDFBox 3 |
| `tiktoken` | JTokkit |
| `pyyaml` | SnakeYAML |
| `python-dotenv` | dotenv-java |
| `asyncio` / `async def` | `CompletableFuture` + `ExecutorService` |
| `asyncio.gather(*tasks)` | `CompletableFuture.allOf(...)` |
| `dict` | `Map<String, Object>` |
| `SimpleNamespace` config | `PageIndexConfig` POJO |
| `json.dumps` | Jackson `ObjectMapper` |

---

## Acknowledgements

PageIndexJ is a Java port of **[PageIndex](https://github.com/VectifyAI/PageIndex)** by [VectifyAI](https://vectify.ai).
All credit for the original framework, retrieval algorithms, and LLM prompts belongs to the PageIndex team.

```bibtex
@article{zhang2025pageindex,
  author  = {Mingtian Zhang and Yu Tang and PageIndex Team},
  title   = {PageIndex: Next-Generation Vectorless, Reasoning-based RAG},
  journal = {PageIndex Blog},
  year    = {2025},
  month   = {September},
  note    = {https://pageindex.ai/blog/pageindex-intro}
}
```

---

<div align="center">

**PageIndexJ** — Java port maintained independently from the original Python project.

[![GitHub](https://img.shields.io/badge/GitHub-jayant93%2FPageIndexJ-181717?style=flat-square&logo=github)](https://github.com/jayant93/PageIndexJ)

© 2025

</div>
