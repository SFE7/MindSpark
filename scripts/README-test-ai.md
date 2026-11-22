# MindSpark AI Service - End-to-End Verification Guide

This guide explains how to run the end-to-end integration test script `scripts/test-ai.ps1` against live Keycloak users and LLM providers.

---

## 1. Setup Temporary Keycloak Test Client

To test user token generation with standard password grants, create a **temporary** client in Keycloak.

> [!WARNING]
> **NEVER enable Direct Access Grants on the existing `frontend` client.** Always create a separate, temporary test client and delete it after testing.

1. Open Keycloak Admin Console at `http://localhost:8180` (or `http://<HOST_IP>:8180`).
2. Select realm: **`mindspark`**.
3. Navigate to **Clients** -> **Create client**:
   - **Client type:** `OpenID Connect`
   - **Client ID:** e.g. `temp-ai-tester`
   - Click **Next**.
4. Capability config:
   - **Client authentication:** `Off` (Public client)
   - **Direct access grants:** `Enabled` (Checked)
   - Click **Save**.

---

## 2. Set Environment Variables

Set the credentials in your PowerShell session:

```powershell
$env:KC_TEST_CLIENT_ID = "temp-ai-tester"
$env:TEST_FREE_PASSWORD = "your-test-free-password"
$env:TEST_PREMIUM_PASSWORD = "your-test-premium-password"
# Optional: if accessing from another machine on your LAN
# $env:HOST_IP = "192.168.1.X"
```

*(Alternatively, these 4 keys can be placed directly in your local `.env` file without echoing or committing).*

---

## 3. Run the Verification Script

Run the automated test script in PowerShell:

```powershell
.\scripts\test-ai.ps1
```

### Optional: Test Daily Limit Rollover / Rejection

To verify that the daily limit triggers HTTP 429 `DAILY_LIMIT_REACHED` for the `test-free` user (consumes up to 10 requests from the test-free quota):

```powershell
.\scripts\test-ai.ps1 -LimitTest
```

---

## 4. Understanding Results & Log Diagnostics

1. **Pass/Fail Table:**
   - Verifies `test-free` resolves to `FREE` tier (model list: `gemini-3.5-flash-lite`, `gemini-3.1-flash-lite`).
   - Verifies `test-premium` resolves to `PREMIUM` tier (model list: `openai/gpt-oss-120b`, `qwen/qwen3.8-27b`).
   - Verifies quota limits (FREE: 10/day, PREMIUM: 20/day) and shared usage.
   - Verifies all 7 endpoints with real English, French, and Arabic prompts.
   - Verifies token security (401 without token) and payload bounds (400 on >6000 chars).

2. **Model Fallback Diagnostic:**
   - If you see `ModelUnavailable`, it indicates that the configured model ID was not found or was deprecated by the provider (wrong model ID, 404 or explicit `model_not_found` error code).
   - If you see `ProviderBadRequest`, it indicates that the provider rejected the request format or constraint (HTTP 400 with e.g. `json_validate_failed` or context length exceeded).
   - In both cases, the service automatically logs the specific reason and falls back to the next configured model in that tier.

---

## 5. Cleanup

After running the tests:
1. Go back to Keycloak Admin Console -> **Clients**.
2. Select `temp-ai-tester` -> Click **Delete client**.
3. Clear session environment variables:
   ```powershell
   Remove-Item Env:KC_TEST_CLIENT_ID, Env:TEST_FREE_PASSWORD, Env:TEST_PREMIUM_PASSWORD
   ```
