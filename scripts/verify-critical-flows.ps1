<#
.SYNOPSIS
    Automated Critical-Flow End-to-End Verification Script (T6 + T7).
    Verifies service health, gateway security, live WebSocket upgrades over LAN origin,
    and all core user flows across the 6-microservice platform.

.DESCRIPTION
    Tests:
    1. Gateway & Microservice Health Endpoints through Gateway (port 8080)
    2. Gateway Security: /internal/** lockdown, CORS LAN origin reflection, header deduplication, nosniff
    3. Live WebSocket upgrade through Gateway with LAN Origin (ws://... with Origin: http://192.168.0.3:4200)
    4. Authenticated flows:
       - User sync -> Room create -> Join -> Idea create -> Idea move -> Vote
       - Subscription plans -> Subscription status (/me) -> Billing history
       - Session Snapshot create -> Snapshot get -> Export (.md, .json, .csv)
       - Media upload 1x1 PNG -> Fetch with JWT -> Reject fake file -> Canvas image attach -> Clean up
       - Vote pool create -> Cast vote -> Close pool -> Results tally
       - Room deletion / cleanup
    Never prints secrets or tokens.
#>

[CmdletBinding()]
param(
    [string]$HostIpOverride = ""
)

$ErrorActionPreference = "Continue"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " MindSpark Platform - Automated Critical Flow Tests (T6+T7)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$repoRoot = Resolve-Path "$PSScriptRoot\.."
$envFile = Join-Path $repoRoot ".env"

$HostIp = if ($HostIpOverride) { $HostIpOverride } elseif ($env:HOST_IP) { $env:HOST_IP } else { "localhost" }
$ClientId = if ($env:KC_TEST_CLIENT_ID) { $env:KC_TEST_CLIENT_ID } else { "ai-test-client" }
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
            if ($k -eq "HOST_IP" -and $HostIp -eq "localhost" -and -not $HostIpOverride) { $HostIp = $v }
            if ($k -eq "KC_TEST_CLIENT_ID" -and $v) { $ClientId = $v }
            if ($k -eq "TEST_FREE_PASSWORD" -and -not $FreePassword) { $FreePassword = $v }
            if ($k -eq "TEST_PREMIUM_PASSWORD" -and -not $PremiumPassword) { $PremiumPassword = $v }
            if ($k -eq "TEST_FREE_USERNAME" -and -not $env:TEST_FREE_USERNAME) { $FreeUsername = $v }
            if ($k -eq "TEST_PREMIUM_USERNAME" -and -not $env:TEST_PREMIUM_USERNAME) { $PremiumUsername = $v }
        }
    }
}

if (-not $FreePassword) {
    Write-Error "TEST_FREE_PASSWORD not set in environment or .env."
    exit 1
}

$GatewayUrl = "http://${HostIp}:8080"
$TokenUrl = "http://${HostIp}:8180/realms/mindspark/protocol/openid-connect/token"

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
    # First try configured ClientId, then fall back to admin-cli if needed
    $clientsToTry = @($ClientId, "admin-cli") | Select-Object -Unique
    foreach ($cid in $clientsToTry) {
        try {
            $body = @{
                grant_type = "password"
                client_id  = $cid
                username   = $Username
                password   = $Password
            }
            $resp = Invoke-RestMethod -Uri $TokenUrl -Method Post -Body $body -ContentType "application/x-www-form-urlencoded" -UseBasicParsing -ErrorAction Stop
            if ($resp.access_token) {
                return $resp.access_token
            }
        } catch {
            # Try next client
        }
    }
    Write-Error "Failed to obtain token for ${Username} with clients: $($clientsToTry -join ', ')"
    return $null
}

function Invoke-Api {
    param(
        [string]$Path,
        [string]$Method = "GET",
        [string]$Token = $null,
        [object]$Body = $null,
        [string]$ContentType = "application/json",
        [hashtable]$Headers = @{}
    )
    $allHeaders = @{}
    if ($Headers) { foreach ($k in $Headers.Keys) { $allHeaders[$k] = $Headers[$k] } }
    if ($Token) { $allHeaders["Authorization"] = "Bearer $Token" }

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $uri = "${GatewayUrl}${Path}"
    try {
        $params = @{
            Uri = $uri
            Method = $Method
            Headers = $allHeaders
            UseBasicParsing = $true
            ErrorAction = "Stop"
        }
        if ($Body -ne $null) {
            if ($Body -is [string]) {
                $params["Body"] = $Body
            } else {
                $params["Body"] = ($Body | ConvertTo-Json -Depth 10)
            }
            $params["ContentType"] = $ContentType
        }
        $response = Invoke-RestMethod @params
        $sw.Stop()
        return [PSCustomObject]@{
            StatusCode = 200
            Data = $response
            LatencyMs = [int]$sw.ElapsedMilliseconds
            Error = $null
        }
    } catch {
        $sw.Stop()
        $statusCode = 0
        $respBody = ""
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
            try {
                $stream = $_.Exception.Response.GetResponseStream()
                $reader = [System.IO.StreamReader]::new($stream)
                $respBody = $reader.ReadToEnd()
            } catch {}
        }
        return [PSCustomObject]@{
            StatusCode = $statusCode
            Data = $respBody
            LatencyMs = [int]$sw.ElapsedMilliseconds
            Error = $_.Exception.Message
        }
    }
}

# ========================================================
# SECTION 1: Health Endpoints Across Gateway
# ========================================================
Write-Host "`n--- 1. Gateway & Microservices Health Checks ---" -ForegroundColor Yellow

$healthChecks = @(
    @{ Name = "API Gateway Actuator"; Path = "/actuator/health"; ExpectedStatus = 200 },
    @{ Name = "Backend Health"; Path = "/api/health"; ExpectedStatus = 200 },
    @{ Name = "AI Service Health"; Path = "/api/ai/health"; ExpectedStatus = 200 },
    @{ Name = "Subscription Service Health"; Path = "/api/plans/health"; ExpectedStatus = 200 },
    @{ Name = "Session Service Health"; Path = "/api/sessions/health"; ExpectedStatus = 200 },
    @{ Name = "Media Service Health"; Path = "/api/media/health"; ExpectedStatus = 200 }
)

foreach ($hc in $healthChecks) {
    $res = Invoke-Api -Path $hc.Path -Method "GET"
    if ($res.StatusCode -eq $hc.ExpectedStatus) {
        Record-Result -TestName $hc.Name -Status "PASS" -Details "Returned $($res.StatusCode)" -LatencyMs $res.LatencyMs
    } else {
        Record-Result -TestName $hc.Name -Status "FAIL" -Details "Expected $($hc.ExpectedStatus) but got $($res.StatusCode): $($res.Error)" -LatencyMs $res.LatencyMs
    }
}

# ========================================================
# SECTION 2: Gateway Security & Header Hardening
# ========================================================
Write-Host "`n--- 2. Gateway Security & Header Hardening ---" -ForegroundColor Yellow

# 2.1 Verify /internal/** endpoints cannot be accessed through Gateway
$internalRes = Invoke-Api -Path "/internal/rooms/test-room/access/test-user" -Method "GET"
if ($internalRes.StatusCode -eq 404) {
    Record-Result -TestName "Gateway Internal Route Block" -Status "PASS" -Details "Unrouted /internal/** returned 404 Not Found" -LatencyMs $internalRes.LatencyMs
} else {
    Record-Result -TestName "Gateway Internal Route Block" -Status "FAIL" -Details "Expected 404 for /internal/** but got $($internalRes.StatusCode)" -LatencyMs $internalRes.LatencyMs
}

# 2.2 Verify Gateway CORS & Security Headers
try {
    $req = [System.Net.HttpWebRequest]::Create("${GatewayUrl}/api/health")
    $req.Method = "GET"
    $req.Headers.Add("Origin", "http://192.168.0.3:4200")
    $resp = $req.GetResponse()
    $allowOrigin = $resp.Headers["Access-Control-Allow-Origin"]
    $exposeHeaders = $resp.Headers.GetValues("Access-Control-Expose-Headers")
    $contentTypeOptions = $resp.Headers["X-Content-Type-Options"]
    $resp.Close()

    if ($allowOrigin -eq "http://192.168.0.3:4200") {
        Record-Result -TestName "Gateway LAN Origin Reflection" -Status "PASS" -Details "Access-Control-Allow-Origin reflects LAN origin"
    } else {
        Record-Result -TestName "Gateway LAN Origin Reflection" -Status "FAIL" -Details "Got '$allowOrigin'"
    }

    if ($contentTypeOptions -eq "nosniff") {
        Record-Result -TestName "Gateway nosniff Header" -Status "PASS" -Details "X-Content-Type-Options is nosniff"
    } else {
        Record-Result -TestName "Gateway nosniff Header" -Status "FAIL" -Details "Got '$contentTypeOptions'"
    }

    # Verify deduplication: single header value without comma duplicates
    $exposeCombined = if ($exposeHeaders) { [string]::Join(",", $exposeHeaders) } else { "" }
    $hasDuplicate = $exposeCombined.Split(",") | Group-Object | Where-Object { $_.Count -gt 1 }
    if (-not $hasDuplicate) {
        Record-Result -TestName "Gateway Dedupe Response Headers" -Status "PASS" -Details "No duplicate Access-Control-Expose-Headers"
    } else {
        Record-Result -TestName "Gateway Dedupe Response Headers" -Status "FAIL" -Details "Duplicate entries detected: $exposeCombined"
    }
} catch {
    Record-Result -TestName "Gateway Header Verification" -Status "FAIL" -Details "Request failed: $($_.Exception.Message)"
}

# ========================================================
# SECTION 3: Authentication & User Sync
# ========================================================
Write-Host "`n--- 3. Authentication & User Sync ---" -ForegroundColor Yellow

$freeToken = Get-Token -Username $FreeUsername -Password $FreePassword
if (-not $freeToken) {
    Write-Error "Cannot proceed without valid Keycloak token for Free user."
    exit 1
}
Record-Result -TestName "Keycloak Password Grant Token" -Status "PASS" -Details "Successfully acquired Bearer token"

$syncRes = Invoke-Api -Path "/api/users/sync" -Method "POST" -Token $freeToken
if ($syncRes.StatusCode -eq 200 -and $syncRes.Data.id) {
    $userId = $syncRes.Data.id
    Record-Result -TestName "User Sync / Registration" -Status "PASS" -Details "Synced user id $userId" -LatencyMs $syncRes.LatencyMs
} else {
    Record-Result -TestName "User Sync / Registration" -Status "FAIL" -Details "Failed user sync: $($syncRes.StatusCode) $($syncRes.Error)" -LatencyMs $syncRes.LatencyMs
}

# ========================================================
# SECTION 4: Room Lifecycle, Canvas Ideas & Votes
# ========================================================
Write-Host "`n--- 4. Room Lifecycle, Canvas Ideas & Votes ---" -ForegroundColor Yellow

# Ensure active room quota is available by closing any leftover unclosed test rooms
$existingRooms = Invoke-Api -Path "/api/rooms" -Method "GET" -Token $freeToken
if ($existingRooms.StatusCode -eq 200 -and $existingRooms.Data) {
    foreach ($er in $existingRooms.Data) {
        if (-not $er.closed) {
            $null = Invoke-Api -Path "/api/rooms/$($er.id)/close" -Method "POST" -Token $freeToken
        }
    }
}

$roomName = "E2E Test Room $(Get-Random)"
$createRoomRes = Invoke-Api -Path "/api/rooms" -Method "POST" -Token $freeToken -Body @{ name = $roomName }
$roomId = $null
$inviteCode = $null

if ($createRoomRes.StatusCode -eq 200 -and $createRoomRes.Data.id) {
    $roomId = $createRoomRes.Data.id
    $inviteCode = $createRoomRes.Data.invitationCode
    Record-Result -TestName "Room Creation" -Status "PASS" -Details "Created room $roomId (Code: $inviteCode)" -LatencyMs $createRoomRes.LatencyMs
} else {
    Record-Result -TestName "Room Creation" -Status "FAIL" -Details "Failed: $($createRoomRes.StatusCode) $($createRoomRes.Error)" -LatencyMs $createRoomRes.LatencyMs
}

if ($roomId) {
    # 4.1 Live WebSocket Upgrade over Gateway with LAN Origin
    Write-Host "`n--- 4.1 Live WebSocket Upgrade via Gateway ---" -ForegroundColor Yellow
    try {
        $ws = [System.Net.WebSockets.ClientWebSocket]::new()
        $ws.Options.SetRequestHeader("Origin", "http://192.168.0.3:4200")
        $wsUri = [System.Uri]::new("ws://${HostIp}:8080/ws/rooms/${roomId}?token=${freeToken}")
        $cts = [System.Threading.CancellationTokenSource]::new(8000)
        $wsConnectTask = $ws.ConnectAsync($wsUri, $cts.Token)
        $wsConnectTask.Wait()

        if ($ws.State -eq [System.Net.WebSockets.WebSocketState]::Open) {
            Record-Result -TestName "Live Gateway WebSocket Upgrade" -Status "PASS" -Details "Connected to ws://... over LAN Origin (State: Open)"
            try {
                $closeCts = [System.Threading.CancellationTokenSource]::new(3000)
                $ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "Verification done", $closeCts.Token).Wait()
            } catch {}
        } else {
            Record-Result -TestName "Live Gateway WebSocket Upgrade" -Status "FAIL" -Details "WebSocket state is $($ws.State)"
        }
        $ws.Dispose()
    } catch {
        Record-Result -TestName "Live Gateway WebSocket Upgrade" -Status "FAIL" -Details "WS upgrade failed: $($_.Exception.Message)"
    }

    # 4.2 Room Join via Invitation Code
    $joinRes = Invoke-Api -Path "/api/rooms/join" -Method "POST" -Token $freeToken -Body @{ invitationCode = $inviteCode }
    if ($joinRes.StatusCode -eq 200) {
        Record-Result -TestName "Room Join" -Status "PASS" -Details "Joined room with invitation code $inviteCode" -LatencyMs $joinRes.LatencyMs
    } else {
        Record-Result -TestName "Room Join" -Status "FAIL" -Details "Failed: $($joinRes.StatusCode) $($joinRes.Error)" -LatencyMs $joinRes.LatencyMs
    }

    # 4.3 Idea Create on Canvas
    $ideaText = "Automated Idea $(Get-Random)"
    $createIdeaRes = Invoke-Api -Path "/api/rooms/${roomId}/ideas" -Method "POST" -Token $freeToken -Body @{
        text = $ideaText
        title = "Initial Title"
        x = 150.0
        y = 220.0
        color = "#FFEB3B"
    }
    $ideaId = $null
    if ($createIdeaRes.StatusCode -eq 200 -and $createIdeaRes.Data.id) {
        $ideaId = $createIdeaRes.Data.id
        Record-Result -TestName "Idea Create on Canvas" -Status "PASS" -Details "Created idea $ideaId at (150, 220)" -LatencyMs $createIdeaRes.LatencyMs
    } else {
        Record-Result -TestName "Idea Create on Canvas" -Status "FAIL" -Details "Failed: $($createIdeaRes.StatusCode) $($createIdeaRes.Error)" -LatencyMs $createIdeaRes.LatencyMs
    }

    # 4.4 Idea Move / Position Update
    if ($ideaId) {
        $moveIdeaRes = Invoke-Api -Path "/api/ideas/${ideaId}" -Method "PUT" -Token $freeToken -Body @{
            text = "$ideaText Moved"
            title = "Updated Title"
            x = 350.0
            y = 420.0
            color = "#FFEB3B"
        }
        if ($moveIdeaRes.StatusCode -eq 200 -and $moveIdeaRes.Data.x -eq 350.0) {
            Record-Result -TestName "Idea Move on Canvas" -Status "PASS" -Details "Moved idea to (350, 420)" -LatencyMs $moveIdeaRes.LatencyMs
        } else {
            Record-Result -TestName "Idea Move on Canvas" -Status "FAIL" -Details "Failed: $($moveIdeaRes.StatusCode) $($moveIdeaRes.Error)" -LatencyMs $moveIdeaRes.LatencyMs
        }

        # 4.5 Normal Idea Vote
        $voteRes = Invoke-Api -Path "/api/ideas/${ideaId}/vote" -Method "POST" -Token $freeToken
        if ($voteRes.StatusCode -eq 200) {
            Record-Result -TestName "Idea Normal Vote" -Status "PASS" -Details "Voted on idea (voteCount: $($voteRes.Data.voteCount))" -LatencyMs $voteRes.LatencyMs
        } else {
            Record-Result -TestName "Idea Normal Vote" -Status "FAIL" -Details "Failed: $($voteRes.StatusCode) $($voteRes.Error)" -LatencyMs $voteRes.LatencyMs
        }
    }
}

# ========================================================
# SECTION 5: Subscription & Billing Flows
# ========================================================
Write-Host "`n--- 5. Subscription & Billing Flows ---" -ForegroundColor Yellow

# 5.1 Public Plans Listing
$plansRes = Invoke-Api -Path "/api/plans" -Method "GET"
if ($plansRes.StatusCode -eq 200 -and $plansRes.Data.Count -gt 0) {
    Record-Result -TestName "Subscription Plans Listing" -Status "PASS" -Details "Retrieved $($plansRes.Data.Count) plans" -LatencyMs $plansRes.LatencyMs
} else {
    Record-Result -TestName "Subscription Plans Listing" -Status "FAIL" -Details "Failed: $($plansRes.StatusCode) $($plansRes.Error)" -LatencyMs $plansRes.LatencyMs
}

# 5.2 Subscription Status (/me)
$subStatusRes = Invoke-Api -Path "/api/subscriptions/me" -Method "GET" -Token $freeToken
if ($subStatusRes.StatusCode -eq 200 -and $subStatusRes.Data.planCode) {
    Record-Result -TestName "User Subscription Status" -Status "PASS" -Details "Plan: $($subStatusRes.Data.planCode), Status: $($subStatusRes.Data.status)" -LatencyMs $subStatusRes.LatencyMs
} else {
    Record-Result -TestName "User Subscription Status" -Status "FAIL" -Details "Failed: $($subStatusRes.StatusCode) $($subStatusRes.Error)" -LatencyMs $subStatusRes.LatencyMs
}

# 5.3 Billing History
$billingRes = Invoke-Api -Path "/api/billing/history" -Method "GET" -Token $freeToken
if ($billingRes.StatusCode -eq 200) {
    Record-Result -TestName "Billing History" -Status "PASS" -Details "Retrieved billing history records" -LatencyMs $billingRes.LatencyMs
} else {
    Record-Result -TestName "Billing History" -Status "FAIL" -Details "Failed: $($billingRes.StatusCode) $($billingRes.Error)" -LatencyMs $billingRes.LatencyMs
}

# ========================================================
# SECTION 6: Session Snapshot & Multi-Format Export
# ========================================================
Write-Host "`n--- 6. Session Snapshot & Multi-Format Export ---" -ForegroundColor Yellow

$snapshotId = $null
if ($roomId) {
    $snapRes = Invoke-Api -Path "/api/sessions/snapshots" -Method "POST" -Token $freeToken -Body @{
        roomId = $roomId
        title = "Sprint 3 E2E Snapshot"
    }
    if (($snapRes.StatusCode -eq 200 -or $snapRes.StatusCode -eq 201) -and $snapRes.Data.id) {
        $snapshotId = $snapRes.Data.id
        Record-Result -TestName "Session Snapshot Creation" -Status "PASS" -Details "Created snapshot $snapshotId" -LatencyMs $snapRes.LatencyMs
    } else {
        Record-Result -TestName "Session Snapshot Creation" -Status "FAIL" -Details "Failed: $($snapRes.StatusCode) $($snapRes.Error)" -LatencyMs $snapRes.LatencyMs
    }

    if ($snapshotId) {
        # 6.2 Get Snapshot Detail
        $getSnapRes = Invoke-Api -Path "/api/sessions/snapshots/${snapshotId}" -Method "GET" -Token $freeToken
        if ($getSnapRes.StatusCode -eq 200 -and $getSnapRes.Data.title -eq "Sprint 3 E2E Snapshot") {
            Record-Result -TestName "Session Snapshot Fetch" -Status "PASS" -Details "Fetched snapshot detail" -LatencyMs $getSnapRes.LatencyMs
        } else {
            Record-Result -TestName "Session Snapshot Fetch" -Status "FAIL" -Details "Failed: $($getSnapRes.StatusCode)" -LatencyMs $getSnapRes.LatencyMs
        }

        # 6.3 Export Formats (.md, .json, .csv)
        foreach ($fmt in @("md", "json", "csv")) {
            $exportRes = Invoke-Api -Path "/api/sessions/snapshots/${snapshotId}/export?format=${fmt}" -Method "GET" -Token $freeToken
            if ($exportRes.StatusCode -eq 200 -and $exportRes.Data) {
                Record-Result -TestName "Session Export (${fmt})" -Status "PASS" -Details "Successfully exported ${fmt} format" -LatencyMs $exportRes.LatencyMs
            } else {
                Record-Result -TestName "Session Export (${fmt})" -Status "FAIL" -Details "Failed: $($exportRes.StatusCode)" -LatencyMs $exportRes.LatencyMs
            }
        }
    }
}

# ========================================================
# SECTION 7: Media Service & Canvas Images
# ========================================================
Write-Host "`n--- 7. Media Service & Canvas Images ---" -ForegroundColor Yellow

$mediaId = $null
$canvasImageId = $null

if ($roomId) {
    # 7.1 Upload 1x1 valid PNG
    $pngBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="
    $pngBytes = [System.Convert]::FromBase64String($pngBase64)
    $tempPngPath = [System.IO.Path]::GetTempFileName() + ".png"
    [System.IO.File]::WriteAllBytes($tempPngPath, $pngBytes)

    try {
        $uploadOutput = & curl.exe -s -X POST "${GatewayUrl}/api/media/rooms/${roomId}" `
            -H "Authorization: Bearer $freeToken" `
            -F "file=@${tempPngPath};type=image/png"
        
        $uploadJson = $uploadOutput | ConvertFrom-Json -ErrorAction SilentlyContinue
        if ($uploadJson -and $uploadJson.mediaId) {
            $mediaId = $uploadJson.mediaId
            Record-Result -TestName "Media Upload Valid PNG" -Status "PASS" -Details "Uploaded mediaId: $mediaId"
        } else {
            Record-Result -TestName "Media Upload Valid PNG" -Status "FAIL" -Details "Upload failed: $uploadOutput"
        }
    } catch {
        Record-Result -TestName "Media Upload Valid PNG" -Status "FAIL" -Details "Exception: $($_.Exception.Message)"
    } finally {
        if (Test-Path $tempPngPath) { Remove-Item $tempPngPath -Force }
    }

    # 7.2 Fetch Uploaded Image with JWT
    if ($mediaId) {
        $fetchRes = Invoke-Api -Path "/api/media/rooms/${roomId}/${mediaId}" -Method "GET" -Token $freeToken
        if ($fetchRes.StatusCode -eq 200) {
            Record-Result -TestName "Media Fetch with JWT" -Status "PASS" -Details "Retrieved binary media successfully" -LatencyMs $fetchRes.LatencyMs
        } else {
            Record-Result -TestName "Media Fetch with JWT" -Status "FAIL" -Details "Failed: $($fetchRes.StatusCode) $($fetchRes.Error)" -LatencyMs $fetchRes.LatencyMs
        }
    }

    # 7.3 Reject Invalid / Fake File (Magic byte validation)
    $tempSvgPath = [System.IO.Path]::GetTempFileName() + ".svg"
    [System.IO.File]::WriteAllText($tempSvgPath, "<svg><script>alert('xss')</script></svg>")
    try {
        $rejectOutput = & curl.exe -s -X POST "${GatewayUrl}/api/media/rooms/${roomId}" `
            -H "Authorization: Bearer $freeToken" `
            -F "file=@${tempSvgPath};type=image/svg+xml"
        
        if ($rejectOutput -match "UNSUPPORTED_MEDIA_TYPE|415|400") {
            Record-Result -TestName "Media Strict Magic Bytes Reject" -Status "PASS" -Details "Rejected unsupported SVG with UNSUPPORTED_MEDIA_TYPE"
        } else {
            Record-Result -TestName "Media Strict Magic Bytes Reject" -Status "FAIL" -Details "Expected reject but got: $rejectOutput"
        }
    } catch {
        Record-Result -TestName "Media Strict Magic Bytes Reject" -Status "FAIL" -Details "Exception: $($_.Exception.Message)"
    } finally {
        if (Test-Path $tempSvgPath) { Remove-Item $tempSvgPath -Force }
    }

    # 7.4 Canvas Image Attachment & Move
    if ($mediaId) {
        $canvasImgRes = Invoke-Api -Path "/api/rooms/${roomId}/canvas-images" -Method "POST" -Token $freeToken -Body @{
            mediaId = $mediaId
            contentType = "image/png"
            x = 200.0
            y = 250.0
            width = 300.0
            height = 200.0
            naturalWidth = 1
            naturalHeight = 1
            zIndex = 1
        }
        if (($canvasImgRes.StatusCode -eq 200 -or $canvasImgRes.StatusCode -eq 201) -and $canvasImgRes.Data.id) {
            $canvasImageId = $canvasImgRes.Data.id
            Record-Result -TestName "Canvas Image Attach" -Status "PASS" -Details "Attached image $canvasImageId on canvas at (200, 250)" -LatencyMs $canvasImgRes.LatencyMs
        } else {
            Record-Result -TestName "Canvas Image Attach" -Status "FAIL" -Details "Failed: $($canvasImgRes.StatusCode) $($canvasImgRes.Error)" -LatencyMs $canvasImgRes.LatencyMs
        }

        # 7.5 Canvas Image Update / Move
        if ($canvasImageId) {
            $updateImgRes = Invoke-Api -Path "/api/rooms/${roomId}/canvas-images/${canvasImageId}" -Method "PATCH" -Token $freeToken -Body @{
                x = 280.0
                y = 320.0
                width = 320.0
                height = 220.0
                zIndex = 2
            }
            if ($updateImgRes.StatusCode -eq 200 -and $updateImgRes.Data.x -eq 280.0) {
                Record-Result -TestName "Canvas Image Move" -Status "PASS" -Details "Moved canvas image to (280, 320)" -LatencyMs $updateImgRes.LatencyMs
            } else {
                Record-Result -TestName "Canvas Image Move" -Status "FAIL" -Details "Failed: $($updateImgRes.StatusCode)" -LatencyMs $updateImgRes.LatencyMs
            }
        }
    }
}

# ========================================================
# SECTION 8: Vote Pool Lifecycle (Room Creator)
# ========================================================
Write-Host "`n--- 8. Vote Pool Lifecycle ---" -ForegroundColor Yellow

$poolId = $null
if ($roomId -and $ideaId) {
    # 8.1 Create Vote Pool
    $poolRes = Invoke-Api -Path "/api/rooms/${roomId}/vote-pools" -Method "POST" -Token $freeToken -Body @{
        title = "Sprint 3 Priority Voting"
        votesPerParticipant = 3
        allowMultipleVotesPerIdea = $false
    }
    if (($poolRes.StatusCode -eq 200 -or $poolRes.StatusCode -eq 201) -and $poolRes.Data.id) {
        $poolId = $poolRes.Data.id
        Record-Result -TestName "Vote Pool Creation" -Status "PASS" -Details "Created vote pool $poolId (Status: $($poolRes.Data.status))" -LatencyMs $poolRes.LatencyMs
    } else {
        Record-Result -TestName "Vote Pool Creation" -Status "FAIL" -Details "Failed: $($poolRes.StatusCode) $($poolRes.Error)" -LatencyMs $poolRes.LatencyMs
    }

    if ($poolId) {
        # 8.2 Cast Vote in Pool
        $castVoteRes = Invoke-Api -Path "/api/rooms/${roomId}/vote-pools/${poolId}/votes" -Method "POST" -Token $freeToken -Body @{
            ideaId = $ideaId
        }
        if ($castVoteRes.StatusCode -eq 200) {
            Record-Result -TestName "Vote Pool Cast Vote" -Status "PASS" -Details "Cast vote for idea $ideaId" -LatencyMs $castVoteRes.LatencyMs
        } else {
            Record-Result -TestName "Vote Pool Cast Vote" -Status "FAIL" -Details "Failed: $($castVoteRes.StatusCode) $($castVoteRes.Error)" -LatencyMs $castVoteRes.LatencyMs
        }

        # 8.3 Close Vote Pool & Get Results
        $closePoolRes = Invoke-Api -Path "/api/rooms/${roomId}/vote-pools/${poolId}/close" -Method "POST" -Token $freeToken
        if ($closePoolRes.StatusCode -eq 200 -and $closePoolRes.Data.status -eq "CLOSED") {
            Record-Result -TestName "Vote Pool Close & Tally" -Status "PASS" -Details "Closed pool, tally count: $($closePoolRes.Data.results.Count)" -LatencyMs $closePoolRes.LatencyMs
        } else {
            Record-Result -TestName "Vote Pool Close & Tally" -Status "FAIL" -Details "Failed: $($closePoolRes.StatusCode)" -LatencyMs $closePoolRes.LatencyMs
        }
    }
}

# ========================================================
# SECTION 9: Clean-up
# ========================================================
Write-Host "`n--- 9. Cleanup ---" -ForegroundColor Yellow

if ($roomId -and $canvasImageId) {
    $delImgRes = Invoke-Api -Path "/api/rooms/${roomId}/canvas-images/${canvasImageId}" -Method "DELETE" -Token $freeToken
    if ($delImgRes.StatusCode -eq 200 -or $delImgRes.StatusCode -eq 204) {
        Record-Result -TestName "Cleanup Canvas Image" -Status "PASS" -Details "Cleaned up canvas image"
    }
}

if ($ideaId) {
    $delIdeaRes = Invoke-Api -Path "/api/ideas/${ideaId}" -Method "DELETE" -Token $freeToken
    if ($delIdeaRes.StatusCode -eq 200 -or $delIdeaRes.StatusCode -eq 204) {
        Record-Result -TestName "Cleanup Idea" -Status "PASS" -Details "Cleaned up idea"
    }
}

if ($roomId) {
    $null = Invoke-Api -Path "/api/rooms/${roomId}/close" -Method "POST" -Token $freeToken
}

# ========================================================
# SUMMARY REPORT
# ========================================================
Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host " Automated Critical Flow Tests Summary" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Total Tests Run : $($TotalPassed + $TotalFailed)" -ForegroundColor White
Write-Host "Passed          : $TotalPassed" -ForegroundColor Green
Write-Host "Failed          : $TotalFailed" -ForegroundColor $(if ($TotalFailed -eq 0) { "Green" } else { "Red" })

if ($TotalFailed -gt 0) {
    Write-Host "`nFailed Tests List:" -ForegroundColor Red
    $Results | Where-Object { $_.Status -eq "FAIL" } | ForEach-Object {
        Write-Host "  - $($_.Test): $($_.Details)" -ForegroundColor Red
    }
    exit 1
} else {
    Write-Host "`nAll platform critical flows PASSED flawlessly!" -ForegroundColor Green
    exit 0
}
