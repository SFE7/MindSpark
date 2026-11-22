<#
.SYNOPSIS
    End-to-end verification script for MindSpark AI Service.
    Tests Keycloak JWT authentication, tier resolution, quota management,
    security hardening, and all 7 AI feature endpoints across BOTH tiers (FREE & PREMIUM).

.DESCRIPTION
    Requires Keycloak users (configured via TEST_FREE_USERNAME and TEST_PREMIUM_USERNAME)
    and a temporary direct-access-grant client (see scripts/README-test-ai.md).
    Never prints secrets or tokens.

.PARAMETER LimitTest
    Optional switch. Exhausts the test-free daily quota to verify DAILY_LIMIT_REACHED (429).
#>

[CmdletBinding()]
param(
    [switch]$LimitTest
)

$ErrorActionPreference = "Continue"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " MindSpark AI Service - End-to-End Test Suite" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "WARNING: A full execution of this test suite will consume 7 FREE daily quota units and 7 PREMIUM daily quota units." -ForegroundColor Yellow

# Read configuration from environment or .env via $PSScriptRoot (NEVER prints secrets)
$repoRoot = Resolve-Path "$PSScriptRoot\.."
$envFile = Join-Path $repoRoot ".env"

$HostIp = if ($env:HOST_IP) { $env:HOST_IP } else { "localhost" }
$ClientId = if ($env:KC_TEST_CLIENT_ID) { $env:KC_TEST_CLIENT_ID } else { "" }
$FreePassword = if ($env:TEST_FREE_PASSWORD) { $env:TEST_FREE_PASSWORD } else { "" }
$PremiumPassword = if ($env:TEST_PREMIUM_PASSWORD) { $env:TEST_PREMIUM_PASSWORD } else { "" }
$FreeUsername = if ($env:TEST_FREE_USERNAME) { $env:TEST_FREE_USERNAME } else { "test-free@gmail.com" }
$PremiumUsername = if ($env:TEST_PREMIUM_USERNAME) { $env:TEST_PREMIUM_USERNAME } else { "test-premium@gmail.com" }

if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -notmatch "^#" -and $line.Contains("=")) {
            $parts = $line.Split("=", 2)
            $k = $parts[0].Trim()
            $v = $parts[1].Trim().Trim('"').Trim("'")
            if ($k -eq "HOST_IP" -and $HostIp -eq "localhost") { $HostIp = $v }
            if ($k -eq "KC_TEST_CLIENT_ID" -and -not $ClientId) { $ClientId = $v }
            if ($k -eq "TEST_FREE_PASSWORD" -and -not $FreePassword) { $FreePassword = $v }
            if ($k -eq "TEST_PREMIUM_PASSWORD" -and -not $PremiumPassword) { $PremiumPassword = $v }
            if ($k -eq "TEST_FREE_USERNAME" -and -not $env:TEST_FREE_USERNAME) { $FreeUsername = $v }
            if ($k -eq "TEST_PREMIUM_USERNAME" -and -not $env:TEST_PREMIUM_USERNAME) { $PremiumUsername = $v }
        }
    }
}

if (-not $ClientId) {
    Write-Error "KC_TEST_CLIENT_ID not found in environment or .env. Please check scripts/README-test-ai.md."
    exit 1
}
if (-not $FreePassword -or -not $PremiumPassword) {
    Write-Error "TEST_FREE_PASSWORD or TEST_PREMIUM_PASSWORD not set in environment or .env. Please check scripts/README-test-ai.md."
    exit 1
}

$GatewayUrl = "http://${HostIp}:8080"
$TokenUrl = "http://${HostIp}:8180/realms/mindspark/protocol/openid-connect/token"
$PremiumModelsList = @("openai/gpt-oss-120b", "qwen/qwen3.8-27b", "openai/gpt-oss-20b", "allam-2-7b")

$Results = [System.Collections.Generic.List[PSCustomObject]]::new()
$TotalPassed = 0
$TotalFailed = 0

function Record-Result {
    param([string]$TestName, [string]$Status, [string]$Details, [int]$LatencyMs = 0)
    $latencyStr = if ($LatencyMs -gt 0) { " (${LatencyMs} ms)" } else { "" }
    $script:Results.Add([PSCustomObject]@{
        Test = $TestName
        Status = $Status
        Details = "$Details$latencyStr"
    })
    if ($Status -eq "PASS") {
        $script:TotalPassed++
        Write-Host "  [PASS] $TestName - $Details$latencyStr" -ForegroundColor Green
    } else {
        $script:TotalFailed++
        Write-Host "  [FAIL] $TestName - $Details$latencyStr" -ForegroundColor Red
    }
}

function Get-Token {
    param([string]$Username, [string]$Password)
    try {
        $body = @{
            grant_type = "password"
            client_id  = $ClientId
            username   = $Username
            password   = $Password
        }
        $resp = Invoke-RestMethod -Uri $TokenUrl -Method Post -Body $body -ContentType "application/x-www-form-urlencoded" -UseBasicParsing -ErrorAction Stop
        return $resp.access_token
    } catch {
        Write-Error "Failed to obtain token for ${Username}: $($_.Exception.Message)"
        return $null
    }
}

function Call-Api {
    param(
        [string]$Path,
        [string]$Method = "POST",
        [string]$Token = $null,
        [object]$Body = $null
    )
    $headers = @{}
    if ($Token) {
        $headers["Authorization"] = "Bearer $Token"
    }

    $bytes = $null
    if ($Body -ne $null) {
        $jsonStr = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 10 }
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($jsonStr)
    }

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $params = @{
            Uri = "${GatewayUrl}${Path}"
            Method = $Method
            Headers = $headers
            UseBasicParsing = $true
            ErrorAction = "Stop"
        }
        if ($bytes -ne $null) {
            $params["Body"] = $bytes
            $params["ContentType"] = "application/json; charset=utf-8"
        }
        $response = Invoke-WebRequest @params
        $sw.Stop()

        # PS 5.1 & 7 safe UTF-8 decode from RawContentStream to prevent mojibake
        $rawString = $null
        if ($response.RawContentStream) {
            $stream = $response.RawContentStream
            if ($stream.CanSeek) {
                $stream.Position = 0
            }
            $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8)
            $rawString = $reader.ReadToEnd()
        }
        if (-not $rawString) {
            $rawString = $response.Content
        }

        $content = $null
        if ($rawString) {
            try { $content = $rawString | ConvertFrom-Json } catch {}
        }

        return @{
            StatusCode = [int]$response.StatusCode
            Content = $content
            Raw = $rawString
            LatencyMs = [int]$sw.ElapsedMilliseconds
        }
    } catch {
        $sw.Stop()
        $statusCode = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        $errorContent = $null
        $raw = $null
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
            $raw = $_.ErrorDetails.Message
        } elseif ($_.Exception.Response -and $_.Exception.Response.GetResponseStream) {
            try {
                $stream = $_.Exception.Response.GetResponseStream()
                if ($stream.CanSeek) { $stream.Position = 0 }
                $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8)
                $raw = $reader.ReadToEnd()
            } catch {}
        }
        if ($raw) {
            try { $errorContent = $raw | ConvertFrom-Json } catch {}
        }

        return @{
            StatusCode = $statusCode
            Content = $errorContent
            Raw = $raw
            ErrorMessage = $_.Exception.Message
            LatencyMs = [int]$sw.ElapsedMilliseconds
        }
    }
}

# 1. Obtain Tokens
Write-Host "`n[1] Authenticating test users..." -ForegroundColor Yellow
$FreeToken = Get-Token $FreeUsername $FreePassword
$PremiumToken = Get-Token $PremiumUsername $PremiumPassword

if (-not $FreeToken) {
    Record-Result "Auth: $FreeUsername token" "FAIL" "Could not obtain token"
} else {
    Record-Result "Auth: $FreeUsername token" "PASS" "Token acquired"
}

if (-not $PremiumToken) {
    Record-Result "Auth: $PremiumUsername token" "FAIL" "Could not obtain token"
} else {
    Record-Result "Auth: $PremiumUsername token" "PASS" "Token acquired"
}

if (-not $FreeToken -or -not $PremiumToken) {
    Write-Host "`nAuthentication failed. Aborting remaining tests." -ForegroundColor Red
    exit 1
}

# 2. Check /api/ai/me tier resolution
Write-Host "`n[2] Verifying tier resolution (/api/ai/me)..." -ForegroundColor Yellow
$meFree = Call-Api -Path "/api/ai/me" -Method "GET" -Token $FreeToken
if ($meFree.StatusCode -eq 200 -and $meFree.Content.tier -eq "FREE") {
    Record-Result "Tier: $FreeUsername -> FREE" "PASS" "User: $($meFree.Content.userId), Tier: $($meFree.Content.tier)" -LatencyMs $meFree.LatencyMs
} else {
    Record-Result "Tier: $FreeUsername -> FREE" "FAIL" "HTTP $($meFree.StatusCode), Tier: $($meFree.Content.tier)" -LatencyMs $meFree.LatencyMs
}

$mePrem = Call-Api -Path "/api/ai/me" -Method "GET" -Token $PremiumToken
if ($mePrem.StatusCode -eq 200 -and $mePrem.Content.tier -eq "PREMIUM") {
    Record-Result "Tier: $PremiumUsername -> PREMIUM" "PASS" "User: $($mePrem.Content.userId), Tier: $($mePrem.Content.tier)" -LatencyMs $mePrem.LatencyMs
} else {
    Record-Result "Tier: $PremiumUsername -> PREMIUM" "FAIL" "HTTP $($mePrem.StatusCode), Tier: $($mePrem.Content.tier)" -LatencyMs $mePrem.LatencyMs
}

# 3. Baseline Quota Check (/api/ai/usage)
Write-Host "`n[3] Recording baseline quota (/api/ai/usage)..." -ForegroundColor Yellow
$usageFreeBefore = Call-Api -Path "/api/ai/usage" -Method "GET" -Token $FreeToken
$usagePremBefore = Call-Api -Path "/api/ai/usage" -Method "GET" -Token $PremiumToken

Record-Result "Usage Baseline: $FreeUsername" "PASS" "Used: $($usageFreeBefore.Content.usedToday)/$($usageFreeBefore.Content.dailyLimit)"
Record-Result "Usage Baseline: $PremiumUsername" "PASS" "Used: $($usagePremBefore.Content.usedToday)/$($usagePremBefore.Content.dailyLimit)"

# 4. Security & Prompt Injection Checks
Write-Host "`n[4] Security, Injection & Bounds Validation..." -ForegroundColor Yellow

$noAuth = Call-Api -Path "/api/ai/ideas/generate" -Method "POST" -Body @{ topic = "Test" }
if ($noAuth.StatusCode -eq 401) {
    Record-Result "Security: No token returns 401" "PASS" "HTTP 401 Unauthorized" -LatencyMs $noAuth.LatencyMs
} else {
    Record-Result "Security: No token returns 401" "FAIL" "HTTP $($noAuth.StatusCode)" -LatencyMs $noAuth.LatencyMs
}

$oversized = Call-Api -Path "/api/ai/ideas/generate" -Method "POST" -Token $FreeToken -Body @{ topic = ("A" * 6050) }
if ($oversized.StatusCode -eq 400 -and $oversized.Content.error -eq "VALIDATION_ERROR") {
    Record-Result "Validation: Oversized payload returns 400" "PASS" "HTTP 400 VALIDATION_ERROR" -LatencyMs $oversized.LatencyMs
} else {
    Record-Result "Validation: Oversized payload returns 400" "FAIL" "HTTP $($oversized.StatusCode)" -LatencyMs $oversized.LatencyMs
}

# Check (h): Body contains "tier":"PREMIUM" sent with FreeToken -> assert tier remains FREE
$bodyTierSpoof = @{
    topic = "Educational coding game"
    count = 2
    tier = "PREMIUM"
}
$spoofCall = Call-Api -Path "/api/ai/ideas/generate" -Token $FreeToken -Body $bodyTierSpoof
if ($spoofCall.StatusCode -eq 200 -and $spoofCall.Content.meta.tier -eq "FREE" -and $spoofCall.Content.meta.model.StartsWith("gemini")) {
    Record-Result "Security: Body tier injection ignored" "PASS" "Tier remains FREE, model: $($spoofCall.Content.meta.model)" -LatencyMs $spoofCall.LatencyMs
} else {
    Record-Result "Security: Body tier injection ignored" "FAIL" "Tier was: $($spoofCall.Content.meta.tier)" -LatencyMs $spoofCall.LatencyMs
}

# 5. Call ALL 7 Feature Endpoints on BOTH Tiers
Write-Host "`n[5] Calling all 7 Feature Endpoints on BOTH Tiers..." -ForegroundColor Yellow

$freeSuccessCount = 0
$premSuccessCount = 0

function Assert-AiMeta {
    param($Call, [string]$ExpectedTier)
    if ($Call.StatusCode -ne 200) { return $false }
    $tierOk = ($Call.Content.meta.tier -eq $ExpectedTier)
    $modelOk = if ($ExpectedTier -eq "FREE") {
        $Call.Content.meta.model.StartsWith("gemini")
    } else {
        $PremiumModelsList -contains $Call.Content.meta.model
    }
    return ($tierOk -and $modelOk)
}

# Feature 1: Generate Ideas (French prompt)
$genBody = @{
    topic = "Plateforme d'apprentissage du code pour débutants"
    context = "Interface ludique et interactive pour enfants"
    count = 2
}

$genFree = Call-Api -Path "/api/ai/ideas/generate" -Token $FreeToken -Body $genBody
$isFrenchFree = $genFree.Raw -match "(?i)(apprentissage|pour|dans|enfants|code|les|des|une|plateforme|débutants)"
if ((Assert-AiMeta $genFree "FREE") -and $isFrenchFree) {
    $freeSuccessCount++
    $title = $genFree.Content.result.ideas[0].title
    Record-Result "Endpoint 1: Generate [FREE] (French)" "PASS" "Model: $($genFree.Content.meta.model) | French verified: True | Title: $title" -LatencyMs $genFree.LatencyMs
} else {
    Record-Result "Endpoint 1: Generate [FREE] (French)" "FAIL" "HTTP $($genFree.StatusCode) | French verified: $isFrenchFree | Model: $($genFree.Content.meta.model)" -LatencyMs $genFree.LatencyMs
}

$genPrem = Call-Api -Path "/api/ai/ideas/generate" -Token $PremiumToken -Body $genBody
$isFrenchPrem = $genPrem.Raw -match "(?i)(apprentissage|pour|dans|enfants|code|les|des|une|plateforme|débutants)"
if ((Assert-AiMeta $genPrem "PREMIUM") -and $isFrenchPrem) {
    $premSuccessCount++
    $title = $genPrem.Content.result.ideas[0].title
    Record-Result "Endpoint 1: Generate [PREMIUM] (French)" "PASS" "Model: $($genPrem.Content.meta.model) | French verified: True | Title: $title" -LatencyMs $genPrem.LatencyMs
} else {
    Record-Result "Endpoint 1: Generate [PREMIUM] (French)" "FAIL" "HTTP $($genPrem.StatusCode) | French verified: $isFrenchPrem | Model: $($genPrem.Content.meta.model)" -LatencyMs $genPrem.LatencyMs
}

# Feature 2: Improve Idea (Arabic prompt)
$impBody = @{
    idea = @{ id = "ar-1"; text = "تطبيق ذكي لإدارة المهام اليومية وجدولة المواعيد" }
    mode = "improve"
}

$impFree = Call-Api -Path "/api/ai/ideas/improve" -Token $FreeToken -Body $impBody
$hasArabicFree = $impFree.Raw -match "[\u0600-\u06FF]"
if ((Assert-AiMeta $impFree "FREE") -and $hasArabicFree) {
    $freeSuccessCount++
    Record-Result "Endpoint 2: Improve [FREE] (Arabic)" "PASS" "Model: $($impFree.Content.meta.model) | Arabic verified: True | Title: $($impFree.Content.result.title)" -LatencyMs $impFree.LatencyMs
} else {
    Record-Result "Endpoint 2: Improve [FREE] (Arabic)" "FAIL" "HTTP $($impFree.StatusCode) | Arabic verified: $hasArabicFree" -LatencyMs $impFree.LatencyMs
}

$impPrem = Call-Api -Path "/api/ai/ideas/improve" -Token $PremiumToken -Body $impBody
$hasArabicPrem = $impPrem.Raw -match "[\u0600-\u06FF]"
if ((Assert-AiMeta $impPrem "PREMIUM") -and $hasArabicPrem) {
    $premSuccessCount++
    Record-Result "Endpoint 2: Improve [PREMIUM] (Arabic)" "PASS" "Model: $($impPrem.Content.meta.model) | Arabic verified: True | Title: $($impPrem.Content.result.title)" -LatencyMs $impPrem.LatencyMs
} else {
    Record-Result "Endpoint 2: Improve [PREMIUM] (Arabic)" "FAIL" "HTTP $($impPrem.StatusCode) | Arabic verified: $hasArabicPrem" -LatencyMs $impPrem.LatencyMs
}

# Feature 3: Group Ideas (English prompt)
$grpBody = @{
    ideas = @(
        @{ id = "g1"; text = "Redesign UI with dark mode" },
        @{ id = "g2"; text = "Build PostgreSQL database schema" },
        @{ id = "g3"; text = "Add automated unit tests in CI" }
    )
}

$grpFree = Call-Api -Path "/api/ai/ideas/group" -Token $FreeToken -Body $grpBody
if (Assert-AiMeta $grpFree "FREE") {
    $freeSuccessCount++
    Record-Result "Endpoint 3: Group [FREE]" "PASS" "Model: $($grpFree.Content.meta.model) | Groups: $($grpFree.Content.result.groups.Count)" -LatencyMs $grpFree.LatencyMs
} else {
    Record-Result "Endpoint 3: Group [FREE]" "FAIL" "HTTP $($grpFree.StatusCode)" -LatencyMs $grpFree.LatencyMs
}

$grpPrem = Call-Api -Path "/api/ai/ideas/group" -Token $PremiumToken -Body $grpBody
if (Assert-AiMeta $grpPrem "PREMIUM") {
    $premSuccessCount++
    Record-Result "Endpoint 3: Group [PREMIUM]" "PASS" "Model: $($grpPrem.Content.meta.model) | Groups: $($grpPrem.Content.result.groups.Count)" -LatencyMs $grpPrem.LatencyMs
} else {
    Record-Result "Endpoint 3: Group [PREMIUM]" "FAIL" "HTTP $($grpPrem.StatusCode)" -LatencyMs $grpPrem.LatencyMs
}

# Feature 4: Summarize Session (English prompt)
$sumBody = @{
    sessionTitle = "MindSpark Sprint Planning"
    ideas = @(
        @{ id = "s1"; text = "Implement AI microservice"; votes = 10; category = "AI" },
        @{ id = "s2"; text = "Setup API gateway routing"; votes = 8; category = "Infra" }
    )
}

$sumFree = Call-Api -Path "/api/ai/sessions/summarize" -Token $FreeToken -Body $sumBody
if (Assert-AiMeta $sumFree "FREE") {
    $freeSuccessCount++
    Record-Result "Endpoint 4: Summarize [FREE]" "PASS" "Model: $($sumFree.Content.meta.model)" -LatencyMs $sumFree.LatencyMs
} else {
    Record-Result "Endpoint 4: Summarize [FREE]" "FAIL" "HTTP $($sumFree.StatusCode)" -LatencyMs $sumFree.LatencyMs
}

$sumPrem = Call-Api -Path "/api/ai/sessions/summarize" -Token $PremiumToken -Body $sumBody
if (Assert-AiMeta $sumPrem "PREMIUM") {
    $premSuccessCount++
    Record-Result "Endpoint 4: Summarize [PREMIUM]" "PASS" "Model: $($sumPrem.Content.meta.model)" -LatencyMs $sumPrem.LatencyMs
} else {
    Record-Result "Endpoint 4: Summarize [PREMIUM]" "FAIL" "HTTP $($sumPrem.StatusCode)" -LatencyMs $sumPrem.LatencyMs
}

# Feature 5: Action Plan (Arabic prompt)
$actBody = @{
    goal = "إطلاق منصة التعليم التفاعلي في السوق"
    ideas = @(
        @{ id = "ar-1"; text = "تطوير لوحة تحكم المعلم والطلاب" },
        @{ id = "ar-2"; text = "إضافة روبوت الدردشة التوليدي" }
    )
}

$actFree = Call-Api -Path "/api/ai/ideas/action-plan" -Token $FreeToken -Body $actBody
$hasArabicActFree = $actFree.Raw -match "[\u0600-\u06FF]"
if ((Assert-AiMeta $actFree "FREE") -and $hasArabicActFree) {
    $freeSuccessCount++
    Record-Result "Endpoint 5: Action Plan [FREE] (Arabic)" "PASS" "Model: $($actFree.Content.meta.model) | Arabic verified: True | Title: $($actFree.Content.result.title)" -LatencyMs $actFree.LatencyMs
} else {
    Record-Result "Endpoint 5: Action Plan [FREE] (Arabic)" "FAIL" "HTTP $($actFree.StatusCode) | Arabic verified: $hasArabicActFree" -LatencyMs $actFree.LatencyMs
}

$actPrem = Call-Api -Path "/api/ai/ideas/action-plan" -Token $PremiumToken -Body $actBody
$hasArabicActPrem = $actPrem.Raw -match "[\u0600-\u06FF]"
if ((Assert-AiMeta $actPrem "PREMIUM") -and $hasArabicActPrem) {
    $premSuccessCount++
    Record-Result "Endpoint 5: Action Plan [PREMIUM] (Arabic)" "PASS" "Model: $($actPrem.Content.meta.model) | Arabic verified: True | Title: $($actPrem.Content.result.title)" -LatencyMs $actPrem.LatencyMs
} else {
    Record-Result "Endpoint 5: Action Plan [PREMIUM] (Arabic)" "FAIL" "HTTP $($actPrem.StatusCode) | Arabic verified: $hasArabicActPrem" -LatencyMs $actPrem.LatencyMs
}

# Feature 6: Risks (English prompt)
$rskBody = @{
    idea = @{ id = "r1"; text = "Store encrypted user tokens in browser localStorage" }
    context = "Enterprise web application security"
}

$rskFree = Call-Api -Path "/api/ai/ideas/risks" -Token $FreeToken -Body $rskBody
if (Assert-AiMeta $rskFree "FREE") {
    $freeSuccessCount++
    Record-Result "Endpoint 6: Risks [FREE]" "PASS" "Model: $($rskFree.Content.meta.model) | Risk: $($rskFree.Content.result.risks[0].title)" -LatencyMs $rskFree.LatencyMs
} else {
    Record-Result "Endpoint 6: Risks [FREE]" "FAIL" "HTTP $($rskFree.StatusCode)" -LatencyMs $rskFree.LatencyMs
}

$rskPrem = Call-Api -Path "/api/ai/ideas/risks" -Token $PremiumToken -Body $rskBody
if (Assert-AiMeta $rskPrem "PREMIUM") {
    $premSuccessCount++
    Record-Result "Endpoint 6: Risks [PREMIUM]" "PASS" "Model: $($rskPrem.Content.meta.model) | Risk: $($rskPrem.Content.result.risks[0].title)" -LatencyMs $rskPrem.LatencyMs
} else {
    Record-Result "Endpoint 6: Risks [PREMIUM]" "FAIL" "HTTP $($rskPrem.StatusCode)" -LatencyMs $rskPrem.LatencyMs
}

# Feature 7: Compare (English prompt)
$cmpBody = @{
    ideas = @(
        @{ id = "c1"; text = "Monolithic backend in Spring Boot" },
        @{ id = "c2"; text = "Serverless microservices on AWS Lambda" }
    )
}

$cmpFree = Call-Api -Path "/api/ai/ideas/compare" -Token $FreeToken -Body $cmpBody
if (Assert-AiMeta $cmpFree "FREE") {
    $freeSuccessCount++
    Record-Result "Endpoint 7: Compare [FREE]" "PASS" "Model: $($cmpFree.Content.meta.model) | Recommended: $($cmpFree.Content.result.recommendation.ideaId)" -LatencyMs $cmpFree.LatencyMs
} else {
    Record-Result "Endpoint 7: Compare [FREE]" "FAIL" "HTTP $($cmpFree.StatusCode)" -LatencyMs $cmpFree.LatencyMs
}

$cmpPrem = Call-Api -Path "/api/ai/ideas/compare" -Token $PremiumToken -Body $cmpBody
if (Assert-AiMeta $cmpPrem "PREMIUM") {
    $premSuccessCount++
    Record-Result "Endpoint 7: Compare [PREMIUM]" "PASS" "Model: $($cmpPrem.Content.meta.model) | Recommended: $($cmpPrem.Content.result.recommendation.ideaId)" -LatencyMs $cmpPrem.LatencyMs
} else {
    Record-Result "Endpoint 7: Compare [PREMIUM]" "FAIL" "HTTP $($cmpPrem.StatusCode)" -LatencyMs $cmpPrem.LatencyMs
}

# 6. Assert Quota Increment (Check f)
Write-Host "`n[6] Verifying quota increments..." -ForegroundColor Yellow
$usageFreeAfter = Call-Api -Path "/api/ai/usage" -Method "GET" -Token $FreeToken
$usagePremAfter = Call-Api -Path "/api/ai/usage" -Method "GET" -Token $PremiumToken

# Notice spoofCall also consumed 1 FREE unit if it succeeded with 200
$expectedFreeIncrement = $freeSuccessCount + 1
$actualFreeIncrement = [int]$usageFreeAfter.Content.usedToday - [int]$usageFreeBefore.Content.usedToday
$actualPremIncrement = [int]$usagePremAfter.Content.usedToday - [int]$usagePremBefore.Content.usedToday

if ($actualFreeIncrement -eq $expectedFreeIncrement) {
    Record-Result "Quota: $FreeUsername increment matches calls" "PASS" "Incremented by $actualFreeIncrement (expected $expectedFreeIncrement)"
} else {
    Record-Result "Quota: $FreeUsername increment matches calls" "FAIL" "Incremented by $actualFreeIncrement (expected $expectedFreeIncrement)"
}

if ($actualPremIncrement -eq $premSuccessCount) {
    Record-Result "Quota: $PremiumUsername increment matches calls" "PASS" "Incremented by $actualPremIncrement (expected $premSuccessCount)"
} else {
    Record-Result "Quota: $PremiumUsername increment matches calls" "FAIL" "Incremented by $actualPremIncrement (expected $premSuccessCount)"
}

# 7. Optional Limit Test
if ($LimitTest) {
    Write-Host "`n[7] Running -LimitTest on $FreeUsername (WARNING: exhausts daily quota)..." -ForegroundColor Magenta
    $limitHit = $false
    $count = 0
    while ($count -lt 15 -and -not $limitHit) {
        $count++
        $call = Call-Api -Path "/api/ai/ideas/generate" -Token $FreeToken -Body @{ topic = "Ping $count"; count = 1 }
        if ($call.StatusCode -eq 429 -and $call.Content.error -eq "DAILY_LIMIT_REACHED") {
            $limitHit = $true
            Record-Result "LimitTest: 429 DAILY_LIMIT_REACHED" "PASS" "Hit daily limit after $count requests" -LatencyMs $call.LatencyMs
        }
    }
    if (-not $limitHit) {
        Record-Result "LimitTest: 429 DAILY_LIMIT_REACHED" "FAIL" "Did not hit limit after $count requests"
    }
}

# 8. Summary Table
Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host " TEST EXECUTION SUMMARY" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
$Results | Format-Table -AutoSize

Write-Host "Total Passed: $TotalPassed | Total Failed: $TotalFailed" -ForegroundColor $(if ($TotalFailed -eq 0) { "Green" } else { "Red" })

if ($TotalFailed -gt 0) {
    exit 1
} else {
    exit 0
}