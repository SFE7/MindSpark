# AI Service Test Coverage Matrix

This matrix documents the verification of all 7 AI feature endpoints across the 9 core functional criteria plus the 5 cross-cutting security, prompt-engineering, and boundary rules.
All tests are implemented in `com.mindspark.ai.api.EndpointFeatureMatrixTest` and verified via Maven Surefire test execution.

## 1. Feature Endpoints Coverage (7 x 9 Matrix)

| Endpoint | 1. 200 Happy Path (Shape) | 2. 400 Validation | 3. 401 Unauthorized | 4. Tier Routing (Free:10 / Prem:20) | 5. Tier Tampering Prevention | 6. 429 Daily Limit | 7. 503 Provider Refund | 8. 502 Bad Model Output | 9. Unknown IDs Handled |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Generate Ideas** (`/api/ai/ideas/generate`) | `testGenerate_200HappyPath` [PASS] | `testGenerate_400Validation` [PASS] | `testGenerate_401Unauthorized` [PASS] | `testGenerate_TierRoutingAndLimits` [PASS] | `testGenerate_TierTamperingPrevention` [PASS] | `testGenerate_429DailyLimit` [PASS] | `testGenerate_503ProviderRefund` [PASS] | `testGenerate_502BadModelOutput` [PASS] | `testGenerate_OutputSchemaValidation` [PASS] |
| **Improve Idea** (`/api/ai/ideas/improve`) | `testImprove_200HappyPath` [PASS] | `testImprove_400Validation` [PASS] | `testImprove_401Unauthorized` [PASS] | `testImprove_TierRoutingAndLimits` [PASS] | `testImprove_TierTamperingPrevention` [PASS] | `testImprove_429DailyLimit` [PASS] | `testImprove_503ProviderRefund` [PASS] | `testImprove_502BadModelOutput` [PASS] | `testImprove_OutputSchemaValidation` [PASS] |
| **Group Ideas** (`/api/ai/ideas/group`) | `testGroup_200HappyPath` [PASS] | `testGroup_400Validation` [PASS] | `testGroup_401Unauthorized` [PASS] | `testGroup_TierRoutingAndLimits` [PASS] | `testGroup_TierTamperingPrevention` [PASS] | `testGroup_429DailyLimit` [PASS] | `testGroup_503ProviderRefund` [PASS] | `testGroup_502BadModelOutput` [PASS] | `testGroup_UnknownIdsDroppedAndUngrouped` [PASS] |
| **Action Plan** (`/api/ai/ideas/action-plan`) | `testActionPlan_200HappyPath` [PASS] | `testActionPlan_400Validation` [PASS] | `testActionPlan_401Unauthorized` [PASS] | `testActionPlan_TierRoutingAndLimits` [PASS] | `testActionPlan_TierTamperingPrevention` [PASS] | `testActionPlan_429DailyLimit` [PASS] | `testActionPlan_503ProviderRefund` [PASS] | `testActionPlan_502BadModelOutput` [PASS] | `testActionPlan_UnknownIdsDropped` [PASS] |
| **Find Risks** (`/api/ai/ideas/risks`) | `testRisks_200HappyPath` [PASS] | `testRisks_400Validation` [PASS] | `testRisks_401Unauthorized` [PASS] | `testRisks_TierRoutingAndLimits` [PASS] | `testRisks_TierTamperingPrevention` [PASS] | `testRisks_429DailyLimit` [PASS] | `testRisks_503ProviderRefund` [PASS] | `testRisks_502BadModelOutput` [PASS] | `testRisks_OutputSchemaValidation` [PASS] |
| **Compare Ideas** (`/api/ai/ideas/compare`) | `testCompare_200HappyPath` [PASS] | `testCompare_400Validation` [PASS] | `testCompare_401Unauthorized` [PASS] | `testCompare_TierRoutingAndLimits` [PASS] | `testCompare_TierTamperingPrevention` [PASS] | `testCompare_429DailyLimit` [PASS] | `testCompare_503ProviderRefund` [PASS] | `testCompare_502BadModelOutput` [PASS] | `testCompare_UnknownIdsDropped` [PASS] |
| **Summarize Session** (`/api/ai/sessions/summarize`) | `testSummarize_200HappyPath` [PASS] | `testSummarize_400Validation` [PASS] | `testSummarize_401Unauthorized` [PASS] | `testSummarize_TierRoutingAndLimits` [PASS] | `testSummarize_TierTamperingPrevention` [PASS] | `testSummarize_429DailyLimit` [PASS] | `testSummarize_503ProviderRefund` [PASS] | `testSummarize_502BadModelOutput` [PASS] | `testSummarize_UnknownIdsDropped` [PASS] |

## 2. Cross-Cutting Rules Coverage

| Rule Description | Test Method Name | Verified Assertion | Status |
| :--- | :--- | :--- | :--- |
| **Total request payload > 6000 chars -> 400** | `testCrossCutting_TotalPayloadOver6000Chars_Returns400` | Rejects payload of 6001 chars with 400 `VALIDATION_ERROR` | **PASS** |
| **Individual ideas > 300 chars trimmed server-side** | `testCrossCutting_IndividualIdeasOver300Chars_TrimmedInPrompt` | Asserts built `ChatRequest.userPrompt` contains exactly 300 chars trimmed from a 450-char input idea | **PASS** |
| **Prompt injection inside idea text delimited by `<user_data>`** | `testCrossCutting_PromptInjection_DelimitedByUserData` | Asserts adversarial prompt injection text is bounded inside `<user_data>` and system prompt instructs model to treat as untrusted data | **PASS** |
| **System prompt of EVERY endpoint contains language instruction** | `testCrossCutting_AllSystemPromptsContainLanguageInstruction` | Asserts all 7 prompt builder outputs contain "Respond in the EXACT SAME LANGUAGE as the user input text" | **PASS** |
| **Error bodies and logs NEVER contain secrets or provider body** | `testCrossCutting_ErrorBodiesAndLogsNeverContainSecretsOrProviderBody` | Asserts 503 response body from simulated provider error containing fake secret key never leaks downstream | **PASS** |

## 3. Test Execution Proof
Full test suite execution proof is archived in `docs/proof/step6-all-tests.txt` (146 tests run, 0 failures, 0 errors, 1 skipped).
Endpoint matrix execution proof is archived in `docs/proof/step6-matrix-test.txt` (68 tests run, 0 failures, 0 errors, 0 skipped).
