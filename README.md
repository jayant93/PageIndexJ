# PageIndexJ

**Java port of [PageIndex](https://github.com/VectifyAI/PageIndex) — Vectorless, Reasoning-based RAG**

> Build a hierarchical tree index from long PDF and Markdown documents using LLM reasoning. No vector database. No chunking. Human-like retrieval.

---

## What is PageIndexJ?

PageIndexJ is a faithful Java translation of the open-source [PageIndex](https://github.com/VectifyAI/PageIndex) framework by VectifyAI.

Instead of chunking documents and relying on cosine similarity for retrieval, PageIndex:

1. Generates a **hierarchical tree structure** (like a smart Table of Contents) from a long document
2. Uses an LLM to **reason** over that tree to navigate directly to the most relevant section

This enables **reasoning-based retrieval** that mirrors how a human expert would navigate a complex document — traceable, explainable, and significantly more accurate than vector similarity search for professional documents.

---

## Features

- **No Vector DB** — document structure + LLM reasoning replaces embedding search
- **No Chunking** — sections are natural document divisions, not artificial splits
- **PDF & Markdown** — supports both `.pdf` and `.md` / `.markdown` input
- **Automatic TOC Detection** — detects existing Table of Contents with or without page numbers; falls back to LLM-generated structure if none found
- **Self-Verifying** — concurrently verifies each section location against actual page text and auto-corrects mismatches
- **Recursive Sub-Indexing** — nodes that are too large are automatically re-indexed to create deeper tree levels
- **Optional Enrichment** — node IDs, LLM-generated summaries, document description, raw page text
- **Parallel LLM Calls** — async-style concurrency via `CompletableFuture` + thread pool

---

## Prerequisites

| Requirement | Version |
|---|---|
| Java | 17+ |
| Maven | 3.8+ |
| OpenAI API Key | (GPT-4o access) |

---

## Build

```bash
git clone https://github.com/your-org/PageIndexJ.git
cd PageIndexJ
mvn package -q
```

This produces a fat JAR at:
```
target/pageindexj-1.0.0-jar-with-dependencies.jar
```

---

## Configuration

Create a `.env` file in the project root (same directory you run the JAR from):

```env
CHATGPT_API_KEY=your_openai_api_key_here
```

Default settings are in [`src/main/resources/config.yaml`](src/main/resources/config.yaml):

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

## CLI Usage

### Process a PDF

```bash
java -jar target/pageindexj-1.0.0-jar-with-dependencies.jar \
  --pdf_path /path/to/document.pdf
```

### Process a Markdown file

```bash
java -jar target/pageindexj-1.0.0-jar-with-dependencies.jar \
  --md_path /path/to/document.md
```

Output is saved to `results/<filename>_structure.json`.

### All options

| Flag | Default | Description |
|---|---|---|
| `--pdf_path` | — | Path to the PDF file |
| `--md_path` | — | Path to the Markdown file |
| `--model` | `gpt-4o-2024-11-20` | OpenAI model to use |
| `--toc-check-pages` | `20` | Pages to scan for an existing TOC |
| `--max-pages-per-node` | `10` | Max pages per tree node before recursive split |
| `--max-tokens-per-node` | `20000` | Max tokens per node before recursive split |
| `--if-add-node-id` | `yes` | Assign sequential node IDs |
| `--if-add-node-summary` | `yes` | Generate LLM summary per node |
| `--if-add-doc-description` | `no` | Generate one-sentence document description |
| `--if-add-node-text` | `no` | Include raw page text in output |
| `--if-thinning` | `no` | *(Markdown only)* Collapse small nodes |
| `--thinning-threshold` | `5000` | *(Markdown only)* Min token count per node |
| `--summary-token-threshold` | `200` | *(Markdown only)* Skip summary if node is shorter |

---

## Programmatic API

### PDF

```java
import ai.pageindex.PageIndex;
import ai.pageindex.config.PageIndexConfig;
import ai.pageindex.config.ConfigLoader;
import java.util.Map;

// Use all defaults
Map<String, Object> result = PageIndex.pageIndex("/path/to/document.pdf");

// With custom config
PageIndexConfig config = new ConfigLoader().load();
config.model = "gpt-4o-2024-11-20";
config.ifAddNodeSummary = "yes";
config.ifAddDocDescription = "yes";

Map<String, Object> result = PageIndex.pageIndexMain("/path/to/document.pdf", config);

// Pretty-print JSON
System.out.println(PageIndex.toJson(result));
```

### Markdown

```java
import ai.pageindex.core.PageIndexMd;
import ai.pageindex.util.OpenAIClient;
import java.util.Map;

OpenAIClient ai = new OpenAIClient();
PageIndexMd indexer = new PageIndexMd(ai);

Map<String, Object> result = indexer.mdToTree(
    "/path/to/document.md",
    false,   // ifThinning
    5000,    // thinningThreshold
    "yes",   // ifAddNodeSummary
    200,     // summaryTokenThreshold
    "gpt-4o-2024-11-20",
    "no",    // ifAddDocDescription
    "no",    // ifAddNodeText
    "yes"    // ifAddNodeId
);
```

---

## Output Format

Both PDF and Markdown produce the same JSON structure:

```json
{
  "doc_name": "annual-report.pdf",
  "doc_description": "A 2023 annual report covering financial performance...",
  "structure": [
    {
      "title": "Financial Stability",
      "node_id": "0006",
      "start_index": 21,
      "end_index": 22,
      "summary": "The Federal Reserve's assessment of...",
      "nodes": [
        {
          "title": "Monitoring Financial Vulnerabilities",
          "node_id": "0007",
          "start_index": 22,
          "end_index": 28,
          "summary": "Covers the monitoring framework used to..."
        },
        {
          "title": "Domestic and International Cooperation",
          "node_id": "0008",
          "start_index": 28,
          "end_index": 31,
          "summary": "In 2023, the Federal Reserve collaborated..."
        }
      ]
    }
  ]
}
```

`start_index` / `end_index` are **physical page numbers** in the original PDF.
For Markdown, `line_num` indicates the source line of each section header.

---

## Project Structure

```
PageIndexJ/
├── pom.xml
└── src/main/
    ├── resources/
    │   └── config.yaml                  # Default configuration
    └── java/ai/pageindex/
        ├── PageIndex.java               # Public API entry point
        ├── PageIndexMain.java           # CLI entry point
        ├── config/
        │   ├── PageIndexConfig.java     # Config data class
        │   └── ConfigLoader.java        # YAML config loader with override merging
        ├── util/
        │   ├── OpenAIClient.java        # OpenAI REST API wrapper (sync + async)
        │   ├── PdfParser.java           # PDF text extraction + token counting
        │   ├── TreeUtils.java           # Tree/JSON manipulation utilities
        │   └── JsonLogger.java          # JSON-based run logger (writes to ./logs/)
        └── core/
            ├── PageIndexPdf.java        # Full PDF indexing engine (~700 lines)
            └── PageIndexMd.java         # Markdown indexing engine
```

---

## Python → Java Translation Reference

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

## Dependencies

| Library | Purpose |
|---|---|
| `com.squareup.okhttp3:okhttp` | HTTP client for OpenAI REST API |
| `org.apache.pdfbox:pdfbox` | PDF text extraction |
| `com.knuddels:jtokkit` | Tiktoken-compatible token counting |
| `com.fasterxml.jackson.core:jackson-databind` | JSON parsing and serialization |
| `org.yaml:snakeyaml` | YAML config loading |
| `io.github.cdimascio:dotenv-java` | `.env` file support |

---

## How the Pipeline Works

```
PDF / Markdown
      │
      ▼
  Page Parser        Extract text + token count per page (PDFBox / plain text)
      │
      ▼
  TOC Detection      Scan first N pages with LLM — TOC present? Has page numbers?
      │
      ├─ TOC + page numbers  →  Extract page numbers, compute offset, map to physical pages
      ├─ TOC, no numbers     →  Ask LLM to locate each section heading in the document
      └─ No TOC              →  LLM generates full structure from scratch (page groups)
      │
      ▼
  Verification       Concurrent LLM checks: does each section title appear on its claimed page?
      │
      ├─ accuracy > 60%  →  Fix incorrect items (up to 3 retry rounds)
      └─ accuracy ≤ 60%  →  Fall back to next processing mode
      │
      ▼
  Tree Building      Convert flat verified list → nested tree (by structure codes 1, 1.1, 1.1.2…)
      │
      ▼
  Recursive Split    Nodes > max_pages AND > max_tokens are re-indexed recursively
      │
      ▼
  Enrichment         Add node IDs, LLM summaries, doc description (optional)
      │
      ▼
  JSON Output        results/<filename>_structure.json
```

---

## Logging

Every run writes a detailed JSON log to `./logs/<filename>_<timestamp>.json`, capturing:
- Total page count and token count
- TOC detection result
- Verification accuracy and incorrect items at each stage
- Fix attempts and outcomes

---

## Acknowledgements

This project is a Java port of **[PageIndex](https://github.com/VectifyAI/PageIndex)** by [VectifyAI](https://vectify.ai).
All credit for the original framework, algorithms, and prompts goes to the PageIndex team.

Original citation:
```
Mingtian Zhang, Yu Tang and PageIndex Team,
"PageIndex: Next-Generation Vectorless, Reasoning-based RAG",
PageIndex Blog, Sep 2025.
```

---

© 2025 — Java port maintained separately from the original Python project.
#   P a g e I n d e x J  
 