<#
.SYNOPSIS
    Runs the PostgreSQL subscription concurrency integration test against a throwaway database.

.DESCRIPTION
    Creates a temporary database 'sub_it_temp' inside the existing subscription-db container,
    executes PostgresSubscriptionConcurrencyIT with -Drun.postgres.sub.it=true, and drops the
    throwaway database in a finally block.
#>

[CmdletBinding()]
param()

$repoRoot = Resolve-Path "$PSScriptRoot\.."
$subServiceDir = Join-Path $repoRoot "subscription-service"

function Drop-TestDbIfPresent {
    $exists = docker compose -f "$repoRoot\docker-compose.yml" exec -T subscription-db psql -t -A -U subscription_user -d postgres -c "SELECT 1 FROM pg_database WHERE datname = 'sub_it_temp';" 2>$null
    if ($exists -match "1") {
        docker compose -f "$repoRoot\docker-compose.yml" exec -T subscription-db psql -U subscription_user -d postgres -c "DROP DATABASE sub_it_temp;" | Out-Null
    }
}

Write-Host "=== PostgreSQL Subscription Concurrency IT Runner ===" -ForegroundColor Cyan
Write-Host "Ensuring throwaway database 'sub_it_temp' is clean..." -ForegroundColor Gray

try {
    Drop-TestDbIfPresent
    docker compose -f "$repoRoot\docker-compose.yml" exec -T subscription-db psql -U subscription_user -d postgres -c "CREATE DATABASE sub_it_temp OWNER subscription_user;" | Out-Null
    Write-Host "[OK] Created temporary database 'sub_it_temp' in subscription-db." -ForegroundColor Green
} catch {
    Write-Error "Failed to create temporary database in subscription-db container: $_"
    exit 1
}

try {
    Write-Host "Running PostgresSubscriptionConcurrencyIT against sub_it_temp on port 5435..." -ForegroundColor Yellow
    Push-Location $subServiceDir
    & .\mvnw.cmd test "-Dtest=PostgresSubscriptionConcurrencyIT" "-Drun.postgres.sub.it=true"
    if ($LASTEXITCODE -ne 0) {
        throw "Maven test failed with exit code $LASTEXITCODE"
    }
    Write-Host "[SUCCESS] PostgresSubscriptionConcurrencyIT passed cleanly." -ForegroundColor Green
} finally {
    Pop-Location
    Write-Host "Dropping temporary database 'sub_it_temp'..." -ForegroundColor Gray
    Drop-TestDbIfPresent
    Write-Host "[OK] Cleaned up 'sub_it_temp'." -ForegroundColor Green
}
