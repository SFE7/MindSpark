# Source Index and Evidence Grounding — Chapter 2 (State of the Art)

**Project:** MindSpark — Design and Implementation of an AI-Powered Collaborative Brainstorming Platform  
**Academic Institution:** ISET Bizerte, Tunisia  
**Date of Audit & Compilation:** November 2026  

---

## 1. Academic & Theoretical Sources

### 1. Divergent and Convergent Thinking
- **Citation Key:** `\cite{guilford1956structure}`
- **Title:** The Structure of Intellect
- **Author:** Joy Paul Guilford (1956), *Psychological Bulletin*, Vol. 53, No. 4, pp. 267–293.
- **Claims Supported:** Dual-process cognitive creativity theory: operational separation between divergent thinking (fluency, flexibility, novelty) and convergent thinking (evaluative synthesis, categorization, selection).

### 2. Classical Brainstorming Principles
- **Citation Key:** `\cite{osborn1953applied}`
- **Title:** Applied Imagination: Principles and Procedures of Creative Problem-Solving
- **Author:** Alex F. Osborn (1953), Charles Scribner's Sons, New York.
- **Claims Supported:** Four foundational rules of brainstorming: defer judgment, strive for quantity, encourage wild ideas, and combine/improve existing ideas.

### 3. Cognitive Biases and Productivity Losses in Groups
- **Citation Keys:** `\cite{diehl1987productivity}`, `\cite{mullen1991productivity}`
- **Authors:** Michael Diehl & Wolfgang Stroebe (1987), *Journal of Personality and Social Psychology*; Brian Mullen et al. (1991), *Basic and Applied Social Psychology*.
- **Claims Supported:** Production blocking, evaluation apprehension, and free-riding (social loafing) as root causes of group brainstorming productivity losses compared to nominal groups.

### 4. Anchoring and Cognitive Heuristics
- **Citation Key:** `\cite{tversky1974judgment}`
- **Title:** Judgment under Uncertainty: Heuristics and Biases
- **Authors:** Amos Tversky & Daniel Kahneman (1974), *Science*, Vol. 185, No. 4157, pp. 1124–1131.
- **Claims Supported:** Early visible ideas or prominent participant suggestions act as cognitive anchors that disproportionately bias and restrict subsequent group ideation.

### 5. Social Loafing & Anonymity in Computer-Supported Collaboration
- **Citation Keys:** `\cite{karau1993social}`, `\cite{connolly1990idea}`
- **Authors:** Steven J. Karau & Kipling D. Williams (1993), *JPSP*; Terry Connolly, Leonard M. Jessup, & Joseph S. Valacich (1990), *Small Group Research*.
- **Claims Supported:** Social loafing mechanisms in collaborative settings; how digital anonymity and computer-mediated idea generation attenuate evaluation apprehension and enhance contribution volume.

### 6. Real-Time Communication Protocols & WebSockets
- **Citation Key:** `\cite{rfc6455}`
- **Title:** The WebSocket Protocol (RFC 6455)
- **Authors:** Ian Fette & Alexey Melnikov (2011), Internet Engineering Task Force (IETF).
- **URL:** https://www.rfc-editor.org/rfc/rfc6455.txt
- **Claims Supported:** Full-duplex bidirectional persistent TCP channel initiated via HTTP Upgrade handshake; frame-level low-overhead communication (2 to 10 bytes overhead per frame).

### 7. Server-Sent Events (SSE)
- **Citation Key:** `\cite{whatwg_sse}`
- **Title:** HTML Standard: Server-Sent Events
- **Author:** Web Hypertext Application Technology Working Group (WHATWG).
- **URL:** https://html.spec.whatwg.org/multipage/server-sent-events.html
- **Access Date:** November 2026
- **Claims Supported:** Monidirectional text stream (`text/event-stream`) over persistent HTTP/1.1 or HTTP/2 connections; built-in browser reconnection mechanisms.

### 8. Transaction-Bound Event Broadcasting
- **Citation Key:** `\cite{spring_framework_doc}`
- **Title:** Spring Framework Documentation: WebSockets and Transaction-Bound Events
- **Author:** Broadcom / Spring Team.
- **URL:** https://docs.spring.io/spring-framework/reference/web/websocket.html
- **Access Date:** November 2026
- **Claims Supported:** Application event decoupling via `@TransactionalEventListener(phase = AFTER_COMMIT)` ensuring events are dispatched across brokers or sockets only after database transactions successfully commit, preventing race conditions with uncommitted persistence states.

### 9. Transformer Architecture & Attention Mechanisms
- **Citation Key:** `\cite{vaswani2017attention}`
- **Title:** Attention Is All You Need
- **Authors:** Ashish Vaswani et al. (2017), *NeurIPS 2017*, pp. 5998–6008.
- **Claims Supported:** Self-attention mechanisms replacing recurrence/convolution; multi-head attention allowing parallelized sequence modeling.

### 10. Large Language Models & Few-Shot In-Context Learning
- **Citation Keys:** `\cite{devlin2019bert}`, `\cite{brown2020language}`
- **Authors:** Jacob Devlin et al. (2019), *NAACL*; Tom Brown et al. (2020), *NeurIPS*.
- **Claims Supported:** Autoregressive language modeling, tokenization concepts, context windows, and emergent zero-shot/few-shot problem solving capabilities.

### 11. Dense Embeddings & Semantic Clustering Algorithms
- **Citation Keys:** `\cite{reimers2019sentencebert}`, `\cite{ester1996density}`
- **Authors:** Nils Reimers & Iryna Gurevych (2019), *EMNLP*; Martin Ester, Hans-Peter Kriegel, Jörg Sander, & Xiaowei Xu (1996), *KDD-96*.
- **Claims Supported:** Mapping textual concepts to high-dimensional dense vector spaces (Sentence-BERT); spatial density-based clustering (DBSCAN) without requiring a predefined cluster count $k$.

### 12. Prompt Engineering, Reasoning, and Structured Outputs
- **Citation Keys:** `\cite{wei2022chain}`, `\cite{json_schema_spec}`, `\cite{ji2023survey}`
- **Authors:** Jason Wei et al. (2022), *NeurIPS*; Austin Wright et al. (2020), IETF Draft; Ziwei Ji et al. (2023), *ACM Computing Surveys*.
- **Claims Supported:** Chain-of-thought prompting paradigms; JSON schema-constrained decoding; taxonomy of LLM hallucinations (intrinsic vs. extrinsic) and output verification protocols.

### 13. Cloud AI Provider Documentation
- **Citation Key:** `\cite{google_gemini_models}`
- **Title:** Gemini Models Documentation: Flash and Flash-Lite Model Families
- **Author:** Google Cloud.
- **URL:** https://cloud.google.com/vertex-ai/generative-ai/docs/learn/models
- **Access Date:** November 2026
- **Claims Supported:** `gemini-3.5-flash-lite` and `gemini-3.1-flash-lite` model availability, latency optimization, and subagent routing.
- **Citation Key:** `\cite{groq_models_docs}`
- **Title:** Groq Supported Models and LPU Inference Engine Documentation
- **Author:** Groq Inc.
- **URL:** https://groq.com/docs/models
- **Access Date:** November 2026
- **Claims Supported:** `openai/gpt-oss-120b` (120B MoE) and `qwen/qwen3.8-27b` (27B dense vision-language model) availability on Groq LPU hardware with high token throughput.

---

## 2. Commercial Platform Documentation & Pricing Verification

| Platform | Verification URL | Access Date | Claims & Verified Pricing Supported |
| :--- | :--- | :--- | :--- |
| **Miro** | https://miro.com/pricing/ | November 2026 | **Free Plan:** \$0 (3 editable boards, 10 Miro AI credits/mo).<br>**Starter Plan:** \$8/member/mo billed annually (\$10/member/mo billed monthly).<br>**Business Plan:** **\$20/member/mo billed annually (\$25/member/mo billed monthly)** (adds SSO, unlimited private workspaces/guests, advanced AI workflows, 50 AI credits/member/mo).<br>**Enterprise Plan:** Custom pricing.<br>*Features:* Multi-user live cursors on all plans; built-in voting app available on Starter and higher; AI clustering/summarization governed by AI credit limits. |
| **Mural** | https://mural.co/pricing | November 2026 | **Free Plan:** \$0 (3 active murals, unlimited members, built-in voting).<br>**Team+ Plan:** \$9.99/user/mo billed annually (\$12.00/user/mo billed monthly), adding Mural AI and private rooms.<br>**Business Plan:** \$17.99/user/mo billed annually (unlimited external guests, SAML SSO).<br>**Enterprise Plan:** Custom pricing.<br>*Features:* Live cursors and facilitation voting available across plans; AI summarization gated on paid tiers. |
| **Klaxoon** | https://klaxoon.com/pricing | November 2026 | **Free Plan:** \$0 (**1 open activity at a time**, max **5 participants** per activity, monthly audience limit of 50 unique contributors, 50 ideas per board).<br>**Starter Plan:** \$24.90/host/mo billed annually (unlimited open activities, up to 20 participants per activity).<br>**Enterprise Plan:** Custom pricing (up to 1,000 participants).<br>*Features:* Integrated dot-voting, Board Setup AI Wizard, and automated action plan generation into Jira/Wrike. |
| **FigJam / Figma** | https://www.figma.com/pricing/ | November 2026 | **Starter Plan:** \$0 (up to 3 collaborative FigJam files).<br>**Figma Collab Seat (for pure FigJam/reviewers):** **\$3/seat/mo** on Professional; **\$5/seat/mo** on Organization and Enterprise (billed annually).<br>**Full Design Seat (bundled FigJam + Figma Design):** \$16/seat/mo on Professional, \$55 on Organization, \$90 on Enterprise (billed annually).<br>*Features:* Multi-user live cursors, cursor chat, native voting sessions with hidden ballots, and FigJam AI clustering. |

---

## 3. MindSpark Codebase Audit Claims & Evidence Grounding

All claims regarding the current state of MindSpark are grounded strictly in the repository files audited in November 2026:

1. **Service Discovery & API Gateway:**
   - Eureka Discovery Server: `discovery-server/pom.xml`, port 8761.
   - Spring Cloud Gateway: `api-gateway/src/main/resources/application.yml`, routes `/ws/**` and `/api/**` to `lb://mindspark-backend`.
2. **Backend Architecture & Security:**
   - Spring Boot 4.1.1, Java 21: `backend/pom.xml`.
   - Keycloak 26.2.4 OAuth2 Resource Server via asymmetric RS256 JWKS: `backend/src/main/java/com/sme/maintenance/security/SecurityConfig.java`, `docs/AUTH.md`.
3. **Real-Time WebSocket Protocol & Soft-Locking:**
   - WebSocket Handler: `backend/src/main/java/com/sme/maintenance/websocket/RoomWebSocketHandler.java`.
   - Handshake Interceptor: `backend/src/main/java/com/sme/maintenance/websocket/RoomHandshakeInterceptor.java`.
   - Event Listener: `backend/src/main/java/com/sme/maintenance/websocket/RoomBroadcastEventListener.java` (`@TransactionalEventListener(phase = AFTER_COMMIT)`).
   - Rate limiting: 25/s for cursors, 15/s for card movements.
   - Soft object lock: 3-second sliding window (`heldObjects`).
   - Coordinate normalization: Clamped to $X \in [0, 1000]$, $Y \in [0, 500]$.
4. **Presence Management & Sweeper:**
   - Heartbeat & multi-client tracking: `backend/src/main/java/com/sme/maintenance/presence/PresenceService.java`.
   - Background Sweeper: `backend/src/main/java/com/sme/maintenance/presence/PresenceSweeper.java` (`@Scheduled(fixedDelay = 5000)`).
5. **Ideation, Comments, Categories, Drawings & Voting:**
   - Entities: `Idea.java`, `Comment.java`, `Category.java`, `Drawing.java`, `Vote.java`.
   - Current Non-anonymous voting: `Vote.java` uniquely binds `(idea_id, user_id)` in database rows.
6. **AI Service & SaaS Status:**
   - Inspected codebase: Zero AI dependencies, zero `ai-service` directory, zero billing/multi-tenancy SDKs.
   - All AI routing (`gemini-3.5-flash-lite`, `gpt-oss-120b`), failover, daily quota databases, and anonymous vote hashing are recorded as **confirmed team decisions and planned specifications**, not existing code.
