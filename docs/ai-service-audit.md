# AI Service Self-Audit & Disclosure Report

Date: 2026-11-19
Environment: MindSpark ai-service microservice

---

### a) Docker Startup Failures & Fixes

During the initial container rebuild of `ai-service`, the Spring Boot application failed to start twice in container runtime:

1. **Failure 1 (RestClient$Builder missing):**
   - **Exception:** `org.springframework.beans.factory.UnsatisfiedDependencyException` caused by `org.springframework.beans.factory.NoSuchBeanDefinitionException: No qualifying bean of type 'org.springframework.web.client.RestClient$Builder' available: expected at least 1 bean which qualifies as autowire candidate.`
   - **Root Cause:** `DefaultLlmClientFactory` injected `RestClient.Builder`. In this configuration, Spring Boot's automatic client builder auto-configuration was not exporting a standalone builder bean into the application context.
   - **Fix:** Created `RestClientConfig` providing `@Bean @ConditionalOnMissingBean public RestClient.Builder restClientBuilder() { return RestClient.builder(); }`.

2. **Failure 2 (ObjectMapper missing):**
   - **Exception:** `org.springframework.beans.factory.UnsatisfiedDependencyException` caused by `org.springframework.beans.factory.NoSuchBeanDefinitionException: No qualifying bean of type 'com.fasterxml.jackson.databind.ObjectMapper' available: expected at least 1 bean which qualifies as autowire candidate.`
   - **Root Cause:** `DefaultLlmClientFactory` also injected `ObjectMapper`. Because `spring-boot-starter-webmvc` was originally without `spring-boot-starter-json`, and Spring Boot 4's modular starter setup did not register a global `com.fasterxml.jackson.databind.ObjectMapper` bean in the application context by default.
   - **Fix:** Added `@Bean @ConditionalOnMissingBean public ObjectMapper objectMapper() { return new ObjectMapper(); }` to `RestClientConfig`.

---

### b) Why Unit Tests (77 Passing) Did Not Catch It

The existing 77 tests were all slice or pure unit tests:
- Controller tests (`AiEndpointsTest`, `IdeaAiFeaturesTest`, `SessionAiFeaturesTest`) used `@WebMvcTest` with `@TestConfiguration` classes that explicitly supplied mock beans or instantiated dependencies.
- Component tests (`ModelRouterTest`, `QuotaServiceTest`, `StructuredOutputHelperTest`, `OpenAiCompatibleClientTest`) were standalone JUnit tests creating instances with standard Java `new` operators.
- **There was no full `@SpringBootTest` context loading test (`AiServiceApplicationTests`).** Therefore, complete end-to-end Spring dependency injection wiring across production `@Configuration` classes was never validated during the Maven build phase, allowing runtime bean definition omissions to slip through to Docker execution.

---

### c) Secret Scan Finding in OpenAiCompatibleClientTest.java

- **Matched Pattern:** `g-s-k_` (Groq API key prefix)
- **File:** `ai-service/src/test/java/com/mindspark/ai/provider/client/OpenAiCompatibleClientTest.java:40`
- **Line Content:** `private static final String API_KEY = "[test-fixture-key]";`
- **Nature:** It was a **pure test fixture string** used to verify that the client adds the `Authorization: Bearer <key>` header to the MockRestServiceServer request. It was NOT a real key.
- **Resolution:** To prevent false positives from security auditing tools scanning for Groq key prefixes, the fixture was renamed to `test-mock-secret-key-12345`.

---

### d) Purpose and Details of .env.example Edits

- **Reason for Edit:** To document the configuration keys required for running the end-to-end test script `scripts/test-ai.ps1` without exposing actual values.
- **Changes Made:** Appended empty variable declarations:
  ```env
  KC_TEST_CLIENT_ID=
  TEST_FREE_PASSWORD=
  TEST_PREMIUM_PASSWORD=
  ```
- No secret values or sensitive data were placed in `.env.example`.

---

### e) Container Recreation Audit (Docker Inspect Proof)

Execution output of `docker inspect`:
```
/mindspark-backend-1 created=2026-11-19T16:24:20.696074396Z started=2026-11-19T16:24:32.134647874Z
/mindspark-keycloak-1 created=2026-11-19T14:50:42.299814709Z started=2026-11-19T15:13:31.565104358Z
/mindspark-ai-db-1 created=2026-11-19T14:50:42.050635687Z started=2026-11-19T14:50:42.587789398Z
/mindspark-api-gateway-1 created=2026-11-19T16:24:25.401561517Z started=2026-11-19T16:24:32.776335388Z
/mindspark-ai-service-1 created=2026-11-19T16:30:51.561320148Z started=2026-11-19T16:30:52.819915723Z
```

- **`mindspark-keycloak-1`:** Created at 14:50:42Z. **NOT recreated.**
- **`mindspark-ai-db-1`:** Created at 14:50:42Z. **NOT recreated.**
- **`mindspark-backend-1`:** Created at 16:24:20Z. **YES, it was recreated.**
  - **Why:** In Phase 8, `docker compose up -d --build ai-service api-gateway` was executed without `--no-deps`. In `docker-compose.yml`, `api-gateway` has `depends_on: [discovery-server, backend]`. Docker Compose resolved the dependency graph and recreated `backend` and `discovery-server` alongside `api-gateway`.
  - **Correction:** Future container restarts must strictly use `--no-deps`:
    `docker compose up -d --build --no-deps ai-service`
