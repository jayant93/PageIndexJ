# Deploying VectorLess RAG to Google Cloud Run

Google Cloud Run offers a **generous always-free tier** — no credit card charges for typical demo/low-traffic usage.

---

## Free Tier Limits (always free, no expiry)

| Resource | Free per month |
|---|---|
| Requests | 2,000,000 |
| CPU time | 180,000 vCPU-seconds |
| Memory | 360,000 GB-seconds |
| Egress (outbound) | 1 GB |

The app **scales to zero** when idle — you only pay when someone actually uses it.

---

## Prerequisites

### 1. Google Cloud Account
Sign up at https://cloud.google.com — a free account with no billing required for Cloud Run free tier.

### 2. Google Cloud SDK

**Already installed** at:
```
C:\Users\Gunjan\AppData\Local\Google\Cloud SDK\google-cloud-sdk\
```
Version: **559.0.0**

Run via PowerShell (use this for all commands below):
```powershell
gcloud.cmd --version
```

> In Git Bash use the `.cmd` wrapper: `gcloud.cmd` instead of `gcloud`.
> In PowerShell or Command Prompt: `gcloud` works directly after adding the bin folder to PATH.

---

## One-Time Setup

### Step 1 — Authenticate
```bash
gcloud auth login
```
This opens a browser. Sign in with your Google account.

### Step 2 — Create a project
```bash
gcloud projects create vectorless-rag --name="VectorLess RAG"
gcloud config set project vectorless-rag
```

> If `vectorless-rag` is taken, choose any unique ID (e.g. `vectorless-rag-2024`).

### Step 3 — Enable required APIs
```bash
gcloud services enable \
  run.googleapis.com \
  artifactregistry.googleapis.com \
  cloudbuild.googleapis.com
```
This takes about 1–2 minutes.

### Step 4 — Link a billing account (required to enable APIs, but free tier means no charges)
Go to https://console.cloud.google.com/billing and link a billing account to your project.
Cloud Run free tier charges are $0 for normal usage — linking billing just unlocks the APIs.

---

## Build & Deploy

Run these commands from the project root directory:
```
c:\Users\Gunjan\Documents\Business_Apps\Open_Source_Project\PageIndexJ\PageIndexJ\
```

### Step 5 — Build the Docker image using Cloud Build (no local Docker needed)
```bash
gcloud builds submit --tag gcr.io/vectorless-rag/pageindexj .
```

This uploads your source code to Google, builds the Docker image in the cloud, and stores it in Container Registry. Takes ~3–5 minutes on first build.

### Step 6 — Deploy to Cloud Run
```bash
gcloud run deploy pageindexj \
  --image gcr.io/vectorless-rag/pageindexj \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --memory 1Gi \
  --cpu 1 \
  --timeout 300 \
  --set-env-vars "GROQ_API_KEY=YOUR_GROQ_KEY,CEREBRAS_API_KEY=YOUR_CEREBRAS_KEY,MISTRAL_API_KEY=YOUR_MISTRAL_KEY,GOOGLE_API_KEY=YOUR_GOOGLE_KEY"
```

Replace the `YOUR_*_KEY` placeholders with your actual API keys from `.env`.

After ~1 minute you will see:
```
Service [pageindexj] revision [pageindexj-00001-xxx] has been deployed and is serving 100 percent of traffic.
Service URL: https://pageindexj-xxxxxxxxxxxx-uc.a.run.app
```

That URL is your live public app. Share it with anyone.

---

## Updating the Deployment

After any code change, rebuild and redeploy:
```bash
# From the project root:
gcloud builds submit --tag gcr.io/vectorless-rag/pageindexj .
gcloud run deploy pageindexj --image gcr.io/vectorless-rag/pageindexj --region us-central1
```

---

## Managing API Keys Securely

Instead of passing keys on the command line, use **Secret Manager** (recommended for production):

```bash
# Store each key as a secret
echo -n "YOUR_GROQ_KEY" | gcloud secrets create GROQ_API_KEY --data-file=-
echo -n "YOUR_CEREBRAS_KEY" | gcloud secrets create CEREBRAS_API_KEY --data-file=-
echo -n "YOUR_MISTRAL_KEY" | gcloud secrets create MISTRAL_API_KEY --data-file=-
echo -n "YOUR_GOOGLE_KEY" | gcloud secrets create GOOGLE_API_KEY --data-file=-

# Deploy referencing secrets instead of plain env vars
gcloud run deploy pageindexj \
  --image gcr.io/vectorless-rag/pageindexj \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --memory 1Gi \
  --update-secrets "GROQ_API_KEY=GROQ_API_KEY:latest,CEREBRAS_API_KEY=CEREBRAS_API_KEY:latest,MISTRAL_API_KEY=MISTRAL_API_KEY:latest,GOOGLE_API_KEY=GOOGLE_API_KEY:latest"
```

---

## Useful Commands

| Task | Command |
|---|---|
| View live logs | `gcloud run logs tail pageindexj --region us-central1` |
| List revisions | `gcloud run revisions list --service pageindexj --region us-central1` |
| Get service URL | `gcloud run services describe pageindexj --region us-central1 --format='value(status.url)'` |
| Delete service | `gcloud run services delete pageindexj --region us-central1` |
| View all costs | https://console.cloud.google.com/billing |

---

## Important Notes

### Ephemeral Storage
Cloud Run containers are stateless. The `results/` directory (indexed documents) is reset every time a new container instance starts. For a persistent demo this is fine — for production you would store results in **Cloud Storage** or a database.

### Cold Starts
When the service is idle it scales to zero. The first request after idle takes ~5–10 seconds to spin up (cold start). Subsequent requests are fast.

### File Upload Size
The `application.properties` already sets `max-file-size=200MB`. Cloud Run's default request timeout is 300 seconds — this is set in the deploy command above (`--timeout 300`) to handle large PDF indexing jobs.

### Region
`us-central1` has the highest free tier allocation. You can use `europe-west1` or `asia-east1` if preferred — free tier applies to all regions.

---

## Architecture on Cloud Run

```
User Browser
     │
     ▼
Cloud Run Container (Spring Boot :8080)
     │
     ├── POST /api/index  ──► Free Model Pool
     │                           ├── Groq API
     │                           ├── Cerebras API
     │                           ├── Mistral API
     │                           └── Gemini API
     │
     ├── GET  /api/events/{id}  ──► SSE live log stream
     │
     └── POST /api/query  ──► Free Model Pool (same rotation)
```

---

## TODO (Planned)

- [ ] **Admin page** — UI where users can provide their own AI model base URL (OpenAI-compatible endpoint) and API key, stored server-side and added to the pool as an additional provider
- [ ] **Persistent storage** — Move `results/` to Cloud Storage so indexed documents survive container restarts
- [ ] **Paid tier** — GPT-4o integration with user-provided OpenAI key, unlimited pages
