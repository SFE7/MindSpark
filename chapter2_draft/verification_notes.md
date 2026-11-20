# Verification Notes & Implementation Tracking — Chapter 2

This document tracks technical decisions, architectural confirmations, and the implementation status of features in the **MindSpark** platform.

---

## 1. Confirmed Decisions Recorded in Chapter 2

The development team has confirmed the following architectural and functional decisions, which have been integrated into Chapter 2 as **formal design specifications and target requirements**:

1. **AI Microservice Architecture (`ai-service`):**
   - **Decision:** A dedicated Spring Boot microservice named `ai-service`, registered with Eureka Discovery Server and accessible through the existing Spring Cloud API Gateway (`lb://ai-service`).
   - **Implementation Rationale:** Preserves full technology stack homogeneity (Spring Boot 4 / Java 21 across the backend), leverages existing Keycloak JWT filters, avoids polyglot operations (no Python/FastAPI), and provides low-overhead control over HTTP timeouts, routing, and schema validation.
   - **Client Choice:** A lightweight, custom OpenAI-compatible HTTP client (avoiding heavyweight dependencies on Spring AI or third-party wrappers).

2. **Multi-Tier AI Provider & Model Strategy:**
   - **Free Tier (Google Gemini via OpenAI-compatible endpoint):**
     - Primary Model: `gemini-3.5-flash-lite` (low-latency, cost-effective multimodal model for agentic routing and structured drafting).
     - Fallback Model: `gemini-3.1-flash-lite` (latency-sensitive fallback).
   - **Premium Tier (Groq LPU Inference):**
     - Primary Model: `openai/gpt-oss-120b` (120B Mixture-of-Experts model for advanced reasoning and synthesis).
     - Fallback Model: `qwen/qwen3.8-27b` (27B dense vision-language model).
   - **Performance Metric:** The team reports an internal target latency of approximately 0.8 seconds on Groq LPU inference. In Chapter 2, this is formally attributed as a **team-reported measurement**, not an independently audited benchmark.

3. **Anonymous Voting Requirement:**
   - **Requirement:** Voting must be completely anonymous, preventing public attribution and database-level direct identity linkage, while permitting users to retract their own votes.
   - **Status in Code:** **Not yet implemented.** The current database constraint `uk_vote_idea_user` explicitly joins `user_id` to `idea_id` in plain text.
   - **Target Architectural Nuance:** Salted hashing alone ($H(\text{user\_id} \parallel \text{room\_salt})$) does not guarantee complete anonymity if the server retains access to the salt and user records, or in small participation groups where correlation attacks are feasible. The target design requires separating ballot persistence from eligibility verification (e.g., blinded tokens or cryptographic ballot isolation).

---

## 2. Status Breakdown: Implemented vs. Planned

| Feature / Architectural Component | Status in Codebase | Verification Location / Code Evidence |
| :--- | :--- | :--- |
| **Spring Cloud API Gateway & Eureka** | **Verified Implemented** | `discovery-server/`, `api-gateway/src/main/resources/application.yml` |
| **PostgreSQL 16 & Spring Data JPA** | **Verified Implemented** | `backend/pom.xml`, `docker-compose.yml` |
| **Keycloak 26.2.4 OAuth2 Resource Server** | **Verified Implemented** | `backend/src/main/java/com/sme/maintenance/security/SecurityConfig.java` |
| **WebSocket Protocol & Rate Limiting** | **Verified Implemented** | `RoomWebSocketHandler.java` (25/s cursors, 15/s cards, 20/s strokes) |
| **Soft Concurrency Locking (3s TTL)** | **Verified Implemented** | `RoomWebSocketHandler.java` (`heldObjects` map) |
| **Presence Sweeping (5s fixedDelay)** | **Verified Implemented** | `PresenceSweeper.java` (60s expiry + 10s grace) |
| **Normalized Coordinates ($0..1000 \times 0..500$)**| **Verified Implemented** | `RoomWebSocketHandler.java`, `idea-position-guard.ts` |
| **Transaction-Safe Event Dispatching** | **Verified Implemented** | `RoomBroadcastEventListener.java` (`AFTER_COMMIT`) |
| **Three.js & 2D Vector Drawing Canvas** | **Verified Implemented** | `Drawing.java`, `DrawingService.java`, `workspace-canvas.ts` |
| **Threaded Comments & Categories** | **Verified Implemented** | `Comment.java`, `Category.java`, `Idea.java` |
| **Non-Anonymous Voting (Direct `user_id`)** | **Verified Implemented** | `Vote.java`, `VoteService.java` (`uk_vote_idea_user`) |
| **Cryptographic Anonymous Voting** | **Planned / Target Requirement** | Not in repository. Detailed as target architecture in Section 2.2.1/2.2.3. |
| **`ai-service` Spring Boot Microservice** | **Planned / Selected Architecture** | Not in repository. Detailed as confirmed architecture in Section 2.3.2. |
| **Multi-Tier Model Routing (Gemini & Groq)** | **Planned / Selected Architecture** | Not in repository. Documented in Section 2.3.2. |
| **Daily AI Quota Database & Rate Limiter** | **Planned / Selected Architecture** | Not in repository. Documented in Section 2.3.2/2.4. |
| **SaaS Multi-Tenancy & Billing (Stripe)** | **Planned / Future Work** | Not in repository. Single-tenant prototype documented in Section 2.4. |

---

## 3. Remaining Questions for the Team

The following questions represent genuinely unresolved decisions for subsequent chapters:

1. **`ai-service` Repository Timeline:**
   - What is the scheduled timeline for committing the `ai-service` microservice into the master repository, and will its quota PostgreSQL database be launched as a secondary database container in `docker-compose.yml`?
2. **Anonymous Ballot Architecture Selection:**
   - For decoupling ballot casting from identity, does the team plan to use client-side blinded tokens (issued by the room upon joining) or an isolated vote ledger table stripped of user identifiers?
3. **Repository Licensing:**
   - Which open-source license (e.g., MIT, Apache 2.0, or GNU GPLv3) does the team plan to add to the repository prior to the graduation defense?
