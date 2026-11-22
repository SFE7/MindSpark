# Sprint 3 Readiness Checklist: Platform Hardening & Verification

**Status:** ALL CHECKS PASSED (Sprint 3 Complete)  
**Date:** November 21, 2026  
**Auditor:** Antigravity AI Assistant  

---

## 1. Executive Summary

All stories and tasks designated for Sprint 3 (Phase 1 AI Service, Phase 2 Admin & Subscription, Phase 3 Session Service, Phase 5 Media Service & Vote Pools, and Phase 6 Multi-PC WebSockets + T6 Automated Tests + T7 Hardening) are completed, verified across all layers, and hardened for production deployment.

---

## 2. Platform Architecture & Service Inventory

| Service Name | Port | Base Tech / Framework | Heap Limit | Responsibilities | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **API Gateway** | 8080 | Spring Cloud Gateway (Boot 4.1.1) | `-Xmx192m` | Reverse proxy, CORS, LAN origin reflection, security headers, WebSocket upgrade | **HEALTHY (UP)** |
| **Backend** | 8082 | Spring Boot 4.1.1 / Spring Data JPA | `-Xmx256m` | Core room domain, idea cards, live presence, real-time WebSockets, vote pools, canvas images | **HEALTHY (UP)** |
| **AI Service** | 8081 | Spring Boot 4.1.1 / Jackson 3 | `-Xmx256m` | Gemini & Groq multi-provider fallback, concurrency limiting, user tier & quota enforcement | **HEALTHY (UP)** |
| **Subscription Service** | 8083 | Spring Boot 4.1.1 / PostgreSQL | `-Xmx192m` | Plans, tiers, entitlement verification, admin subscriptions & billing history | **HEALTHY (UP)** |
| **Session Service** | 8084 | Spring Boot 4.1.1 / PostgreSQL | `-Xmx256m` | Room snapshotting, audit logs, multi-format export (.md, .json, .csv) | **HEALTHY (UP)** |
| **Media Service** | 8085 | Spring Boot 4.1.1 / Docker Volume | `-Xmx192m` | Strict binary image upload (magic bytes), polyglot protection, room media streaming | **HEALTHY (UP)** |
| **Discovery Server** | 8761 | Spring Cloud Netflix Eureka | `-Xmx192m` | Dynamic microservice registration and health monitoring | **HEALTHY (UP)** |
| **Frontend** | 4200 | Angular 19 / Nginx Dev Server | N/A | Whiteboard canvas, real-time collaboration, billing, admin, snapshots, media tools | **HEALTHY (UP)** |
| **Keycloak** | 8180 | Keycloak 26.4.0 / PostgreSQL | N/A | Centralized IAM, JWT issuance, realm roles (ADMIN, SUPER_ADMIN) | **HEALTHY (UP)** |

---

## 3. Automated Test Suite Results (Part C / T6)

All 6 project test suites pass with **0 failures and 0 errors**:

| Component / Test Suite | Tests Run | Passed | Failures | Errors | Skipped | Proof Log File |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **ai-service** (Unit & Integration) | 164 | 159 | 0 | 0 | 5 | `docs/proof/s3p6-ai-test.txt` |
| **subscription-service** (Unit) | 31 | 31 | 0 | 0 | 0 | `docs/proof/s3p6-sub-test.txt` |
| **subscription-service** (PostgreSQL Concurrency IT) | 1 | 1 | 0 | 0 | 0 | `docs/proof/s3p6-sub-postgres-it.txt` |
| **session-service** (Unit & Lifecycle IT) | 21 | 21 | 0 | 0 | 0 | `docs/proof/s3p6-session-test.txt` |
| **media-service** (Security & Validation IT) | 14 | 14 | 0 | 0 | 0 | `docs/proof/s3p6-media-test.txt` |
| **backend** (Full Suite + Multi-Client WS IT) | 90 | 89 | 0 | 0 | 1* | `docs/proof/s3p6-backend-test.txt` |
| **frontend** (Vitest unit tests) | 121 | 121 | 0 | 0 | 0 | `docs/proof/s3p6-fe-test.txt` |
| **frontend** (Production Build) | N/A | Complete | 0 | 0 | 0 | `docs/proof/s3p6-fe-build.txt` |

*\* Backend skipped test: `UserStoryAuditTests.testPaginationSupport` is intentionally `@Disabled` because room idea listings intentionally return full canvas snapshots.*

---

## 4. Live End-to-End Verification (`scripts/verify-critical-flows.ps1`)

**Executed:** Live against active Docker cluster on port 8080 (API Gateway).  
**Result:** **36 / 36 tests PASSED (0 failures)**.  
**Proof Log:** `docs/proof/s3p6-e2e-live.txt`

### Flow Verification Summary:
1. **Health Checks:** Gateway, Backend, AI, Subscription, Session, Media (All 200 OK).
2. **Gateway Security:**
   - `/internal/**` returns 404 (internal microservice endpoints completely blocked from public routing).
   - Dynamic LAN CORS origin reflection for `http://192.168.0.3:4200`.
   - Security header `X-Content-Type-Options: nosniff`.
   - Response header deduplication (no duplicate `Access-Control-Expose-Headers`).
3. **Multi-PC Real-Time WebSockets:**
   - Live WebSocket upgrade through API Gateway to Backend with LAN Origin `http://192.168.0.3:4200`.
   - Presence tracking, participant snapshots, and real-time broadcasting.
4. **Room Domain & Canvas Collaboration:**
   - User synchronization via JWT claims.
   - Room creation, join by code, idea creation, live move/drag coordinates, normal voting.
5. **Subscription & Billing:**
   - Public plans catalog, active subscription status (`/api/subscriptions/me`), billing history.
6. **Session Service:**
   - Session snapshot creation, retrieval, and multi-format export (`.md`, `.json`, `.csv`).
7. **Media Service:**
   - Valid 1x1 PNG upload and binary streaming with JWT.
   - Strict binary magic byte validation rejecting invalid SVG/HTML files (`UNSUPPORTED_MEDIA_TYPE`).
   - Whiteboard canvas image attachment and coordinate repositioning (`PATCH`).
8. **Vote Pools:**
   - Room creator vote pool creation, participant vote casting, pool closure, and results tally.
9. **Data Cleanup:**
   - Deleted canvas image and idea cards after verification.

---

## 5. Platform Hardening Checklist (Part B / T7)

- [x] **Gateway Header Deduplication:** Added `Access-Control-Expose-Headers` to `DedupeResponseHeader` (`RETAIN_FIRST`).
- [x] **Gateway Surface Lockdown:** Actuator exposure locked down to `health,info` (removed `gateway` management endpoint).
- [x] **Global AI Concurrency Limiter:** Configured `Semaphore(8)` in `ai-service` with fast-fail `429 PROVIDER_BUSY` and daily quota refunds on capacity exhaustion.
- [x] **Internal Secret Startup Check:** `ApplicationRunner` beans added to `subscription-service`, `session-service`, `media-service`, and `backend` logging explicit `WARN` when the default development secret is in use.
- [x] **Container Memory Hygiene:** JVM `-Xmx` heap limits configured across all services in `docker-compose.yml` (`discovery-server: 192m`, `api-gateway: 192m`, `ai-service: 256m`, `subscription-service: 192m`, `session-service: 256m`, `media-service: 192m`, `backend: 256m`).
- [x] **Zero Leaked Secrets:** Automated regex scan confirmed 0 exposed API keys, tokens, or credentials in codebase and proof artifacts (`docs/proof/s3p6-secret-scan.txt`).
- [x] **Hard Constraints Honored:** `.env` and `infra/auth/import/mindspark-realm.json` remain pristine and unmodified; no database volumes recreated.
