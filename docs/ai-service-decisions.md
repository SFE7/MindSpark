# AI Service Decision Log

- Phase 0: HTTP/JSON stack is Spring WebMvc RestClient (built into Spring Boot 4.1.1 / Spring 6.2+) and Jackson ObjectMapper.
- Phase 0: Database persistence has spring-boot-starter-data-jpa and PostgreSQL runtime driver, with ddl-auto=none; no Flyway/Liquibase present; schema management handled via SQL schema script or JDBC initialization.
- Phase 0: API Gateway uses Spring Cloud Gateway (WebFlux reactive, Spring Boot 3.4.3) with Eureka lb:// routes; ai-service route will use static http://ai-service:8081 before monolith fallback route.
- Phase 1: Gemini and Groq verified to support OpenAI-compatible chat completion endpoints with standard Bearer authorization header; base URLs made configurable with official defaults.
- Phase 1: OpenAI-compatible client implemented via Spring RestClient with configurable connect timeout (5s) and read timeout (45s).
- Phase 1: Typed provider exceptions ensure provider response bodies and API keys are never leaked to caller messages or logs.
- Phase 1: ModelRouter implemented with fallback on 429, 404, 5xx/IO, and invalid JSON output; AllProvidersFailed carries rate-limited flag and min retry-after.
- Phase 2: ai_usage schema created via schema.sql and DatabaseInitializer ApplicationRunner for portability across PostgreSQL and H2.
- Phase 2: Atomic QuotaService implemented via conditional atomic update + duplicate key race handling on insert, with non-negative refunds and injectable UTC Clock.
- Phase 2: GET /api/ai/usage implemented returning tier, dailyLimit, usedToday, remainingToday, and resetsAtUtc.
- Phase 3: GlobalExceptionHandler standardizes error body {error, message, retryAfterSeconds} across validation (400), daily limit (429), provider busy (429), bad model output (502), and AI unavailable (503).
- Phase 3: StructuredOutputHelper strips markdown fences and extracts outermost JSON, validating parsed DTOs.
- Phase 3: AiOrchestrator manages JWT tier resolution, payload size verification, atomic quota reservation and refund on any failure.
- Phase 3: Security & prompt-injection hardening wraps user data in <user_data> tags and enforces output in caller's input language.
- Phase 4-7: Implemented all 7 AI feature endpoints under /api/ai/ideas and /api/ai/sessions behind AiOrchestrator.
- Phase 4-7: Group ideas normalizes IDs server-side guaranteeing every input ID appears exactly once across groups and ungrouped.
- Phase 4-7: Action plan, summarize, and compare drop unknown IDs and normalize enum fields/severities defensively.
- Phase 8: Added route for /api/ai/** to http://ai-service:8081 before monolith fallback in api-gateway with 60s response timeout and 5s connect timeout.
- Phase 8: Rebuilt ai-service and api-gateway; verified all 7 endpoints and /me return 401 without token, health returns 200 directly and via gateway, and backend health regression passes.
- Follow-up Audit: Created docs/ai-service-audit.md documenting Docker startup NoSuchBeanDefinitionException root causes, slice test isolation gap, test fixture secret scan hit, .env.example edit rationale, and container inspect proof.
- Follow-up Step 3: Implemented @SpringBootTest AiServiceContextLoadTest with MockJwtDecoderConfig and H2 PostgreSQL mode, proving all critical beans wire, with simulated missing bean failure proof.
- Follow-up Step 4: Upgraded QuotaService.reserve() to dialect-aware atomic upsert: PostgreSQL uses INSERT ... ON CONFLICT (user_id, usage_day) DO NOTHING then guarded UPDATE, preventing transaction aborts on PostgreSQL; verified with 25-parallel/limit-10 and first-request-of-day concurrency test against real Postgres (ai_quota_test database in ai-db container, safely dropped after test).
- Follow-up Step 5: Resolved Jackson dual-stack mismatch by adopting Spring Boot 4's native Jackson 3 (tools.jackson): removed redundant com.fasterxml.jackson.core:jackson-databind dependency from pom.xml and manual ObjectMapper bean in RestClientConfig, switching all classes to tools.jackson.databind.ObjectMapper, verified full test suite passes.
- Follow-up Step 6: Created EndpointFeatureMatrixTest implementing 68 tests covering all 7 feature endpoints across all 9 functional criteria and 5 cross-cutting rules; verified with 146 total passing tests and generated docs/ai-service-test-matrix.md.
- Follow-up Step 7: Executed live provider check without printing secrets; verified Gemini models (gemini-3.5-flash-lite and gemini-3.1-flash-lite) return 2xx and valid non-empty JSON; detected Groq configured IDs (openai/gpt-oss-120b and qwen/qwen3.8-27b) return 4xx (ModelUnavailable); reported to operator with proposed replacements without silently altering configured model IDs.
- Follow-up Step 8: Rebuilt ai-service container with --no-deps; captured complete gateway re-proof in docs/proof/step8-gateway-proof.txt verifying public health (200), protected /me and 7 feature endpoints without token (401), garbage token (401), direct ai-service health (200), and monolith health regressions (200).
- Follow-up Step 9: Deep-diagnosed live provider models. Groq model check proved `qwen/qwen3.8-27b` returns HTTP 200 OK in ~189ms. `openai/gpt-oss-120b` was verified to exist on Groq (confirmed via GET /openai/v1/models in docs/proof/step9-groq-models-list.txt); the initial 400 was caused by test `maxOutputTokens=10` under which internal reasoning tokens exhausted the budget before JSON emission (`json_validate_failed`). With standard token budgets, it returns HTTP 200 OK. Gemini `gemini-3.5-flash-lite` 3-run latency check showed latencies of 5.8s, 17.8s, and 9.2s (docs/proof/step9-gemini-latency.txt), proving the earlier 54s was a cold-start outlier.
- Follow-up Step 10: Implemented robust error mapping distinguishing `ModelUnavailable` (HTTP 404 or explicit `model_not_found`/`does not exist`) from `ProviderBadRequest` (other HTTP 400s like `json_validate_failed` or context exceeded). Implemented configurable total timeout budget in `ModelRouter` (default 50s, computing `min(45s, remaining time)` per call) to guarantee client response within gateway's 60s timeout. Configured reasoning parameters (`reasoning_effort: "low"` for Groq/Gemini and `reasoning_format: "parsed"` for Groq) per official docs (Groq: https://console.groq.com/docs/reasoning; Gemini: https://ai.google.dev/gemini-api/docs/openai#reasoning). Validated all 7 endpoints live across FREE and PREMIUM tiers in French, Arabic, and English (docs/proof/step10-live-endpoints.txt): all 14 returned 200 OK, valid JSON, validated DTOs, and finish_reason="stop" (zero truncation). Full non-live suite passed with 153 tests (docs/proof/step10-mvn-test.txt). Secret scan clean (docs/proof/step10-secret-scan.txt).

## Known risks

### 1. Per-User Quota vs. Global Provider Rate and Token Limits
- **Per-User Daily Quota (Application-Level)**: Managed atomically in PostgreSQL (`ai_usage` table) keyed by token `sub`. Each user is strictly capped at their tier limit (10 requests/day for FREE, 20 requests/day for PREMIUM).
- **Global Provider Limits (Third-Party Account-Level)**: Upstream providers enforce account-wide rate limits (RPM) and token volume limits (TPM) shared across *all* active users of the application.
  - Gemini Flash-Lite: 15 RPM, 250K TPM, 500 requests/day total across all users.
  - Groq: 30 RPM, 1K requests/day, 8,000 tokens/minute (TPM) cap, 200,000 tokens/day total.

### 2. Why Per-User Quotas Do Not Guarantee Provider Success
- A per-user quota of 10 or 20 daily requests protects against single-user abuse, but **does not guarantee upstream provider availability**.
- For example, Groq's 8,000 tokens-per-minute cap can be exhausted by as few as 3 to 4 simultaneous active users running idea generation, comparison, or summarization within the same 60-second window, regardless of whether those users have remaining daily quota.
- When an account-wide limit is exhausted, even a user making their very first request of the day will be rate-limited by the provider.

### 3. System Behavior Under Provider Rate Limits
- When a provider responds with HTTP 429:
  1. The `OpenAiCompatibleClient` parses the `Retry-After` header (if provided) and throws a typed `ProviderRateLimited` exception.
  2. The `ModelRouter` catches this exception and attempts the next configured fallback model in that tier's ordered model list.
  3. If all configured fallback models in the tier fail with 429, the router throws `AllProvidersFailed` flagged with `rateLimited = true` and the minimum `retryAfterSeconds`.
  4. The `AiOrchestrator` catches the failure and immediately **refunds** the user's reserved daily quota so the user is not penalized for an upstream provider outage.
  5. The `GlobalExceptionHandler` converts the error into HTTP 429 `PROVIDER_RATE_LIMITED` (or HTTP 503 `AI_UNAVAILABLE` if unexpected server error) with a standard JSON body containing `{error, message, retryAfterSeconds}`.

### 4. Operator Recommendations
1. **Groq Model IDs & Reasoning**: Both configured models (`openai/gpt-oss-120b` and `qwen/qwen3.8-27b`) exist and are active on Groq. The system now automatically configures `reasoning_effort: "low"` and `reasoning_format: "parsed"`, reducing internal reasoning overhead (e.g. from ~74 tokens down to ~6 tokens on gpt-oss-120b). The previous rough conjecture of ">=150 tokens" is retracted: with default reasoning effort ("medium"), 100 tokens occasionally hit token exhaustion during reasoning (`json_validate_failed`), whereas 300 tokens consistently passed; with `reasoning_effort: "low"`, gpt-oss-120b completes successfully even with 100 tokens (captured in docs/proof/step10-gpt-oss-120b-100tokens.txt and docs/proof/step10-gpt-oss-120b-300tokens.txt).
2. **Upgrade Provider Tiers**: To support production traffic with multiple concurrent users, upgrade Groq and Gemini developer tiers beyond free-tier caps (specifically addressing Groq's 8K TPM constraint).
3. **Model Fallback Chain**: Keep at least two distinct provider models configured in each tier list so transient rate spikes on one model trigger seamless failover to the secondary model.
4. **Monitoring**: Track HTTP 429 and 503 response rates in the gateway and logs to detect upstream throttling proactively.


## Follow-up 4 Decisions: Robust Fallback, Router Time Share, and Keycloak Integration Readiness

### 1. Router Time Sharing (Per-Attempt Timeout Cap)
- **Decision**: Added `perAttemptTimeoutSeconds` (default 25s) to `AiTiersProperties` in addition to the 50s `totalTimeoutSeconds` deadline budget.
- **Rationale**: If the primary model stalls or hangs on an unresponsive upstream socket, a single call could consume the entire 50s total deadline, leaving 0ms for the fallback model and resulting in a gateway timeout (60s). Capping each attempt at `min(perAttemptCap, remainingBudget)` ensures the fallback model always has at least 25s to fulfill the request.
- **Verification**: Unit tested in `ModelRouterTest#testPerAttemptTimeoutEnforcedForSlowPrimary` where a slow primary was interrupted at 100ms and the fallback model answered successfully.

### 2. Groq Reasoning Compatibility
- **Decision**: Confirmed that `reasoning_format: "parsed"` and `reasoning_effort: "low"` are accepted by Groq without HTTP 400 rejection across both `openai/gpt-oss-120b` and `qwen/qwen3.8-27b`.
- **Findings**: `openai/gpt-oss-120b` generated exactly 6 reasoning tokens under `reasoning_effort: "low"` (`step11-reasoning-low-tokens.txt`). `qwen/qwen3.8-27b` succeeded cleanly on prompts without rejecting parameters, but hits Groq's 8,000 TPM rate limit during rapid bursts, triggering seamless fallback to `openai/gpt-oss-120b`.

### 3. Gemini Latency and Fallback Behavior
- **Decision**: Evaluated 5 consecutive runs for `gemini-3.5-flash-lite` and `gemini-3.1-flash-lite`.
- **Findings**:
  - `gemini-3.5-flash-lite` averaged ~2.5s latency without reasoning and ~1.5s with `low`.
  - `gemini-3.1-flash-lite` exhibited severe latency (13.9s - 32.6s) without reasoning and frequent HTTP 503 `ProviderUnavailable` responses.
  - The per-attempt timeout cap (25s) successfully terminated `gemini-3.1-flash-lite` when it hung during prompt execution, triggering fallback to `gemini-3.5-flash-lite`.

### 4. PostgreSQL Quota IT Automation
- **Decision**: Provided `scripts/run-postgres-quota-it.ps1` to execute `PostgresQuotaConcurrencyIT` in an isolated throwaway database (`ai_quota_test`) against the running `ai-db` container without impacting the main `ai_db`.

### 5. Keycloak Token Issuer Validation
- **Decision**: Inspected `JwtAuthConverter` and `application.properties` configuring `KEYCLOAK_ISSUER_URI: http://${HOST_IP}:8180/realms/mindspark`.
- **Finding**: Spring Security validates `jwt.getIssuer().toString().equals(issuerUri)`. Tokens requested from `http://${HOST_IP}:8180` match exactly. Tokens requested from `http://localhost:8180` when `HOST_IP` is an IP (e.g., LAN IP) will be rejected due to strict string comparison.


## Follow-up 5 Decisions: Production Hygiene & Cross-Shell Hardening

### 1. Removal of Test-Specific Fallbacks from Production Code
- **Decision**: Removed `if (result == null) { result = client.complete(request); }` from `ModelRouter.java`.
- **Rationale**: Production code should not carry defensive fallbacks for Mockito interface mocks. `AiOrchestratorTest.java` was corrected to properly stub `complete(any(), any())`, aligning unit tests with production semantics where timeouts are first-class parameters.

### 2. PowerShell 5.1 & Multi-Platform Test Script Hardening
- **Decision**: Updated `scripts/test-ai.ps1` with:
  1. `-UseBasicParsing` on all web requests to prevent interactive IE prompts on PS 5.1.
  2. UTF-8 response stream decoding via `$response.RawContentStream` and `[System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8)` to prevent ANSI/Latin-1 mojibake on Arabic and French responses.
  3. Strict validation: tests fail immediately if Arabic characters (`[\u0600-\u06FF]`) are missing.
  4. Configurable test usernames (`TEST_FREE_USERNAME` and `TEST_PREMIUM_USERNAME`) with defaults.
  5. Saved with UTF-8 BOM (`EF-BB-BF`) to ensure correct script interpretation in legacy Windows PowerShell hosts.
