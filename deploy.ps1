# VectorLess RAG — Google Cloud Run Deploy Script
# Run from project root in PowerShell:
#   .\deploy.ps1
# Or with a specific step:
#   .\deploy.ps1 -Step login
#   .\deploy.ps1 -Step setup
#   .\deploy.ps1 -Step build
#   .\deploy.ps1 -Step deploy
#   .\deploy.ps1 -Step all

param(
    [string]$Step = "all",
    [string]$ProjectId = "vectorless-rag",
    [string]$Region = "us-central1",
    [string]$ServiceName = "pageindexj"
)

$GCLOUD = "C:\Users\Gunjan\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
$IMAGE  = "gcr.io/$ProjectId/$ServiceName"

function Run-Gcloud {
    param([string[]]$Args)
    & $GCLOUD @Args
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: gcloud command failed (exit $LASTEXITCODE)" -ForegroundColor Red
        exit $LASTEXITCODE
    }
}

Write-Host "`n=== VectorLess RAG — Cloud Run Deployment ===" -ForegroundColor Cyan

switch ($Step.ToLower()) {

    "login" {
        Write-Host "`n[1/1] Authenticating with Google..." -ForegroundColor Yellow
        Run-Gcloud "auth", "login"
        Write-Host "Done. Run '.\deploy.ps1 -Step setup' next." -ForegroundColor Green
    }

    "setup" {
        Write-Host "`n[1/3] Creating project '$ProjectId'..." -ForegroundColor Yellow
        & $GCLOUD projects create $ProjectId --name="VectorLess RAG" 2>&1 | Out-Null
        Write-Host "  (project may already exist — that's OK)"

        Write-Host "[2/3] Setting active project..." -ForegroundColor Yellow
        Run-Gcloud "config", "set", "project", $ProjectId

        Write-Host "[3/3] Enabling APIs (run, cloudbuild, artifactregistry)..." -ForegroundColor Yellow
        Run-Gcloud "services", "enable", "run.googleapis.com", "cloudbuild.googleapis.com", "artifactregistry.googleapis.com"

        Write-Host "`nSetup complete. Run '.\deploy.ps1 -Step build' next." -ForegroundColor Green
    }

    "build" {
        Write-Host "`n[1/1] Building Docker image via Cloud Build..." -ForegroundColor Yellow
        Write-Host "  Image: $IMAGE"
        Run-Gcloud "builds", "submit", "--tag", $IMAGE, "."
        Write-Host "`nBuild complete. Run '.\deploy.ps1 -Step deploy' next." -ForegroundColor Green
    }

    "deploy" {
        # Load API keys from .env if it exists
        $EnvFile = Join-Path $PSScriptRoot ".env"
        $EnvVars = @{}
        if (Test-Path $EnvFile) {
            Get-Content $EnvFile | ForEach-Object {
                if ($_ -match "^([^#=]+)=(.+)$") {
                    $EnvVars[$Matches[1].Trim()] = $Matches[2].Trim()
                }
            }
            Write-Host "  Loaded API keys from .env" -ForegroundColor Gray
        } else {
            Write-Host "  WARNING: .env not found — API keys will not be set" -ForegroundColor Yellow
        }

        $EnvString = ($EnvVars.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join ","

        Write-Host "`n[1/1] Deploying to Cloud Run ($Region)..." -ForegroundColor Yellow
        $DeployArgs = @(
            "run", "deploy", $ServiceName,
            "--image", $IMAGE,
            "--platform", "managed",
            "--region", $Region,
            "--allow-unauthenticated",
            "--memory", "1Gi",
            "--cpu", "1",
            "--timeout", "300"
        )
        if ($EnvString) {
            $DeployArgs += "--set-env-vars"
            $DeployArgs += $EnvString
        }
        Run-Gcloud @DeployArgs

        Write-Host "`nDeployment complete!" -ForegroundColor Green
        Write-Host "Getting service URL..." -ForegroundColor Yellow
        & $GCLOUD run services describe $ServiceName --region $Region --format="value(status.url)"
    }

    "all" {
        Write-Host "`nRunning full deployment pipeline..." -ForegroundColor Cyan
        & $PSCommandPath -Step login    -ProjectId $ProjectId -Region $Region -ServiceName $ServiceName
        & $PSCommandPath -Step setup    -ProjectId $ProjectId -Region $Region -ServiceName $ServiceName
        & $PSCommandPath -Step build    -ProjectId $ProjectId -Region $Region -ServiceName $ServiceName
        & $PSCommandPath -Step deploy   -ProjectId $ProjectId -Region $Region -ServiceName $ServiceName
    }

    default {
        Write-Host "Unknown step: $Step" -ForegroundColor Red
        Write-Host "Valid steps: login, setup, build, deploy, all"
        exit 1
    }
}
