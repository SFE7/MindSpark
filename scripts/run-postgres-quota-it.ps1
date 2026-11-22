<#
.SYNOPSIS
    Runs the PostgreSQL concurrency integration test against a throwaway database.

.DESCRIPTION
    Creates a temporary database 'ai_quota_test' inside the existing ai-db container,
    executes PostgresQuotaConcurrencyIT with -Drun.postgres.it=true, and ensures the
    throwaway database is dropped in a finally block. Never touches the production ai_db.

.EXAMPLE
    .\scripts\run-postgres-quota-it.ps1
#>

[CmdletBinding()]
param()

$repoRoot = Resolve-Path "$PSScriptRoot\.."
$aiServiceDir = Join-Path $repoRoot "ai-service"

# Load database password from .env in memory without echoing
if (-not $env:AI_DB_PASSWORD -and (Test-Path "$repoRoot\.env")) {
    $match = (Get-Content "$repoRoot\.env" | Select-String "^AI_DB_PASSWORD=(.*)$")
    if ($match) {
        $env:AI_DB_PASSWORD = $match.Matches.Groups[1].Value.Trim()
    }
}

function Drop-TestDbIfPresent {
    $exists = docker compose -f "$repoRoot\docker-compose.yml" exec -T ai-db psql -t -A -U ai_user -d postgres -c "SELECT 1 FROM pg_database WHERE datname = 'ai_quota_test';" 2>$null
    if ($exists -match "1") {
        docker compose -f "$repoRoot\docker-compose.yml" exec -T ai-db psql -U ai_user -d postgres -c "DROP DATABASE ai_quota_test;" | Out-Null
    }
}

Write-Host "=== PostgreSQL Quota Concurrency IT Runner ===" -ForegroundColor Cyan
Write-Host "Ensuring throwaway database 'ai_quota_test' is clean..." -ForegroundColor Gray

# Step 1: Create throwaway database
try {
    Drop-TestDbIfPresent
    docker compose -f "$repoRoot\docker-compose.yml" exec -T ai-db psql -U ai_user -d postgres -c "CREATE DATABASE ai_quota_test OWNER ai_user;" | Out-Null
    Write-Host "[OK] Created temporary database 'ai_quota_test' in ai-db." -ForegroundColor Green
} catch {
    Write-Error "Failed to create temporary database in ai-db container: $_"
    exit 1
}

# Step 2: Run test with cleanup in finally
try {
    Write-Host "Running PostgresQuotaConcurrencyIT against ai_quota_test on port 5434..." -ForegroundColor Yellow
    Push-Location $aiServiceDir
    & .\mvnw.cmd test "-Dtest=PostgresQuotaConcurrencyIT" "-Drun.postgres.it=true"
    if ($LASTEXITCODE -ne 0) {
        throw "Maven test failed with exit code $LASTEXITCODE"
    }
    Write-Host "[SUCCESS] PostgresQuotaConcurrencyIT passed cleanly." -ForegroundColor Green
} finally {
    Pop-Location
    Write-Host "Dropping temporary database 'ai_quota_test'..." -ForegroundColor Gray
    Drop-TestDbIfPresent
    Write-Host "[OK] Cleaned up 'ai_quota_test'. Database 'ai_db' remains untouched." -ForegroundColor Green
}
