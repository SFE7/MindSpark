<#
Usage:
  .\scripts\set-host-ip.ps1 -NewIp 192.168.0.150
Updates HOST_IP in .env, syncs Keycloak frontend client origins, and restarts keycloak & backend.
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$NewIp
)

$ErrorActionPreference = "Stop"

if ($NewIp -notmatch '^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$') {
    Write-Error "Invalid IP address format: $NewIp"
    exit 1
}

$envFile = Join-Path $PSScriptRoot "..\.env"
if (-not (Test-Path $envFile)) {
    Write-Error ".env file not found at $envFile"
    exit 1
}

$envLines = Get-Content $envFile
$adminUser = "admin"
$adminPass = ""

foreach ($line in $envLines) {
    if ($line -match '^KEYCLOAK_ADMIN=(.*)$') {
        $adminUser = $matches[1].Trim()
    }
    if ($line -match '^KEYCLOAK_ADMIN_PASSWORD=(.*)$') {
        $adminPass = $matches[1].Trim()
    }
}

$newEnvLines = $envLines | ForEach-Object {
    if ($_ -match '^HOST_IP=') {
        "HOST_IP=$NewIp"
    } elseif ($_ -match '^KEYCLOAK_ISSUER_URI=') {
        "KEYCLOAK_ISSUER_URI=http://${NewIp}:8180/realms/mindspark"
    } elseif ($_ -match '^CORS_ALLOWED_ORIGINS=') {
        "CORS_ALLOWED_ORIGINS=http://localhost:4200,http://${NewIp}:4200"
    } else {
        $_
    }
}
$newEnvLines | Set-Content $envFile -Encoding utf8
Write-Host "Updated HOST_IP in .env to $NewIp"

try {
    & docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 --realm master --user $adminUser --password $adminPass 2>$null
    $cid = (& docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh get clients -r mindspark -q clientId=frontend --fields id --format csv --noquotes 2>$null).Trim()
    if ($cid) {
        $redirectUris = "redirectUris=[`"http://localhost:4200/*`",`"http://$NewIp:4200/*`"]"
        $webOrigins = "webOrigins=[`"http://localhost:4200`",`"http://$NewIp:4200`"]"
        & docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh update "clients/$cid" -r mindspark -s $redirectUris -s $webOrigins 2>$null
        Write-Host "Successfully updated Keycloak frontend client redirect URIs and web origins."
    }
} catch {
    Write-Warning "Keycloak was not running or client could not be updated online. Changes will apply on next restart."
}

Write-Host "Recreating keycloak and backend containers..."
& docker compose up -d --no-deps --force-recreate keycloak backend
Write-Host "Done! Services updated for IP $NewIp."
