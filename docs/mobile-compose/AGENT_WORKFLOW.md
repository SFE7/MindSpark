# Antigravity Workflow Instructions — MindSpark Jetpack Compose Client

You are implementing the MindSpark Android client in `mobile-compose/` inside the `SFE7/MindSpark` monorepo. Follow this workflow strictly for every task.

---

## 1. Sources of Truth (Read First)

1. **Implementation Plan:** `docs/mobile-compose/IMPLEMENTATION_PLAN.md`
2. **API & Protocol Contracts:** `docs/mobile-compose/contracts.md` (created in Task 2)
3. **Actual Monorepo Code:** Any backend/frontend source file explicitly named in the task (e.g., `RoomController`, `ApiModels.java`, `docs/REALTIME.md`, `drawing-hit-test.ts`, `workspace-canvas.ts`, `idea-position-guard.ts`).

> **Never modify the backend.** Consume its REST and WebSocket APIs as-is.
> **Never guess JSON fields, paths, or rules.** Verify them in `contracts.md` or the named source files.

---

## 2. Core Operating Rules

* **One task per conversation.** Only implement the single task requested by the human. Do not jump ahead to future tasks.
* **Context efficiency:** Unless you are reviewing the whole plan, read only the plan's header sections (**Code Level Rules**, **Mobile Design Mapping**, **Global Constraints**, **Compose and Android traps**, **Execution notes**) plus **the single Task N you are implementing**.
* **Respect the Human Setup:** `mobile-compose/` is already created in Android Studio as an Empty Activity project (with `local.properties`), and Task 0 (LAN IP / firewall / Keycloak) is already done. Do not recreate the project from scratch.
* **Strict Code Level (Android Basics with Compose — Units 1–5 ONLY):**
  * Allowed: basic Kotlin (`val`/`var`, `when`, `data class`, `enum class`, `sealed interface`, `map`/`filter`/`find`), standard Material 3 Compose widgets, `ViewModel` + `MutableStateFlow`/`StateFlow` + `viewModelFactory`, Retrofit + `kotlinx.serialization`, and `AppContainer`.
  * **Forbidden:** Hilt/Koin, `Room`, Paging, `Channel`, `SharedFlow`, Flow operators beyond `map`, custom `Layout`, reflection, DSLs, operator overloading, extension-function tricks, or mocking libraries (use fake repositories in tests).
  * **Exceptions** are allowed *only* as listed in the plan's Exceptions table (`Canvas`/`pointerInput`/`graphicsLayer`, `AppAuth`, OkHttp `WebSocket`/`Authenticator`/`@Synchronized`, `SharedPreferences`, `ModalBottomSheet`/`LifecycleEventObserver`, `LocalClipboardManager`/`LocalHapticFeedback`/`BuildConfig`, `MockWebServer`). Ignore deprecation warnings on `LocalClipboardManager` rather than rewriting around newer `LocalClipboard` APIs.
* **Pure JVM Unit Tests:** Never use `android.net.Uri` or `android.util.Log` in pure Kotlin files (`EnvConfig`, `logic/`). Use `java.net.URLEncoder` and standard Kotlin/Java types so `./gradlew testDebugUnitTest` runs cleanly on the JVM.
* **Honor All Traps & Guardrails in the Plan:**
  * Follow every rule in **Compose and Android traps** (`requiredSize`, `pointerInput(tool)` + `rememberUpdatedState`, separate chained `.pointerInput(tool)` for tap + drag, `localDeltaToWorld` vs `screenDeltaToWorld`, `appScope` in `onCleared()`, idempotent `startLoops()`/`stopLoops()`, `tombstones.unmark(id)` on redo, `ApiErrors`, etc.).
  * Also ensure:
    1. `IdeaCard` loops inside the canvas are wrapped in `key(idea.id)` with `idea` read via `rememberUpdatedState`.
    2. Auth/Room-exit navigation uses `popUpTo(...) { inclusive = true }` so hardware Back cannot return to a signed-out screen or exited room.
    3. `AppAuth` `AuthorizationService` is either scoped cleanly in `AppContainer` or disposed in `DisposableEffect` to prevent `ServiceConnectionLeaked`.

---

## 3. Two-Tier Task Execution

Follow the tier that matches the task number you are assigned:

### Tier A — Standard Tasks (`1, 4, 5, 6, 7, 8, 11, 12, 15, 18`)
The implementation plan already specifies the exact classes, signatures, and tests. **Do not create extra design documents.** Go straight into the TDD loop:
1. Read the plan header sections + your Task N + relevant sections of `contracts.md` (and any source file explicitly named in the task, such as `drawing-hit-test.ts` in Task 12).
2. Write the failing unit/UI tests specified in the task first.
3. Run the tests and confirm they **FAIL**.
4. Implement the simplest possible code (Units 1–5 style, ~30 lines max per function).
5. Run the tests and build checks until they **PASS**.

### Tier B — High-Risk / Complex Tasks (`2, 3, 9, 10, 13, 14, 16, 17`)
Before modifying any Kotlin code, **STOP and present a short Pre-Code Check in chat** (do *not* create throwaway doc files—except Task 2, which creates `contracts.md`):

* **For Task 2 (`contracts.md` + fixtures):**
  1. Read the backend controllers, `ApiModels.java`, `RoomWebSocketHandler`, `docs/REALTIME.md`, and the Angular `workspace-canvas.ts`, `drawing-hit-test.ts`, `idea-position-guard.ts`, `drawing-position-guard.ts`, and `realtime.service.ts`.
  2. Write `docs/mobile-compose/contracts.md` (including presence TTL/expiry and drawing hit-test/simplify rules) and the JSON fixtures in `app/src/test/resources/fixtures/`.
  3. Report any differences found vs. Appendix A, run `FixturesTest`, and stop for human verification before Task 3 begins.

* **For Tasks `3, 9, 10, 13, 14, 16, 17` (Auth/AppAuth, Canvas, Dragging, Drawing/Gestures, WsService, Realtime Loops, Governance):**
  1. **Source check:** List the exact repo/contract files you read and quote the key endpoints, numbers, or rules (e.g., AppAuth HTTP `ConnectionBuilder` + redirect scheme, clamp bounds, hit-test tolerances, WS close codes/backoff, throttle intervals, presence expiry, close/open endpoints).
  2. **Trap & Gesture check:** Summarize in 5–10 bullet points how you will handle threads, lifecycle, gesture dispatch (`pointerInput(tool)` + tap vs drag for one-point pen strokes), coordinate math, and Units 1–5 `ViewModel`/`StateFlow` wiring without breaking **Compose and Android traps**.
  3. **File plan:** List the exact files you will create or modify.
  4. **Wait for the human to reply "Go" / "Approved"** before writing tests or code.

---

## 4. Verification & Definition of Done (Every Task)

Run commands from `mobile-compose/` (ensure `JAVA_HOME` is set in the shell if Gradle fails to find a JDK):

1. **Tests pass:** Run `./gradlew testDebugUnitTest` (targeted test first, then full unit suite).
2. **Build passes:** On every task that touches Android/UI code, run `./gradlew assembleDebug` (with `-PhostIp=<ip>` if `hostIp` is not in `~/.gradle/gradle.properties`).
3. **Android UI tests (Tasks 7, 9, 11, 17, 18):** Write the Compose UI test under `app/src/androidTest/` and ask the human to run `./gradlew connectedDebugAndroidTest` on their connected phone.
4. **No silent guessing:** Tick `- [x]` on plan steps **only** after showing the actual passing command output.
5. **Checkpoints (`1`–`8`):** If the task ends with a **Checkpoint**, list clearly what the human should test on their phone (against the web app) and **STOP**. Do not commit or move on until the human confirms it works.
6. **Commit:** Once all checks/checkpoints pass, make the exact git commit listed at the end of the task (or ask the human to run it).

---

## 5. Starter Prompts for the Human (Copy & Paste per Chat)

> Always start a **fresh Antigravity chat** for each task.

### For Standard Tasks (`1, 4, 5, 6, 7, 8, 11, 12, 15, 18`)
```text
Read `docs/mobile-compose/AGENT_WORKFLOW.md`, `docs/mobile-compose/contracts.md`, and from `docs/mobile-compose/IMPLEMENTATION_PLAN.md` read the header rules/traps plus Task <NUMBER> only.
Implement Task <NUMBER> strictly following Tier A (TDD first, Units 1–5 basic Kotlin/Compose only, obey all Compose/Android traps).
Run `./gradlew testDebugUnitTest` and `./gradlew assembleDebug` when done and show the output.
```

### For Complex Tasks (`2, 3, 9, 10, 13, 14, 16, 17`)
```text
Read `docs/mobile-compose/AGENT_WORKFLOW.md`, `docs/mobile-compose/contracts.md`, and from `docs/mobile-compose/IMPLEMENTATION_PLAN.md` read the header rules/traps plus Task <NUMBER> only.
We are starting Task <NUMBER>. Follow Tier B: read the relevant source/contract files first and output ONLY your short Pre-Code Check (source rules, trap/gesture check, and file list). Wait for my approval before writing any code.
```

### For Milestone Code Reviews (Fresh Chat After Tasks `5`, `10`, `13`, `17`)
```text
Read `docs/mobile-compose/AGENT_WORKFLOW.md` and the Code Level Rules + Compose/Android traps in `docs/mobile-compose/IMPLEMENTATION_PLAN.md`.
Review the Kotlin code written so far in `mobile-compose/app/src/main/` (focusing on Tasks <RANGE>).
List ONLY real bugs, violations of Units 1–5 rules (e.g., forbidden Flow operators, missing Factories/keys, wrong coordinate scaling, gesture/coroutine leaks), or missing edge cases. Do not suggest style rewrites or advanced patterns.
```