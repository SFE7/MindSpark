# MindSpark Jetpack Compose Android Client Implementation Plan

> **For agentic workers (Antigravity):** Implement this plan task-by-task, in order, one task per agent conversation. Steps use checkbox (`- [ ]`) syntax; tick each one only after its command output matches the stated expectation. Do not start a task before the previous task's tests pass. **Write basic code only** (see "Code Level Rules"): if a simpler construct exists, use it.

**Goal:** Ship a native Kotlin + Jetpack Compose Android client for MindSpark with the same features as the web app and the Flutter client (rooms, whiteboard, ideas, categories, comments, drawing, real-time collaboration) against the existing backend. The UI is redesigned for mobile, not copied from the web layout.

**Architecture:** Single-module Android app in `mobile-compose/` inside the existing `SFE7/MindSpark` monorepo (next to `frontend/` and the Flutter `mobile/`), so the agent can read `docs/REALTIME.md`, the backend controllers and the Angular `drawing-hit-test.ts` locally. Two layers, as taught in Unit 4: a **data layer** (Retrofit services, repositories, WebSocket service) and a **UI layer** (ViewModel + `StateFlow` + composables). Manual dependency container `AppContainer` (as in Unit 5), no DI framework. Pure logic (coordinate math, hit-testing, undo, position guard, realtime reducer, permissions) lives in plain Kotlin files with JUnit tests; composables stay thin. The backend is consumed as-is over REST and a raw WebSocket.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, Navigation Compose, `ViewModel` + `StateFlow`, coroutines, Retrofit + kotlinx.serialization (all from Units 1-5; use the official `com.squareup.retrofit2:converter-kotlinx-serialization`, same version as Retrofit). Beyond the course, kept isolated (see exceptions): OkHttp `WebSocket`, AppAuth-Android, Compose `Canvas` + pointer gestures. Tests: JUnit4, `kotlinx-coroutines-test`, OkHttp `MockWebServer`, a few Compose UI tests.

**Spec:** this file + `docs/mobile-compose/contracts.md` (Task 2). Appendix A lists API and protocol facts already verified from the repo; Task 2 re-verifies them against the backend source.

---

## Code Level Rules (basic Kotlin and Compose)

Source of truth: [Android Basics with Compose](https://developer.android.com/courses/android-basics-compose/course), **Units 1-5 only** (Kotlin basics; Compose layouts, state, buttons; lists, Material theming, animation; navigation, ViewModel, StateFlow, lifecycle; coroutines, Retrofit, repositories, `AppContainer`). Unit 6 (Room, DataStore) and later are **not** used.

**Allowed (what those units teach):**
- Kotlin: `val`/`var`, functions, default arguments, `when`, nullability (`?.`, `?:`, `let`), classes, `data class`, `enum class`, `interface`, `sealed interface` (Unit 5 `UiState` pattern), `object`, lambdas, collection functions (`map`, `filter`, `find`, `sortedBy`, `any`).
- Compose: `Text`, `Image`, `Icon`, `Button`, `IconButton`, `OutlinedTextField`, `Card`, `Column`/`Row`/`Box`, `Modifier`, `LazyColumn`/`LazyRow`, `Scaffold`, `TopAppBar`, `FloatingActionButton`, `AlertDialog`, `FilterChip`, Material 3 theme, `remember`/`mutableStateOf`/`rememberSaveable`, state hoisting, `NavHost`/`NavController`.
- Architecture: `ViewModel` (with a `Factory` built by `viewModelFactory { initializer { ... } }` when it needs arguments, as in Unit 5), `MutableStateFlow`/`StateFlow`, `collectAsState()`, `viewModelScope.launch`, `suspend` functions, repository interface + implementation, `AppContainer`, `sealed interface XUiState { Loading; Success(..); Error }`.
- Tests: JUnit assertions, fake repositories (no mocking libraries), `runTest` for coroutines, one `ViewModel` test per ViewModel (Unit 4 style).

**Not allowed:** Hilt/Koin, Flow operators beyond `map`, `SharedFlow`/`Channel` for events (use a callback or a `StateFlow`), custom `Layout`, reflection, DSLs, generics-heavy helpers, operator overloading, extension-function tricks, `Room`, Paging, multi-module setups.

**Style:** descriptive names; functions about 30 lines or fewer; one composable per concern; state is hoisted (composable gets values and lambdas, ViewModel owns state); a one-line comment above any non-obvious rule (clamp, tolerance, throttle) that cites its source (web file or `docs/REALTIME.md`).

**Exceptions (beyond Units 1-5).** Each is confined to the listed file(s) and uses the smallest example from the official docs.

| Exception | Where | Why |
|---|---|---|
| `Canvas` / `DrawScope`, `graphicsLayer`, `pointerInput` with `detectTapGestures`, `detectDragGestures`, `detectTransformGestures` | `whiteboard/ui/widgets/` | Drawing and pan/zoom cannot be done with Unit 1-5 widgets |
| AppAuth-Android (browser login + PKCE) and `rememberLauncherForActivityResult` to receive its result | `core/auth/LoginLauncher.kt`, `features/auth/LoginScreen.kt` | Keycloak login needs OIDC; hand-written PKCE is riskier |
| `rememberUpdatedState`, `LaunchedEffect` | gesture code in `whiteboard/ui/widgets/`, one-shot navigation in screens | Keeps gesture handlers current without restarting them; runs navigation once |
| OkHttp `WebSocket`, `Authenticator`, `@Synchronized` | `core/network/WsService.kt`, `TokenAuthenticator.kt`, `core/auth/AuthService.kt` | Retrofit has no WebSocket; refresh-on-401 belongs in OkHttp; `@Synchronized` makes the refresh single-flight |
| `SharedPreferences` token store | `core/auth/SharedPrefsTokenStore.kt` | DataStore is Unit 6. Known limitation: not encrypted (LAN dev project); swap the one class later |
| `ModalBottomSheet`, `DisposableEffect`/`LifecycleEventObserver` | sheets, `RoomScreen.kt` | Bottom sheets for mobile UX; resume/pause hooks (Unit 4 covers the lifecycle concepts) |
| `LocalClipboardManager` (ignore its deprecation warning), `LocalHapticFeedback`, `BuildConfig` | `HomeScreen.kt`, `RoomCard.kt`, `RoomScreen.kt`, `ToolBar.kt`, `IdeaCard.kt`, `EnvConfig` / `app/build.gradle.kts` | Copy invite code, haptics, and reading `HOST_IP` (not covered in Units 1-5) |
| `MockWebServer` | tests only | Verifies repository paths and refresh behaviour |

---

## Mobile Design Mapping

Same features and components as the web app, re-laid-out for a phone. Colours, serif headings and the "mindspark" wordmark are kept (palette in Task 1).

| Web component | Mobile design |
|---|---|
| Hero + two action cards (create / join) on Home | `TopAppBar` + `LazyColumn` of room `Card`s; one `FloatingActionButton` opens a small choice: **Create room** / **Join with code** (each an `AlertDialog` with one text field) |
| Room row with delete button | Card with name, code chip (tap copies), `Open`/`Closed` badge, `Owner`/`Member` label, member count; delete (owner only) behind a confirm `AlertDialog` |
| Floating header (brand, room title, code, connection pill, close, participants, leave, undo/redo) | `TopAppBar`: back arrow, room name + code, connection dot, overflow menu (Rename, Close/Reopen, Leave, Delete for owner), participants icon with online count |
| Floating tool palette (select, pen, highlighter, shapes, idea, eraser) + colours | Bottom tool bar: `Row` of `IconButton`s (Hand, Select, Pen, Highlighter, Shapes, Idea, Eraser) + undo/redo; colour dots and shape picker appear in a small row above it only when needed |
| Left "ROOM IDEAS" panel, categories, search, sort | **Ideas bottom sheet** (list icon in top bar): search field, category `FilterChip` row, sort toggle, ideas list with votes, category management (owner) |
| Filter bar over canvas | Same controls, moved into the Ideas sheet; the canvas shows the filtered ideas |
| Participants drawer | Bottom sheet: online users, creator badge, **Remove** (owner) |
| Comments drawer | Bottom sheet from the card's comment button |
| Idea editor floating at the click point | `AlertDialog` (title, description, category dropdown); the card is placed at the tapped world point |
| Mouse hover cursors | Finger cursors: sent only while a finger is down on the canvas (`cursor-leave` on lift); remote ones are a small coloured dot + name |
| Drag with mouse, wheel zoom | **Hand** tool: one-finger pan, pinch zoom; `+` / `-` / **Fit** buttons in every tool. Drawing tools take one finger for drawing |
| Banners (closed, offline, errors) | A `Surface` strip under the top bar and a `Snackbar` for transient errors |

---

## Global Constraints

Paths in tasks are relative to `mobile-compose/app/src/main/java/com/mindspark/compose/` (written `main/`); tests to `mobile-compose/app/src/test/java/com/mindspark/compose/` (written `test/`), unless they start with `docs/`, `infra/`, `keycloak/`, `scripts/`, `app/` or `gradle/`. Run Gradle from `mobile-compose/`.

- Do not modify the backend; consume its REST and WebSocket APIs only.
- One LAN IP (`HOST_IP`) everywhere. Never `localhost` or `10.0.2.2`. The backend rejects tokens whose `iss` is not `http://<HOST_IP>:8180/realms/mindspark` (401 on every call).
- `HOST_IP` is passed as `-PhostIp=192.168.x.x` (or put `hostIp=...` in `~/.gradle/gradle.properties`), becomes `BuildConfig.HOST_IP`, and is read in one place only (`EnvConfig`). Unit-test builds use a harmless default (`192.168.0.1`); the build fails with a clear message only when an `assemble*` or `install*` task is requested without it.
- Backend `http://<HOST_IP>:8080`; WebSocket `ws://<HOST_IP>:8080/ws/rooms/{roomId}?token=<access_token>`; Keycloak `http://<HOST_IP>:8180`, realm `mindspark`.
- Android: `INTERNET` permission, `android:usesCleartextTraffic="true"`; `applicationId` `com.mindspark.compose`; `manifestPlaceholders["appAuthRedirectScheme"] = "com.mindspark.compose"`; `minSdk 26`.
- OIDC: client id `mobile` (public, PKCE S256, reused from the Flutter plan if it exists), redirect `com.mindspark.compose://callback` (**add it** to the client's redirect and post-logout URIs), scopes `openid profile email`. The issuer is `http`, so AppAuth needs a `ConnectionBuilder` that allows HTTP (and `skipIssuerHttpsCheck` only if discovery is used; Task 3 builds the endpoints directly instead).
- `Authorization: Bearer <access_token>` on every REST call. Refresh proactively when the token expires in under 30 s, and on 401 refresh once and retry. After login call `POST /api/users/sync`.
- World coordinate space is fixed at `1000 x 500` (2:1); 1 world unit = 1 dp in the world box. Zoom `0.3..4.0` (0.3 so "Fit" works on a portrait phone), initial scale 0.8 centred.
- Ideas: `(x, y)` is the card's **centre** (web CSS `transform: translate(-50%, -50%)`), so a card is drawn at top-left `(x - w/2, y - h/2)`. Mobile cards have a fixed size of `210 x 110` world units (web minimum width is 210 px). Positions are rounded ints clamped to `x 30..970`, `y 20..480` (web rule). Drawings: pen width 5 opacity 1; highlighter width 30 opacity 0.35; shapes minimum size 12; six-colour palette (Ink `#273b3b`, Coral `#ed8d74`, Butter `#f3c96b`, Sage `#9fc9a9`, Sky `#9dc7d8`, Lilac `#b9add5`).
- WebSocket: send `{"type":"auth","token":...}` on open (web does this as well as the query token); answer server `ping` with `{"type":"pong"}`. Close `4401` = token problem: refresh and reconnect. `4403` / `4404` = forbidden / room gone: stop and leave the room. Other closes (incl. `4408` ping timeout): reconnect with backoff `min(10 s, 1 s * 1.5^(n-1))`. On app resume: reconnect and fully re-sync.
- Soft locks (`object-locked`) expire client-side after 3 s. Outgoing throttles (web values): cursor 50 ms, card-move 120 ms, drawing-move 80 ms, stroke-points 50 ms.
- Rooms have **no WebSocket events** for close/reopen/rename/removal. Poll `GET /api/rooms/{id}` every 3 s (one request in flight); presence heartbeat `POST /api/rooms/{id}/presence {clientId}` every 10 s and `/presence/leave` on exit; full re-sync of ideas, drawings and categories every 15 s while the room screen is in the foreground, on (re)connect and on resume. Deleted ids stay as tombstones for 60 s so a late poll cannot revive them.
- Read-only rule (same as web): `canEdit = !room.closed && !room.offline`. `offline` means the owner left. The server also answers 403 to mutations in such a room, so a mutation 403 means "refresh the room", not "you were removed". Only a 403 on `GET /rooms/{id}` or on presence means removed; 404 means deleted.
- Exact JSON shapes and paths come from `docs/mobile-compose/contracts.md` (Task 2), never guessed.

## Compose and Android traps (read before Tasks 9-17)

Each rule below prevents a bug that unit tests alone do not catch.

- **Errors.** Retrofit `suspend` calls throw `HttpException` for non-2xx and `IOException` for network failures. `core/network/ApiErrors.kt` (Task 4) offers `isConnectionError(t)` and `httpCode(t)`; always catch `HttpException` separately from `IOException`, and in every `catch (e: Exception)` first rethrow `CancellationException`.
- **ViewModels with arguments.** `RoomViewModel(roomId)`, `CommentsViewModel(ideaId)` and the others get a `Factory` (see Code Level Rules); `roomId` comes from the nav argument. A ViewModel created per item must pass a `key`, e.g. `viewModel(key = "comments-$ideaId", factory = ...)`; without a key Compose reuses the first one and shows stale data.
- **One-shot events** (go home after removal, open a room): expose a `StateFlow<String?>` and a `...Handled()` function; the screen navigates in a `LaunchedEffect` and then calls `...Handled()`. No `SharedFlow`/`Channel`.
- **Threads.** WebSocket callbacks arrive on an OkHttp thread. The `WsService` event callback may only call thread-safe things: `MutableStateFlow.update { }` or `viewModelScope.launch { }`.
- **World box size.** The `1000 x 500` dp world box uses `Modifier.requiredSize(...)` (top-start aligned), never `size(...)`, which is clamped to the parent's constraints and would shrink the world to screen width.
- **Gesture handlers.** Never put two `detect*Gestures` calls inside the same `pointerInput` block (the first loops forever and makes the second unreachable), and never key `pointerInput` on the viewport (panning would restart and cancel the active gesture). Instead, key `pointerInput` on the tool (`pointerInput(tool)`), read the viewport and `canEdit` through `rememberUpdatedState`, and branch on the tool: `Hand` uses `detectTransformGestures`; `Idea` and `Eraser` use `detectTapGestures`; `Pen`, `Highlighter`, `Shapes` and `Select` use two separate chained `.pointerInput(tool)` modifiers, one with `detectTapGestures` (needed so a stationary pen tap creates a one-point stroke) and one with `detectDragGestures`.
- **Coordinate spaces.** Gestures on the outer (screen-space) box give screen pixels: use `screenToWorld` / `screenDeltaToWorld`. A `pointerInput` placed on a node *inside* the scaled `graphicsLayer` (idea cards) receives coordinates already in world pixels, so divide only by `density`, never by `scale` again (`localDeltaToWorld`). Checkpoints 4 and 6 verify this on a device at 0.3x, 1x and 4x.
- **Tools vs cards.** Idea cards take drag and tap gestures only under the Hand, Select and Idea tools (plus an eraser tap). Under Pen, Highlighter and Shapes a finger on a card must draw, not drag the card.
- **Leaving a room.** Never rely on `DisposableEffect.onDispose` (it also runs on screen rotation) or on `viewModelScope` (it is already cancelled in `onCleared`). `RoomViewModel.onCleared()` closes the `WsService` and sends `presence/leave` through the app-level `appScope` in `AppContainer` (`CoroutineScope(SupervisorJob() + Dispatchers.IO)`). Polling, sync and heartbeat loops are started and stopped by `startLoops()` / `stopLoops()` (idempotent: they cancel the old `Job` first), called from a `LifecycleEventObserver` on `ON_START` / `ON_STOP`.

## Review Focus

Inputs the spec implies but no feature test would catch; each is pinned by a test in the named task.

1. Several requests get 401 at once, or the refresh token is expired: one refresh only, then clean sign-out to login (Task 4).
2. Dragging a card or drawing while zoomed to 0.3x or 4x, or past the canvas edge: it follows the finger and stays inside the rules above (Tasks 10, 13).
3. The server repeats an event, echoes REST changes back to the sender, or mutates an id the client no longer has: state unchanged, no duplicates, no crash (Task 15).
4. A zero-length stroke (a tap with the pen), a one-point stroke, a zero-size shape, an idea with `x`/`y` null, or a drawing `kind` the web adds later: it renders or is skipped, never throws (Tasks 8, 9).
5. Room closes, goes offline, or the user is removed while a stroke, card drag or drawing drag is in progress: the gesture is discarded and nothing is sent (Task 17).
6. A late sync response arrives right after the user dropped a card or moved a drawing: it does not jump back to its old position (Tasks 10, 13).
7. Undo then an immediate redo of the same drawing works: the 60 s deleted-id memory must not block a re-create by the current user (Task 15).
8. Leaving the room by back button, menu, removal or deletion closes the WebSocket, stops every loop exactly once, and sends `presence/leave`; rotating the screen does none of these (Task 16).

## Execution notes

- Prerequisites on the host: Android Studio with SDK 34+, an Android phone with USB debugging (or emulator **with LAN access to `HOST_IP`**), Docker running.
- Terminal builds need `JAVA_HOME` set (Android Studio's bundled `jbr` works) and `mobile-compose/local.properties` with `sdk.dir=...` (Android Studio creates `local.properties` automatically when you create the Empty Activity project).
- Run on a device: `./gradlew installDebug -PhostIp=<ip>`. Unit tests (`./gradlew testDebugUnitTest`) run headless. Compose UI tests need a device: `./gradlew connectedDebugAndroidTest` is run by the human.
- Commands that start servers or install on a device are approved by the human. **Checkpoint** steps need a human with the phone: report what to look at, then stop and wait.
- Single-test form: `./gradlew testDebugUnitTest --tests "*ClassName"`.

---

### Task 0: LAN, firewall, Keycloak redirect URI

**Files:**
- Modify: `.env` (untracked), `infra/auth/import/mindspark-realm.json`, `keycloak/import/mindspark-realm.json`, `.gitignore`

**Interfaces:**
- Produces: running stack at `http://<HOST_IP>:8080` / `:8180`; Keycloak public client `mobile` accepting `com.mindspark.compose://callback`.

- [ ] **Step 1: Set `HOST_IP`.** Run `scripts/set-host-ip.ps1`; confirm `.env` `HOST_IP` equals the IPv4 from `ipconfig`. Run `docker compose up --build -d`.
- [ ] **Step 2: Open the firewall** (admin PowerShell): `New-NetFirewallRule -DisplayName "MindSpark dev" -Direction Inbound -Protocol TCP -LocalPort 8080,8180 -Action Allow -Profile Private` (skip if the Flutter plan already did it).
- [ ] **Step 3: Client `mobile`.** If it exists (Flutter Task 0), only **add** `com.mindspark.compose://callback` to Valid redirect URIs and Valid post logout redirect URIs. Otherwise create it: Client authentication `Off`; Standard flow `On`; Direct access grants `Off`; both redirect lists as above; Advanced → PKCE Code Challenge Method `S256`. Mirror in both `mindspark-realm.json` import files.
- [ ] **Step 4: Stop tracking `.env`** (`git rm --cached .env` if `git ls-files .env` prints it; keep it in `.gitignore`).
- [ ] **Step 5: Verify.** `curl -s -o /dev/null -w "%{http_code}" http://<HOST_IP>:8080/api/health` → `200`. `curl -s http://<HOST_IP>:8180/realms/mindspark/.well-known/openid-configuration` → JSON whose `issuer` is exactly `http://<HOST_IP>:8180/realms/mindspark`. **Checkpoint 0:** both URLs load in the phone's browser.
- [ ] **Step 6: Commit** `git add .gitignore infra keycloak && git commit -m "chore: compose client redirect uri and LAN setup"`

---

### Task 1: Project scaffold, Android config, `EnvConfig`, theme

**Files:**
- Create: `main/core/config/EnvConfig.kt`, `main/MainActivity.kt`, `main/MindSparkApp.kt`, `main/ui/theme/{Color,Type,Theme}.kt`
- Modify: the existing `mobile-compose/` Android Studio "Empty Activity" project (Kotlin DSL, package `com.mindspark.compose`, already created by the human; do not recreate it): `app/build.gradle.kts`, `gradle/libs.versions.toml`, `app/src/main/AndroidManifest.xml`
- Test: `test/core/config/EnvConfigTest.kt`, `test/AndroidConfigTest.kt`

**Interfaces:**
- Produces: `class EnvConfig(hostIp: String) { val apiUrl: String; val issuer: String; fun wsUrl(roomId: String, token: String): String }` (`apiUrl` = `http://<ip>:8080`, `issuer` = `http://<ip>:8180/realms/mindspark`; token URL-encoded); `MindSparkTheme { }` (colours from the web: ink `#233b3b`, coral `#e9784d`, background `#f3f6ee`, card `#fffdf8`; headings `FontFamily.Serif`); `MindSparkApp()` placeholder `Scaffold`.
- Pure JVM rule: `EnvConfig` must use `java.net.URLEncoder` for the token. Never use `android.net.Uri` or `android.util.Log` in pure logic (`EnvConfig`, `logic/`), so JVM unit tests run without the Android framework.
- Dependencies (catalog): Compose BOM, material3, material-icons-extended, navigation-compose, lifecycle-viewmodel-compose, retrofit, `converter-kotlinx-serialization` (official, same version as Retrofit), kotlinx-serialization-json, okhttp (+ `mockwebserver` for tests), `net.openid:appauth`, `kotlinx-coroutines-test`, `compose-ui-test-junit4`.

- [ ] **Step 1: Write failing tests.**

```kotlin
class EnvConfigTest {
    @Test fun buildsUrlsFromHostIp() {
        val e = EnvConfig("192.168.1.20")
        assertEquals("http://192.168.1.20:8080", e.apiUrl)
        assertEquals("http://192.168.1.20:8180/realms/mindspark", e.issuer)
        assertEquals("ws://192.168.1.20:8080/ws/rooms/r1?token=tok", e.wsUrl("r1", "tok"))
    }
    @Test fun rejectsEmptyLocalhostAndEmulatorAddress() {
        for (bad in listOf("", "localhost", "10.0.2.2")) {
            assertThrows(IllegalArgumentException::class.java) { EnvConfig(bad) }
        }
    }
}
// AndroidConfigTest: unit tests run with the module (app/) as working directory, so it reads
//   File("src/main/AndroidManifest.xml") and File("build.gradle.kts")
//   expects 'android.permission.INTERNET' and 'usesCleartextTraffic="true"'
//   expects 'appAuthRedirectScheme' and 'com.mindspark.compose'
```

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest` — Expected: FAIL (`EnvConfig` missing).
- [ ] **Step 3: Configure and implement.** Configure the existing `mobile-compose/` Android Studio Empty Activity project (do not recreate it), set `applicationId`, add the Global Constraints manifest/gradle entries and the `hostIp` → `BuildConfig.HOST_IP` field (`buildFeatures { buildConfig = true }`; default `192.168.0.1`; throw a `GradleException` only if `gradle.startParameter.taskNames` contains an `assemble` or `install` task and `-PhostIp` is missing), pin the Kotlin version once in the catalog and use the matching plugins (`org.jetbrains.kotlin.plugin.compose`, `org.jetbrains.kotlin.plugin.serialization`), pin current stable versions in `gradle/libs.versions.toml` and print them in your summary (keep `okhttp` and `mockwebserver` on matching 4.12.x versions; OkHttp 5.x renamed artifacts and can mismatch Retrofit), add dependencies, implement `EnvConfig` (throws `IllegalArgumentException` for the three bad values), theme, placeholder app. Copy the web tool icons from `frontend/assets/mindspark/*.png` into `app/src/main/res/drawable/` (lower-case, underscores) for later tasks.
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest` — Expected: PASS. Then `./gradlew assembleDebug -PhostIp=192.168.1.20` — Expected: BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `git add mobile-compose && git commit -m "feat(compose): scaffold app with env config and theme"`

---

### Task 2: API contracts and fixtures

**Files:**
- Create: `docs/mobile-compose/contracts.md`, `app/src/test/resources/fixtures/*.json`, `test/FixturesTest.kt`

**Interfaces:**
- Produces: `contracts.md` with sections **User**, **Rooms**, **Presence**, **Categories**, **Ideas**, **Comments**, **Drawings**, **WebSocket envelopes**, **Read-only rules**; each endpoint as method + path + request JSON + response JSON, copied from the backend controllers/`ApiModels.java`; fixtures `user.json`, `room.json`, `rooms_list.json`, `idea.json`, `category.json`, `comment_with_replies.json`, `drawing_pen.json`, `drawing_highlighter.json`, `drawing_rectangle.json`, `drawing_circle.json`, `drawing_triangle.json`, `ws_<type>.json` (one per message type in `docs/REALTIME.md` plus `presence`, `ping`, `category-*`).
- Later tasks consume these section names. Start from Appendix A and **verify each line against the backend source**; fix the appendix if the code disagrees.

- [ ] **Step 1: Write the failing test** `FixturesTest`: every file in `File("src/test/resources/fixtures")` (working directory is `app/`) parses with `Json.parseToJsonElement` and is a non-empty object or array; all expected file names exist. Add `test/TestFixtures.kt` with `fun fixture(name: String): String` that reads `fixtures/<name>` through `javaClass.classLoader.getResourceAsStream`; all other tests use it.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*FixturesTest"` — Expected: FAIL.
- [ ] **Step 3: Write `contracts.md` and fixtures.** Sources: `RoomController`, `IdeaController`, `CategoryController`, `CommentController`, `DrawingController`, `PresenceController`, `ApiModels.java`, `RoomWebSocketHandler`, `docs/REALTIME.md`, the Angular `realtime.service.ts`, `drawing-hit-test.ts`, `workspace-canvas.ts`. Real JSON only (copy from backend tests or capture with `curl` and a token); do not invent fields. Record: which room call the web Home uses when opening a room (`reopen`) and which pair it uses for close/open; whether `DELETE /api/rooms/{id}` is owner-only; the idea `color` the web sends in `saveIdea`; the exact rules of the web `drawing-position-guard.ts` and `idea-position-guard.ts` (Task 10 ports both); whether the web clamps drawing offsets; that idea `(x, y)` is the card centre (verified: CSS `translate(-50%, -50%)`); the backend presence expiry/TTL (so Task 16 knows how long an owner can background the app before the room is marked offline); and the exact tolerances and rules from `drawing-hit-test.ts` and the point simplification, for Task 12.
- [ ] **Step 4: Run** the same test — Expected: PASS.
- [ ] **Step 5: Commit** `git add docs/mobile-compose mobile-compose && git commit -m "docs(compose): API and WebSocket contracts with fixtures"`

---

### Task 3: `AuthService` (login, token store, single-flight refresh)

**Files:**
- Create: `main/core/auth/{AuthModels,TokenStore,SharedPrefsTokenStore,AuthGateway,OkHttpAuthGateway,AuthService,LoginLauncher}.kt`
- Test: `test/core/auth/AuthServiceTest.kt`, `test/core/auth/OkHttpAuthGatewayTest.kt`

**Interfaces:**
- Consumes: `EnvConfig`.
- Produces:
  - `data class AuthTokens(val accessToken: String, val refreshToken: String, val idToken: String, val expiresAtMillis: Long)`
  - `interface TokenStore { fun read(): AuthTokens?; fun save(t: AuthTokens); fun clear() }` + `SharedPrefsTokenStore(context)` + test `InMemoryTokenStore`
  - `interface AuthGateway { fun refresh(refreshToken: String): AuthTokens; fun logout(refreshToken: String) }` + `OkHttpAuthGateway(env, client)`: form POST to `<issuer>/protocol/openid-connect/token` (`grant_type=refresh_token`, `client_id=mobile`) and `.../logout` (blocking `execute()`)
  - `class SessionExpired : Exception()`
  - `class AuthService(gateway, store, clock: () -> Long)` with `fun saveLogin(t: AuthTokens)`, `fun validAccessToken(): String?` (refreshes when expiring in under 30 s), `@Synchronized fun refreshAccessToken(failedToken: String?): String` (if the stored access token already differs from `failedToken`, return it without calling the gateway; on any gateway error clear the store, set `sessionExpired` and throw `SessionExpired`), `fun logout()`, `val sessionExpired: StateFlow<Boolean>`
  - `class LoginLauncher(env, context)` (AppAuth): service configuration built **directly** from the issuer, `AuthorizationServiceConfiguration(Uri("<issuer>/protocol/openid-connect/auth"), Uri("<issuer>/protocol/openid-connect/token"))`, so no HTTP discovery is needed; `AppAuthConfiguration` with a small `ConnectionBuilder` that allows HTTP; `fun authIntent(): Intent` (client `mobile`, scopes, `prompt=login` so a leftover browser session never silently signs in the previous user); `fun handleResult(data: Intent?, onTokens: (AuthTokens) -> Unit, onError: (String) -> Unit, onCancelled: () -> Unit)` which reads `AuthorizationResponse.fromIntent`, then does the **asynchronous** `performTokenRequest` and calls back on its result (no response and no exception = the user cancelled). Not unit-tested; covered by Checkpoint 1.

- [ ] **Step 1: Write failing tests** (`FakeGateway` counting calls, `InMemoryTokenStore`, fixed clock).

```kotlin
@Test fun saveLoginStoresTokens() {
    service.saveLogin(tokens("a1"))
    assertEquals("a1", store.read()!!.accessToken)
}
@Test fun secondRefreshWithSameFailedTokenDoesNotCallGatewayAgain() {
    service.saveLogin(tokens("a1"))
    val first = service.refreshAccessToken("a1")   // gateway returns a2
    val second = service.refreshAccessToken("a1")  // already refreshed
    assertEquals("a2", first); assertEquals("a2", second)
    assertEquals(1, gateway.refreshCalls)
}
@Test fun refreshFailureClearsStoreAndThrowsSessionExpired() {
    gateway.failRefresh = true
    service.saveLogin(tokens("a1"))
    assertThrows(SessionExpired::class.java) { service.refreshAccessToken("a1") }
    assertNull(store.read()); assertTrue(service.sessionExpired.value)
}
@Test fun validAccessTokenRefreshesWhenExpiringSoon() { /* clock = expiresAt - 10 s -> returns a2 */ }
```
`OkHttpAuthGatewayTest` (MockWebServer): the refresh request has the right form fields; a non-200 response throws.

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*auth*"` — Expected: FAIL.
- [ ] **Step 3: Implement** the Interfaces. Keep `AuthService` plain: no coroutines inside (the OkHttp `Authenticator` calls it from a background thread).
- [ ] **Step 4: Run** the same tests — Expected: PASS.
- [ ] **Step 5: Commit** `git add mobile-compose && git commit -m "feat(compose): auth service with single-flight refresh"`

---

### Task 4: Retrofit + OkHttp client, Bearer injection, refresh-on-401

**Files:**
- Create: `main/core/network/{HttpClientFactory,AuthInterceptor,TokenAuthenticator,ApiErrors}.kt`, `main/AppContainer.kt`, `main/MindSparkApplication.kt`
- Modify: `AndroidManifest.xml` (`android:name=".MindSparkApplication"`)
- Test: `test/core/network/HttpClientTest.kt`

**Interfaces:**
- Consumes: `AuthService`, `SessionExpired`, `EnvConfig`.
- Produces: `fun buildOkHttp(auth: AuthService): OkHttpClient` (`AuthInterceptor` adds `Authorization` from `auth.validAccessToken()`; `TokenAuthenticator` handles 401: one retry using `auth.refreshAccessToken(failedToken)`, returns `null` on `SessionExpired` so the 401 surfaces); `fun buildRetrofit(env: EnvConfig, client: OkHttpClient): Retrofit` (kotlinx.serialization converter, `ignoreUnknownKeys = true`); `class AppContainer(context)` exposing `env`, `authService`, `okHttp`, `retrofit`, and `appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` for work that must outlive a ViewModel (repositories are added by later tasks); `fun isConnectionError(t: Throwable): Boolean` (true for `IOException`), `fun httpCode(t: Throwable): Int?` (the code of an `HttpException`, else `null`).

- [ ] **Step 1: Write failing tests** (MockWebServer, fake `AuthService` with a call counter).

```kotlin
@Test fun addsBearerHeader() { /* first request has "Authorization: Bearer a1" */ }
@Test fun on401RefreshesOnceAndRetriesWithNewToken() { /* 401 then 200; second request has Bearer a2; refreshCalls == 1 */ }
@Test fun rf1_threeParallel401sCauseExactlyOneRefresh() { /* 3 threads, all 401 first; refreshCalls == 1 */ }
@Test fun rf1_refreshFailureSurfaces401AndSetsSessionExpired() { /* gateway fails; response code 401; sessionExpired == true */ }
@Test fun apiErrorsTellHttpFromConnectionFailures() { /* HttpException(403) -> httpCode 403, not connection error; IOException -> connection error */ }
```

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*HttpClientTest"` — Expected: FAIL.
- [ ] **Step 3: Implement.** Retry only once per request (check `response.priorResponse`). `MindSparkApplication` creates the `AppContainer` (Unit 5 pattern).
- [ ] **Step 4: Run** the same test — Expected: PASS.
- [ ] **Step 5: Commit** `git add mobile-compose && git commit -m "feat(compose): http client with token refresh"`

---

### Task 5: Sign-in flow, user sync, navigation

**Files:**
- Create: `main/core/auth/AuthViewModel.kt` (with a `Factory`), `main/core/network/UserApi.kt`, `main/features/auth/LoginScreen.kt`, `main/ui/Routes.kt`
- Modify: `main/MindSparkApp.kt`, `main/MainActivity.kt`, `main/AppContainer.kt`
- Test: `test/core/auth/AuthViewModelTest.kt`

**Interfaces:**
- Consumes: `AuthService`, `LoginLauncher`, Retrofit, contracts **User**.
- Produces: `@Serializable data class UserDto(val id: Long, val username: String, val email: String)`; `interface UserApi { @POST("api/users/sync") suspend fun sync(): UserDto }`; `sealed interface AuthUiState { SignedOut; Loading; SignedIn(user: UserDto); Error(message: String) }`; `class AuthViewModel` with `val uiState: StateFlow<AuthUiState>`, `fun onLoginResult(tokens: AuthTokens)`, `fun onLoginError(message: String)` (→ `Error`), `fun onLoginCancelled()` (→ `SignedOut`, never stuck in `Loading`), `fun signOut()`, startup restore (stored token present → `sync()`, failure → `SignedOut`), reacts to `auth.sessionExpired`; `object Routes { LOGIN = "login"; HOME = "home"; ROOM = "room/{roomId}" }`; `NavHost` redirects: signed out → `login`, signed in → `home`. Login button launches `LoginLauncher.authIntent()` via `rememberLauncherForActivityResult` and passes the result to `handleResult`.

- [ ] **Step 1: Write failing tests** (fake `AuthService`/`UserApi`, `StandardTestDispatcher`).

```kotlin
@Test fun loginSavesTokensCallsSyncAndExposesUser() = runTest {
    viewModel.onLoginResult(tokens("a1")); advanceUntilIdle()
    assertEquals(1, fakeUserApi.syncCalls)
    assertEquals("a@b.tn", (viewModel.uiState.value as AuthUiState.SignedIn).user.email)
}
@Test fun sessionExpiredFlagSignsOut() = runTest { /* set flag -> SignedOut */ }
@Test fun startupWithStoredSessionSyncsUser() = runTest { }
@Test fun startupSyncFailureGoesToSignedOut() = runTest { }
@Test fun cancelledOrFailedLoginLeavesLoadingState() = runTest { }
```

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*AuthViewModelTest"` — Expected: FAIL.
- [ ] **Step 3: Implement** per Interfaces. Login screen: wordmark, one **Log in** button, error text.
- [ ] **Step 4: Run** the same test — Expected: PASS.
- [ ] **Step 5: Checkpoint 1 (human):** `./gradlew installDebug -PhostIp=<ip>`; Log in → Keycloak in a browser tab → back in the app on a placeholder Home showing the username; backend log shows `/api/users/sync`. Kill and reopen: still signed in.
- [ ] **Step 6: Commit** `git add mobile-compose && git commit -m "feat(compose): keycloak sign-in and user sync"`

---

### Task 6: Rooms models, repository, `HomeViewModel`

**Files:**
- Create: `main/features/rooms/model/Room.kt`, `main/features/rooms/data/{RoomApi,RoomRepository}.kt`, `main/features/rooms/ui/{HomeViewModel,HomeUiState}.kt` (`HomeViewModel` has a `Factory`)
- Modify: `main/AppContainer.kt`
- Test: `test/features/rooms/RoomModelsTest.kt`, `test/features/rooms/RoomRepositoryTest.kt`, `test/features/rooms/HomeViewModelTest.kt`

**Interfaces:**
- Consumes: Retrofit; fixtures `room.json`, `rooms_list.json`; contracts **Rooms**, **Presence**.
- Produces:
  - `@Serializable data class Room(val id: String, val name: String, val invitationCode: String, val owner: UserDto? = null, val createdAt: String? = null, val members: List<UserDto> = emptyList(), val memberCount: Int = 0, val member: Boolean = false, val closed: Boolean = false, val offline: Boolean = false)`
  - `interface RoomRepository { suspend fun listRooms(): List<Room>; getRoom(id); createRoom(name); joinByCode(code); reopenRoom(id); renameRoom(id, name); closeRoom(id); openRoom(id); leaveRoom(id); removeMember(roomId, memberId: Long); deleteRoom(id); sendHeartbeat(roomId, clientId); sendLeave(roomId, clientId) }` + `NetworkRoomRepository(api)`
  - `sealed interface HomeUiState { Loading; Success(rooms: List<Room>); Error(message: String, isConnection: Boolean) }` (`Error` only when the list could not be loaded); `HomeViewModel` with `refresh()`, `create(name)`, `join(code)`, `open(room)` (calls `reopenRoom`, then sets `navigateToRoom`), `delete(room)`; `val message: StateFlow<String?>` + `messageShown()` for failed actions (shown as a `Snackbar`, the room list stays on screen); `val navigateToRoom: StateFlow<String?>` + `navigated()`.

- [ ] **Step 1: Write failing tests:** `Room` parses both fixtures (extra/missing fields tolerated); each repository method hits the method + path from `contracts.md` (MockWebServer `takeRequest()`); `HomeViewModel.create` adds the new room to the state; `join` with a blank code does not call the repository; a failed `refresh` becomes `Error` (`isConnection` true only for `IOException`, not for `HttpException`); a failed `create`, `join` or `delete` keeps the current `Success` list and sets `message`; `navigated()` clears `navigateToRoom`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*features.rooms*"` — Expected: FAIL.
- [ ] **Step 3: Implement.** Paths and bodies exactly as in `contracts.md`.
- [ ] **Step 4: Run** the same tests — Expected: PASS.
- [ ] **Step 5: Commit** `git add mobile-compose && git commit -m "feat(compose): rooms data layer"`

---

### Task 7: Home screen

**Files:**
- Create: `main/features/rooms/ui/{HomeScreen,RoomCard,CreateRoomDialog,JoinRoomDialog}.kt`
- Modify: `main/MindSparkApp.kt` (`home` → `HomeScreen`, `room/{roomId}` → placeholder)
- Test: `app/src/androidTest/java/com/mindspark/compose/HomeScreenTest.kt`

**Interfaces:**
- Consumes: `HomeViewModel`, `AuthViewModel` (`UserDto.id` decides Owner/Member).
- Produces: `HomeScreen(onOpenRoom: (String) -> Unit, onSignOut: () -> Unit)`; the top bar shows the username and a sign-out icon; **Connection error** state shows the message and a **Retry** button (hostIp is named in the message).

- [ ] **Step 1: Write failing Compose UI tests:** the list shows `Open`/`Closed`, the invite code and `Owner`/`Member` per room; tapping a card calls `open` and navigates once (no second navigation after `navigated()`); a failed action shows a `Snackbar` while the list stays; the list refreshes when the screen resumes (`ON_RESUME`); the FAB opens a choice and **Create** calls `create`; **Join** calls `join`; the refresh icon calls `refresh`; the empty state text shows for no rooms.
- [ ] **Step 2: Run** `./gradlew connectedDebugAndroidTest --tests "*HomeScreenTest"` (device; the human runs it) — Expected: FAIL.
- [ ] **Step 3: Implement** the screen and two dialogs (one trimmed, non-empty text field each; room name max 120, code max 8). Tapping the code copies it (`LocalClipboardManager`).
- [ ] **Step 4: Run** the same test — Expected: PASS.
- [ ] **Step 5: Checkpoint 2 (human):** a room created on the phone appears on the web app and vice versa; join by code works.
- [ ] **Step 6: Commit** `git add mobile-compose && git commit -m "feat(compose): rooms home screen"`

---

### Task 8: Whiteboard models and repositories

**Files:**
- Create: `main/features/whiteboard/model/{Idea,Category,CommentItem,DrawingDto,DrawingElement}.kt`, `main/features/whiteboard/data/{IdeaApi,CategoryApi,CommentApi,DrawingApi,IdeaRepository,CategoryRepository,CommentRepository,DrawingRepository}.kt`, `main/features/whiteboard/logic/{World,Palette}.kt`
- Modify: `main/AppContainer.kt`
- Test: `test/features/whiteboard/ModelsTest.kt`, `test/features/whiteboard/RepositoriesTest.kt`

**Interfaces:**
- Consumes: fixtures `idea.json`, `category.json`, `comment_with_replies.json`, `drawing_*.json`; contracts **Categories/Ideas/Comments/Drawings**.
- Produces:
  - `const val WORLD_WIDTH = 1000f; const val WORLD_HEIGHT = 500f`; `val palette: List<PaletteColor>`; `fun colorFromHex(hex: String): Color` (bad value → Ink)
  - `Idea(id, author: UserDto?, title, description, text, x: Double?, y: Double?, color, voteCount: Long, voted: Boolean, category: Category?, createdAt, updatedAt)`; `Category(id: Long, title)`; `CommentItem(id, ideaId, author: UserDto?, content, text, parentId, createdAt, updatedAt, replies: List<CommentItem>)`; `DrawingDto(id, roomId, author, kind, color, width, opacity, payload: String, offsetX, offsetY, ...)`
  - `sealed interface DrawingElement { val id: String }` with `StrokeElement(id, authorId, tool, color, width, opacity, points: List<WorldPoint>, offsetX, offsetY)`, `ShapeElement(id, authorId, kind, color, x, y, width, height, offsetX, offsetY)`, `UnknownElement(id)`; `data class WorldPoint(val x: Float, val y: Float)`; `fun DrawingDto.toElement(): DrawingElement` (payload parsed with kotlinx `Json`; stroke payload is an array **or** `{points: [...]}`; shape payload `{x,y,width,height}` with web defaults `0, 0, 50, 50`; bad JSON or unknown kind → `UnknownElement`); `fun DrawingElement.toRequest(): DrawingRequest`.
  - `IdeaRepository { list(roomId, categoryId: Long?, search: String, sortBy: String); create(roomId, IdeaPayload); update(id, IdeaPayload); delete(id); vote(id); removeVote(id) }` (the repository fills both `title` and `text`, as the web does); `CategoryRepository { list; create; rename; delete }`; `CommentRepository { list(ideaId); add(ideaId, text); reply(commentId, text); edit(id, text); delete(id) }` (sends both `content` and `text`); `DrawingRepository { list(roomId); create(roomId, request); move(roomId, id, offsetX, offsetY); delete(roomId, id) }`.

- [ ] **Step 1: Write failing tests:** each model parses its fixture; `toElement().toRequest()` round-trips the five drawing fixtures (same kind, colour, payload geometry); `DrawingDto(kind = "hexagon").toElement()` is `UnknownElement`; a payload `"not json"` gives `UnknownElement`, not an exception; an idea with `x = null` parses; `colorFromHex("#ff0000")` is red and `colorFromHex("nope")` is Ink; repositories hit the contract paths and send `categoryId`/`search`/`sortBy` as query params.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*features.whiteboard*"` — Expected: FAIL.
- [ ] **Step 3: Implement** with field names exactly as in `contracts.md`.
- [ ] **Step 4: Run** the same tests — Expected: PASS.
- [ ] **Step 5: Commit** `git add mobile-compose && git commit -m "feat(compose): whiteboard models and repositories"`

---

### Task 9: Read-only canvas: viewport, drawing layer, idea cards

**Files:**
- Create: `main/features/whiteboard/ui/{RoomScreen,RoomViewModel,CanvasUiState}.kt` (`RoomViewModel(roomId, ...)` has a `Factory`; `roomId` is the nav argument), `main/features/whiteboard/ui/widgets/{CanvasViewport,DrawingLayer,IdeaCard}.kt`, `main/features/whiteboard/logic/{ViewportMath,IdeaLayout}.kt`
- Modify: `main/MindSparkApp.kt` (`room/{roomId}` → `RoomScreen`)
- Test: `test/features/whiteboard/ViewportMathTest.kt`, `test/features/whiteboard/RoomViewModelLoadTest.kt`, `app/src/androidTest/.../DrawingLayerTest.kt`

**Interfaces:**
- Consumes: Task 8 models/repositories.
- Produces:
  - `data class CanvasUiState(val ideas: List<Idea>, val elements: List<DrawingElement>, val categories: List<Category>)`; `sealed interface RoomUiState { Loading; Ready(canvas: CanvasUiState, room: Room); Error(message) }`; `RoomViewModel(roomId)` loading room, ideas, categories, drawings in parallel.
  - `data class ViewportState(val scale: Float, val translation: Offset)`; `fun screenToWorld(screen: Offset, vp: ViewportState, density: Float): Offset` = `(screen - translation) / (scale * density)`; `fun clampScale(s: Float): Float` (0.3..4); `fun fitScale(screenWidthPx: Float, density: Float): Float`; `fun zoomAround(vp, focusScreen, factor): ViewportState`.
  - `const val CARD_WIDTH = 210f; const val CARD_HEIGHT = 110f`; `fun ideaTopLeft(x: Float, y: Float): Offset` = `(x - CARD_WIDTH / 2, y - CARD_HEIGHT / 2)` (the idea `(x, y)` is the card centre); `fun defaultIdeaCentre(index: Int): Offset` for ideas whose `x`/`y` is null (staggered, inside the clamp range).
  - `CanvasViewport(viewport, onViewportChange, content)`: an outer `Box` (screen space, receives gestures) containing an inner `Box(Modifier.requiredSize(1000.dp, 500.dp))`, top-start aligned (never `size`, see traps), drawn with `graphicsLayer(scaleX, scaleY, translationX, translationY, transformOrigin = TopLeft)`; `+` / `-` / **Fit** buttons.
  - `DrawingLayer(elements)`: `Canvas` filling the world box; inside, scale by `density` once so drawing code uses world units; pen/highlighter as a `Path` with round caps, circle as `drawOval`, rectangle as `drawRect`, triangle as a 3-point `Path`, all stroked with the element colour and offset applied.
  - `IdeaCard(idea, gesturesEnabled: Boolean)`: `Card` of fixed size `CARD_WIDTH x CARD_HEIGHT` with title (2 lines), description snippet (2 lines), category chip, background colour, vote count, comment icon, compact padding and `bodySmall` text so it fits; placed with `Modifier.offset(topLeft.x.dp, topLeft.y.dp)` where `topLeft = ideaTopLeft(x, y)`; `x`/`y` null → `defaultIdeaCentre(index)`.

- [ ] **Step 1: Write failing tests:** `screenToWorld` with scale 2, translation (100, 50), density 1 maps screen (300, 250) to world (100, 100); with density 2.75 the result is consistent; `clampScale(0.1) == 0.3`, `clampScale(9) == 4`; `fitScale` for a 1080-px, density-2.75 screen is about 0.39; `ideaTopLeft(500f, 250f)` is `(395, 195)`; `defaultIdeaCentre` stays inside `30..970` x `20..480` for 50 ideas; `zoomAround` keeps the focus point's world position fixed; `RoomViewModel` becomes `Ready` with all three lists, and `Error` if the room call fails. UI test (device): the world box measures 1000 x 500 dp even when the screen is 360 dp wide, and a card with `x=500,y=250` is centred on world `(500, 250)`; a stroke with 3 points, a highlighter, each shape kind, **RF4** a one-point stroke, two identical points, a rectangle with width 0 and an `UnknownElement` all compose without throwing.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*ViewportMathTest" --tests "*RoomViewModelLoadTest"` — Expected: FAIL.
- [ ] **Step 3: Implement.** Single-point/zero-length strokes draw a dot of stroke width; `UnknownElement` is skipped. Pan/zoom uses `detectTransformGestures` on the outer box inside `pointerInput(tool)` (see the Gesture handlers trap), reading the viewport through `rememberUpdatedState` (the tool is fixed to `Hand` until Task 13 adds the tool bar).
- [ ] **Step 4: Run** the unit tests — Expected: PASS.
- [ ] **Step 5: Checkpoint 3 (human):** open a complex web-made room; strokes, shapes and cards sit at the same relative positions; pinch zoom, pan and **Fit** work.
- [ ] **Step 6: Commit** `git add mobile-compose && git commit -m "feat(compose): read-only whiteboard canvas"`

---

### Task 10: Ideas: create, edit, drag, vote, filter, categories

**Files:**
- Create: `main/features/whiteboard/logic/{IdeaClamp,PositionGuard}.kt`, `main/features/whiteboard/ui/{IdeaEditorDialog,IdeasSheet}.kt`
- Modify: `RoomViewModel.kt`, `IdeaCard.kt`, `RoomScreen.kt`
- Test: `test/features/whiteboard/IdeaClampTest.kt`, `test/features/whiteboard/PositionGuardTest.kt`, `test/features/whiteboard/IdeaActionsTest.kt`

**Interfaces:**
- Produces:
  - `fun clampIdeaPosition(x: Float, y: Float): Pair<Int, Int>` (rounded; `x 30..970`, `y 20..480`); `fun screenDeltaToWorld(delta: Offset, vp: ViewportState, density: Float): Offset` (for gestures on the outer, screen-space box) and `fun localDeltaToWorld(delta: Offset, density: Float): Offset` (for the card's own `pointerInput`, which is inside the scaled layer: divide by `density` only)
  - `class PositionGuard { fun registerDrop(key: String, x: Double, y: Double): Int; fun onSaveSuccess(key, seq); fun onSaveFailure(key, seq): Pair<Double, Double>? /* rollback pos */; fun applyIdeas(ideas: List<Idea>): List<Idea>; fun applyDrawings(elements: List<DrawingElement>): List<DrawingElement> /* both keep the local position or offset of an item with a pending or just-saved move until a sync that started after the save */ }`. Keys are `"idea:<id>"` and `"drawing:<id>"`. One simplified port of **both** web guards (`idea-position-guard.ts` and `drawing-position-guard.ts`; read them and match their rules). Task 13 reuses it for drawing offsets.
  - `RoomViewModel`: `createIdea(draft, atWorld)`, `updateIdea(idea, draft)`, `onIdeaDrag(id, worldPos)` (local only, plus a `card-move` hook added in Task 16), `onIdeaDrop(id)` (one `update` call with the full payload and the new x/y), `toggleVote(id)` (optimistic, rolls back on error), `deleteIdea(id)`, filters `setSearch(text)` (300 ms debounce with a cancelled `Job`), `setCategoryFilter(id?)`, `setSort(createdAt|voteCount)`, categories `addCategory`, `renameCategory`, `deleteCategory` (owner, room editable).
  - Rules: any member may move any idea (position-only update is allowed server-side); only the author edits title, description or colour (editor opens only for own ideas); delete via Eraser only by the owner or the author.

- [ ] **Step 1: Write failing tests.**

```kotlin
@Test fun rf2_deltaDividesByScaleAndDensity() {
    val vp = ViewportState(scale = 0.5f, translation = Offset.Zero)
    assertEquals(Offset(80f, 40f), screenDeltaToWorld(Offset(40f, 20f), vp, density = 1f))
    assertEquals(Offset(10f, 5f), screenDeltaToWorld(Offset(40f, 20f), vp.copy(scale = 4f), density = 1f))
}
@Test fun rf2_clampKeepsCardsInsideTheRules() {
    assertEquals(Pair(30, 480), clampIdeaPosition(-5f, 900f))
    assertEquals(Pair(970, 20), clampIdeaPosition(1200f, -1f))
}
@Test fun rf6_serverCoordsAreIgnoredWhileMoveIsSavingOrJustSaved() { /* drop (200,100); applyIdeas with old (50,50) keeps (200,100); a later sync after save accepts server coords */ }
@Test fun localDeltaDividesByDensityOnly() { assertEquals(Offset(10f, 5f), localDeltaToWorld(Offset(27.5f, 13.75f), density = 2.75f)) }
@Test fun sameGuardProtectsDrawingOffsets() { /* registerDrop("drawing:d1", ...) then applyDrawings keeps the local offset */ }
@Test fun failedSaveRollsBackToPreviousPosition() { }
@Test fun dropCallsUpdateOnceWithClampedPosition() { /* drag to (1200,-1) -> update(id, x=970, y=20) once */ }
@Test fun voteTogglesOptimisticallyAndRollsBackOnError() { }
@Test fun searchIsDebouncedAndSentAsQueryParam() { /* type 3 letters quickly -> one list call with search */ }
```

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*IdeaClampTest" --tests "*PositionGuardTest" --tests "*IdeaActionsTest"` — Expected: FAIL.
- [ ] **Step 3: Implement.** Card drag with `detectDragGestures` on the card and the card tap with `detectTapGestures`, in two separate chained `.pointerInput(Unit)` modifiers (never both in one block), each using `rememberUpdatedState` for `gesturesEnabled` and `canEdit`; deltas go through `localDeltaToWorld` and the new centre is `clampIdeaPosition(startCentre + delta)`; the gesture is consumed so the canvas does not pan; it is active only when `gesturesEnabled` (Task 13 sets it from the tool); a tap on an own card opens the editor; the vote and comment buttons are separate clickable areas. With the **Idea** tool, a tap on empty canvas converts the point with `screenToWorld`, clamps it and opens the `AlertDialog` editor (title max 80, description max 280, category dropdown, "No category"). New idea colour: confirm in the web `saveIdea` (contracts) and reuse it. The Ideas sheet holds search, category chips, sort toggle, the idea list (tap an idea to centre the canvas on it) and category management.
- [ ] **Step 4: Run** the same tests — Expected: PASS.
- [ ] **Step 5: Checkpoint 4 (human):** create, move, edit, vote and filter on the phone; refresh the web app and confirm it matches; a card never snaps back after a drop, and the finger stays on the card while dragging at 0.3x, 1x and 4x zoom (no drift, no jitter); a card created on the phone appears at the same spot, centred, on the web.
- [ ] **Step 6: Commit** `git add mobile-compose && git commit -m "feat(compose): idea interactions, voting, filtering, categories"`

---

### Task 11: Comments

**Files:**
- Create: `main/features/whiteboard/ui/{CommentsSheet,CommentsViewModel}.kt` (`CommentsViewModel(ideaId)` has a `Factory`; the sheet creates it with `viewModel(key = "comments-$ideaId", factory = ...)`)
- Modify: `IdeaCard.kt` (comment icon opens the sheet; the idea has no comment count)
- Test: `test/features/whiteboard/CommentsViewModelTest.kt`, `app/src/androidTest/.../CommentsSheetTest.kt`

**Interfaces:**
- Consumes: `CommentRepository`, `CommentItem`.
- Produces: `CommentsUiState(comments: List<CommentItem>, loading: Boolean, error: String?)`; `CommentsViewModel(ideaId)` with `load()`, `add(text)`, `reply(parentId, text)`, `edit(id, text)`, `delete(id)`, `applyRemote(event)`; `canReply = canEdit`, `canEditOrDelete(comment) = canEdit && comment.author.id == me`.

- [ ] **Step 1: Write failing tests:** top-level comments with nested `replies` render in server order; a reply calls `reply(parentId, ...)`; edit and delete are offered only for own comments; when `canEdit` is false there is no input, reply, edit or delete; opening the sheet for idea A and then for idea B shows B's comments (UI test: the ViewModel key includes the idea id); a remote `comment-created` for this idea is added once (twice → still once); a remote `comment-deleted` removes it; text is trimmed and empty text is not sent (max 2000).
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*CommentsViewModelTest"` — Expected: FAIL.
- [ ] **Step 3: Implement** with a `ModalBottomSheet`: header with the idea title, `LazyColumn` (comment, then indented replies), input row at the bottom.
- [ ] **Step 4: Run** the same test — Expected: PASS.
- [ ] **Step 5: Checkpoint 5 (human):** reply on the phone, see it nested on web, and vice versa; edit and delete own comments.
- [ ] **Step 6: Commit** `git add mobile-compose && git commit -m "feat(compose): comments sheet"`

---

### Task 12: Hit-testing, simplification and undo/redo logic

**Files:**
- Create: `main/features/whiteboard/logic/{DrawingHitTest,SimplifyPoints,UndoRedo,Tombstones}.kt`
- Test: `test/features/whiteboard/DrawingHitTestTest.kt`, `test/features/whiteboard/UndoRedoTest.kt`, `test/features/whiteboard/TombstonesTest.kt`

**Interfaces:**
- Consumes: `DrawingElement`, `StrokeElement`, `ShapeElement`.
- Produces: a direct port of `drawing-hit-test.ts` (read it, match numbers):
  - `fun toleranceInWorldUnits(screenPx: Float, vp: ViewportState, density: Float): Float` (6 px by default)
  - `fun distToSegment(p, a, b): Float`, `fun hitTestStroke(p, stroke, tolerance): Boolean` (effective tolerance = `max(tol, width / 2 + tol)`; one point = distance to that point; offsets applied), `fun hitTestShape(p, shape, tolerance)` (circle: normalised ellipse with `+tolerance`; triangle: barycentric test, else distance to its three edges; rectangle: bounding box `+tolerance`), `fun topHit(elements, p, tolerance): DrawingElement?` (last drawn wins; `UnknownElement` never hits)
  - `fun boundingBox(e: DrawingElement): Rect` (offsets applied, so the Select box follows a moved drawing)
  - `fun simplifyPoints(points: List<WorldPoint>, tolerance: Float = 1.5f)` (radial-distance simplification, same as the web)
  - `sealed interface DrawingCommand { fun inverse(): DrawingCommand }` with `CreateDrawing(element)` and `DeleteDrawing(element)`; `class UndoRedoStack { push(c); undo(): DrawingCommand? /* returns the inverse to run */; redo(): DrawingCommand?; canUndo; canRedo }` (`push` clears redo). Undo and redo cover **own drawings only**; ideas are not part of undo. Redo re-creates with the **same id** (`DrawingRequest.id` is accepted).
  - `class Tombstones { fun mark(id: String, nowMillis: Long); fun unmark(id: String); fun isDeleted(id: String, nowMillis: Long): Boolean /* true for 60 s after mark */ }`: the deleted-id memory that stops a late sync from reviving a deleted item. `unmark` is called on redo and whenever the current user creates an element, so a re-created id is never treated as deleted. Task 15 consumes it.

- [ ] **Step 1: Write failing tests.**

```kotlin
@Test fun strokeSegmentHitWithinToleranceMissOutside() {
    val s = stroke(width = 5f, points = listOf(WorldPoint(0f, 0f), WorldPoint(100f, 0f)))
    assertTrue(hitTestStroke(Offset(50f, 5f), s, tolerance = 6f))
    assertFalse(hitTestStroke(Offset(50f, 40f), s, tolerance = 6f))
}
@Test fun onePointStrokeHitsByDistance() { }
@Test fun rectangleInsideAndNearEdgeHit_farMisses() { }
@Test fun circleUsesEllipseWithTolerance() { }
@Test fun triangleInsideHits_outsideAwayFromEdgesMisses() { }
@Test fun topHitReturnsLastElement() { }
@Test fun movedDrawingHitsAndBoxesAtItsOffsetPosition() { }
@Test fun simplifyKeepsFirstAndLastPoint() { }
@Test fun undoOfCreateIsDeleteOfSameElement_pushClearsRedo() { }
@Test fun canUndoFalseWhenEmpty_undoThenRedoRestoresOriginal() { }
@Test fun tombstoneExpiresAfter60Seconds_unmarkClearsItImmediately() { }
```

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*DrawingHitTestTest" --tests "*UndoRedoTest"` — Expected: FAIL.
- [ ] **Step 3: Implement** (plain functions, no Compose).
- [ ] **Step 4: Run** the same tests — Expected: PASS.
- [ ] **Step 5: Commit** `git add mobile-compose && git commit -m "feat(compose): drawing hit-test, simplify and undo stack"`

---

### Task 13: Drawing tools and gesture modes

**Files:**
- Create: `main/features/whiteboard/logic/CanvasTool.kt` (also `toolAllowsCardGestures`), `main/features/whiteboard/ui/widgets/{ToolBar,ColorRow,ShapePicker}.kt`
- Modify: `CanvasViewport.kt`, `DrawingLayer.kt` (preview layer, selection box), `RoomViewModel.kt`, `RoomScreen.kt`
- Test: `test/features/whiteboard/CanvasToolTest.kt`, `test/features/whiteboard/DrawingToolsTest.kt`

**Interfaces:**
- Consumes: `UndoRedoStack`, `topHit`, `DrawingRepository`, `screenToWorld`, `simplifyPoints`.
- Produces:
  - `enum class CanvasTool { Hand, Select, Pen, Highlighter, Shapes, Idea, Eraser }`; `fun toolUsesOneFingerPan(t: CanvasTool): Boolean` (true only for `Hand`); `fun toolAllowsCardGestures(t: CanvasTool): Boolean` (true for `Hand`, `Select`, `Idea`; false for `Pen`, `Highlighter`, `Shapes`, `Eraser`, whose tap is handled by the eraser logic) — passed to `IdeaCard(gesturesEnabled = ...)`
  - `RoomViewModel`: `setTool`, `setShape(kind)`, `setColor(hex)`, `beginDraw(world)`, `updateDraw(world)`, `endDraw()`, `eraseAt(world, toleranceWorld)`, `selectAt(world)`, `dragSelected(deltaWorld)`, `dropSelected()`, `undo()`, `redo()`; the in-progress element is `preview` (not saved until `endDraw`).
  - Behaviour (web rules): `Pen`/`Highlighter` build points and, on `endDraw`, `simplifyPoints`, then `create` once (width 5 / 30, opacity 1 / 0.35, selected colour); `Shapes`: drag from start to end gives `x = min`, `y = min`, `width = max(|dx|, 12)`, `height = max(|dy|, 12)`, payload `{x, y, width, height}`; `Eraser` deletes the top drawing hit (author or room owner, otherwise a transient error "You can only erase your own drawings") or an idea (owner or author); `Select` shows the bounding box (offsets applied) and a drag changes `offsetX`/`offsetY` (one `move` call on drop, registered in `PositionGuard` under `"drawing:<id>"`); it first checks the `drawing:` lock and, if another user holds it, shows "held by X" and does not start the drag; follow the web rule recorded in `contracts.md` for clamping drawing offsets. All points go through `screenToWorld` and are clamped to the world. `Hand` pans and zooms; the other tools use `detectDragGestures` so one finger draws; the `+` / `-` / **Fit** buttons work in every tool.

- [ ] **Step 1: Write failing tests:** `toolUsesOneFingerPan(Hand)` is true and `(Pen)` false; `toolAllowsCardGestures` is true for `Hand`/`Select`/`Idea` and false for `Pen`/`Highlighter`/`Shapes`/`Eraser`; Select on a drawing locked by another user does not start a drag; `beginDraw`, three `updateDraw`, `endDraw` calls `DrawingRepository.create` once with `kind = "pen"`, width 5; highlighter gives width 30 / opacity 0.35; a rectangle drag (10,10)→(110,60) gives payload x 10, y 10, w 100, h 50, and a drag of 3 px gives w and h 12; a pen tap (no move) creates a one-point stroke without throwing; `eraseAt` over another user's drawing (not the owner) does not call `delete`; `undo` after create calls `delete`, `redo` calls `create` with the same id and calls `tombstones.unmark(id)` first; points are clamped to `0..1000` x `0..500`; `dropSelected` calls `move` once with the summed offset.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*CanvasToolTest" --tests "*DrawingToolsTest"` — Expected: FAIL.
- [ ] **Step 3: Implement.** Tool bar icons use the drawables copied in Task 1 (Hand and Select can use Material icons). Colour row and shape picker show only for the tools that need them. All pointer handlers use `pointerInput(tool)` and read the viewport and `canEdit` through `rememberUpdatedState`, following the Gesture handlers trap (tap and drag in separate chained modifiers).
- [ ] **Step 4: Run** the same tests — Expected: PASS.
- [ ] **Step 5: Checkpoint 6 (human):** draw, place shapes, erase, select-and-move, undo/redo; one finger never pans while a drawing tool is active; a finger on an idea card under the Pen draws instead of dragging the card; Hand tool pinch-zoom works; moving a drawing follows the finger at 0.3x and 4x; everything appears on the web after a refresh.
- [ ] **Step 6: Commit** `git add mobile-compose && git commit -m "feat(compose): drawing tools and undo/redo"`

---

### Task 14: `WsService` (connect, auth, ping/pong, reconnect, resume)

**Files:**
- Create: `main/core/network/WsService.kt`, `main/features/whiteboard/model/RoomEvent.kt`
- Test: `test/core/network/WsServiceTest.kt`

**Interfaces:**
- Consumes: `EnvConfig.wsUrl`, `AuthService`, fixtures `ws_*.json`, contracts **WebSocket envelopes**.
- Produces:
  - `sealed interface RoomEvent` with one `data class` per server message: `AuthSuccess`, `Presence(participants)`, `CursorMove`, `CursorLeave`, `CardMove`, `DrawingMove`, `StrokePoints`, `ObjectLocked`, `ObjectUnlocked`, `IdeaCreated/Updated(idea)`, `IdeaDeleted(id)`, `DrawingCreated/Updated(drawing)`, `DrawingDeleted(id)`, `CommentCreated/Updated(ideaId, comment)`, `CommentDeleted(ideaId, commentId)`, `CategoryCreated/Updated(category)`, `CategoryDeleted(id)`, `SocketClosed(code, reason)`; `fun parseRoomEvent(text: String): RoomEvent?` (unknown type or bad JSON → `null`)
  - `interface WsConnector { fun open(url: String, listener: WebSocketListener): WebSocket }` (default: OkHttp; tests use a fake)
  - `class WsService(env, auth, connector, scope)`: `val status: StateFlow<ConnectionStatus>` (`Connected`, `Reconnecting`), `fun connect(roomId: String, onEvent: (RoomEvent) -> Unit)` (a plain callback: no `SharedFlow`, so no event is dropped or buffered by accident; it runs on the OkHttp thread, so the callback may only use `MutableStateFlow.update` or `viewModelScope.launch`, see traps), `fun send(json: String)`, `fun onResync(cb: suspend () -> Unit)` (runs after every reconnect), `fun onAppResumed()`, `fun close()`.

- [ ] **Step 1: Write failing tests** (fake connector and socket).

```kotlin
@Test fun connectsWithTokenInQueryAndSendsAuthMessage() { /* url contains /ws/rooms/r1?token=a1; first sent text has "type":"auth" */ }
@Test fun serverPingIsAnsweredWithPong() { }
@Test fun close4401RefreshesTokenAndReconnectsWithNewToken() { /* refreshCalls == 1; next url has token=a2 */ }
@Test fun close4403And4404EmitSocketClosedAndDoNotReconnect() { }
@Test fun otherCloseReconnectsWithBackoffCappedAtTenSeconds() { /* test scheduler time */ }
@Test fun resumeReconnectsAndRunsResyncOnce() { }
@Test fun malformedFrameIsIgnoredAndStreamStaysOpen() { }
@Test fun closeStopsReconnecting() { }
```

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*WsServiceTest"` — Expected: FAIL.
- [ ] **Step 3: Implement** per the Global Constraints. Parse with kotlinx `JsonObject` and a `when (type)`. Delays use `delay()` in a coroutine so tests use virtual time.
- [ ] **Step 4: Run** the same test — Expected: PASS.
- [ ] **Step 5: Commit** `git add mobile-compose && git commit -m "feat(compose): room websocket service"`

---

### Task 15: Realtime reducer and remote-event applier

**Files:**
- Create: `main/features/whiteboard/logic/{RealtimeState,RemoteApply}.kt`
- Test: `test/features/whiteboard/RealtimeStateTest.kt`, `test/features/whiteboard/RemoteApplyTest.kt`

**Interfaces:**
- Consumes: `RoomEvent`, `CanvasUiState`, `PositionGuard`.
- Produces:
  - `data class Lock(val objectType: String, val id: String, val userId: Long, val userName: String, val expiresAtMillis: Long)`; `data class RemoteCursor(userId, name, color, x, y, lastSeenMillis)`; `data class RealtimeState(locks: Map<String, Lock> /* "card:id" or "drawing:id" */, cursors: Map<Long, RemoteCursor>, liveStrokes: Map<String, LiveStroke>, online: List<Participant>)`
  - `const val LOCK_TTL_MS = 3000L; const val CURSOR_IDLE_MS = 5000L` (cursor idle is chosen here; the docs say nothing)
  - `fun reduce(s: RealtimeState, e: RoomEvent, nowMillis: Long, selfUserId: Long): RealtimeState`; `fun expire(s, nowMillis): RealtimeState`
  - `fun applyRemote(c: CanvasUiState, e: RoomEvent, selfUserId: Long, tombstones: Tombstones, positionGuard: PositionGuard): CanvasUiState` for idea/drawing/category events and `card-move`/`drawing-move` (position/offset by id); `Tombstones` (defined in Task 12)

- [ ] **Step 1: Write failing tests** (payloads from fixtures).

```kotlin
@Test fun lockEventSetsExpiryAndExpireRemovesItAfterThreeSeconds() { }
@Test fun objectUnlockedRemovesLockImmediately() { }
@Test fun cursorRemovedAfterIdle_cursorLeaveRemovesImmediately() { }
@Test fun strokePointsAccumulateByStrokeId_andClearWhenDrawingCreatedWithSameId() { }
@Test fun rf3_ownEventsDoNotCreateLocksOrCursorsForSelf() { }
@Test fun rf3_sameDrawingCreatedTwiceYieldsOneElement() {
    val once = applyRemote(state, drawingCreated, selfUserId = 1, tombstones, positionGuard)
    assertEquals(once.elements.size, applyRemote(once, drawingCreated, 1, tombstones, positionGuard).elements.size)
}
@Test fun rf3_deleteOrUpdateForUnknownIdLeavesStateUnchanged() { }
@Test fun deletedIdIsNotRevivedBySyncWithin60Seconds() { }
@Test fun rf7_undoThenImmediateRedoOfSameIdIsNotBlockedByTombstone() { /* mark(id); unmark(id); the next sync shows the element */ }
@Test fun remoteCardMoveChangesOnlyThatIdea_andRespectsPendingLocalMove() { }
@Test fun presenceEventReplacesOnlineList() { }
```

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*RealtimeStateTest" --tests "*RemoteApplyTest"` — Expected: FAIL.
- [ ] **Step 3: Implement** pure, immutable functions (copy lists, do not mutate). Event type names come from `contracts.md`.
- [ ] **Step 4: Run** the same tests — Expected: PASS.
- [ ] **Step 5: Commit** `git add mobile-compose && git commit -m "feat(compose): realtime state reducers"`

---

### Task 16: Realtime UI wiring (locks, live strokes, cursors, presence, sync loop)

**Files:**
- Create: `main/core/network/Throttler.kt`, `main/features/whiteboard/ui/widgets/CursorOverlay.kt`
- Modify: `RoomViewModel.kt`, `IdeaCard.kt`, `DrawingLayer.kt`, `RoomScreen.kt`, `AppContainer.kt`
- Test: `test/core/network/ThrottlerTest.kt`, `test/features/whiteboard/RealtimeSyncTest.kt`

**Interfaces:**
- Consumes: `WsService`, `reduce`/`expire`/`applyRemote`, `LOCK_TTL_MS`, `RoomRepository.sendHeartbeat` and `sendLeave` (Task 6).
- Produces: `class Throttler(intervalMillis: Long, now: () -> Long) { fun shouldEmit(): Boolean }`; `RoomViewModel` exposes `realtime: StateFlow<RealtimeState>` and `connection: StateFlow<ConnectionStatus>`; outgoing helpers `sendCursor(world)`, `sendCardMove(id, x, y)`, `sendDrawingMove(id, dx, dy)`, `sendStrokePoints(strokeId, ...)`, `sendUnlock(type, id)`; card UI shows a coloured border and "Held by X" badge and refuses drags (Snackbar "currently being moved by X"); remote live strokes are drawn in `DrawingLayer`; remote cursors in `CursorOverlay`; a 1 s tick calls `expire`; the **sync loop** (15 s, also on every WS reconnect via `onResync`, and on resume) reloads ideas, drawings, categories and room, applying the position guard and tombstones; a presence heartbeat runs every 10 s with a `clientId` created once per room visit. Loops are controlled by `startLoops()` / `stopLoops()` (idempotent; each cancels the previous `Job` before launching) and called from a `LifecycleEventObserver` on `ON_START` / `ON_STOP`. `onCleared()` closes the `WsService` and sends `presence/leave` through `AppContainer.appScope` (the `viewModelScope` is already cancelled there; `DisposableEffect.onDispose` is not used because it also fires on rotation).

- [ ] **Step 1: Write failing tests:** `Throttler(50)` emits at t=0, not at t=30, emits at t=50; a card drag sends `card-move` at most once per 120 ms and always sends a final one plus `object-unlock` on drop; a card locked by another user rejects the drag and sends nothing; with the Pen tool selected `IdeaCard` gestures are disabled; a remote `stroke-points` appears in the exposed state; a resync call reloads all lists once and keeps a just-dropped card where it was (RF6); the heartbeat is sent at t=0 and every 10 s with the same `clientId`; calling `startLoops()` twice, or stop then start, leaves exactly one heartbeat, one poll and one sync loop (RF8); `onCleared()` closes the socket and sends leave once through the app scope even though the ViewModel scope is cancelled (fake scope); a configuration change (ViewModel kept) sends no leave; a lock expires 3 s after the last lock event (fake clock).
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*ThrottlerTest" --tests "*RealtimeSyncTest"` — Expected: FAIL.
- [ ] **Step 3: Implement.** Loops are `viewModelScope.launch { while (true) { ...; delay(n) } }`; keep each `Job` in a field. Cursor events are sent only while a finger is down on the canvas; `cursor-leave` on lift.
- [ ] **Step 4: Run** the same tests — Expected: PASS.
- [ ] **Step 5: Checkpoint 7 (human):** web and phone in the same room; card drags, strokes and finger cursors appear on both within about 150 ms; a card held on the web shows "Held by" on the phone; put the phone to sleep for 30 s, wake it: it reconnects and catches up.
- [ ] **Step 6: Commit** `git add mobile-compose && git commit -m "feat(compose): realtime collaboration UI"`

---

### Task 17: Room governance and read-only enforcement

**Files:**
- Create: `main/features/whiteboard/logic/RoomPermissions.kt`, `main/features/whiteboard/ui/{ParticipantsSheet,RoomMenu,RoomBanner}.kt`
- Modify: `RoomViewModel.kt`, `ToolBar.kt`, `IdeaCard.kt`, `IdeasSheet.kt`, `CommentsSheet.kt`, `RoomScreen.kt`
- Test: `test/features/whiteboard/RoomPermissionsTest.kt`, `test/features/whiteboard/GovernanceTest.kt`

**Interfaces:**
- Consumes: `Room`, `UserDto`, `RoomRepository`, `RealtimeState.online`, contracts **Read-only rules**.
- Produces: `enum class RoomBanner { None, Closed, Offline }`; `class RoomPermissions(room: Room, userId: Long) { val canEdit: Boolean /* !closed && !offline */; val isOwner: Boolean; val banner: RoomBanner }`; `RoomViewModel` polls the room every 3 s (single request in flight) and exposes `room`, `permissions` and `exitMessage: StateFlow<String?>` + `exitHandled()` ("You were removed from this room" on 403, "This room was deleted" on 404; the screen navigates home in a `LaunchedEffect`, then calls `exitHandled()`); actions `rename`, `closeOrReopen`, `leave`, `delete` (owner only), `removeMember(memberId)` (owner only); participants sheet lists online users from `presence` plus the room's `members`, creator badge, **Remove** for the owner on others; the menu hides owner actions for members; banner text: "Closed (read-only)" or "Room offline: the owner has left".

- [ ] **Step 1: Write failing tests.**

```kotlin
@Test fun closedRoomIsReadOnlyWithClosedBanner() { }
@Test fun offlineRoomIsReadOnlyWithOfflineBanner_unlikeTheFlutterPlan() { }
@Test fun onlyOwnerCanManage() { }
@Test fun pollOn403SetsRemovedMessage_on404SetsDeletedMessage_andStopsPolling() { /* uses httpCode(); exitHandled() clears it so navigation happens once */ }
@Test fun mutation403RefreshesRoomAndDoesNotEmitRemoved() { }
@Test fun rf5_roomClosesMidStroke_endDrawSendsNothingAndPreviewIsCleared() = runTest {
    vm.beginDraw(Offset(5f, 5f)); vm.updateDraw(Offset(9f, 9f))
    fakeRooms.setRoom(room.copy(closed = true)); vm.pollOnce()
    vm.endDraw()
    assertEquals(0, fakeDrawings.createCalls); assertNull(vm.state.value.preview)
}
@Test fun rf5_closedRoomCancelsCardDragAndDrawingDragWithoutSendingMove() { }
@Test fun everyMutatingViewModelMethodChecksCanEditFirst() { }
```
Plus a UI test (device): with `canEdit = false` the tool bar tools (except Hand), the Idea tool, vote, editor, comment input are disabled.

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*RoomPermissionsTest" --tests "*GovernanceTest"` — Expected: FAIL.
- [ ] **Step 3: Implement.** Every mutating ViewModel method starts with `if (!permissions.canEdit) return`. When `canEdit` flips to false, discard any preview, active drag and open editor. Use the exact close/open/reopen calls recorded in `contracts.md`.
- [ ] **Step 4: Run** the same tests — Expected: PASS.
- [ ] **Step 5: Checkpoint 8 (human):** close the room on web → the phone becomes read-only within about 3 s; reopen → editing returns without restarting; remove the phone user on web → the phone returns home with the message; owner leaves → "Room offline" banner.
- [ ] **Step 6: Commit** `git add mobile-compose && git commit -m "feat(compose): room governance and read-only mode"`

---

### Task 18: Resilience, haptics, branding

**Files:**
- Create: `main/ui/{ConnectionErrorView,LoadingView,EmptyView}.kt`
- Modify: `HomeScreen.kt`, `RoomScreen.kt`, `ToolBar.kt`, `IdeaCard.kt`, `app/src/main/res/` (launcher icon), `AndroidManifest.xml`
- Test: `app/src/androidTest/.../ConnectionErrorViewTest.kt`

**Interfaces:**
- Produces: `ConnectionErrorView(hostIp: String, onRetry: () -> Unit)` (message names `HOST_IP`) shown when `isConnectionError(t)` is true (an `IOException`); an `HttpException` shows a normal error message instead; `LoadingView` (`CircularProgressIndicator`) while rooms/canvas load; empty states ("Your rooms will appear here after you create one", "No ideas yet"); `LocalHapticFeedback` on card lock, vote toggle and tool selection; adaptive launcher icon (Image Asset Studio, coral "M" on ink) and `Theme.MindSpark` splash colours; connection dot in the top bar (green / amber).

- [ ] **Step 1: Write failing test:** `ConnectionErrorView` shows the host IP and calls `onRetry` on tap; Home shows it when the repository throws `IOException` and shows a plain error (not the connection view) for an `HttpException`; an empty list shows the empty text.
- [ ] **Step 2: Run** `./gradlew connectedDebugAndroidTest --tests "*ConnectionErrorViewTest"` (device) — Expected: FAIL.
- [ ] **Step 3: Implement** the Interfaces.
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest` (full unit suite) and `./gradlew lintDebug` — Expected: all PASS, no new errors.
- [ ] **Step 5: Commit** `git add mobile-compose && git commit -m "feat(compose): resilience, haptics, branding"`

---

## Appendix A: Verified API and protocol facts (from the repo)

Re-checked against the backend in Task 2. IDs are UUID strings except user `id` and category `id` (Long).

| Area | Facts |
|---|---|
| User | `POST /api/users/sync` → `{id, username, email}`; `GET /api/health` |
| Rooms | `GET /api/rooms`; `GET /api/rooms/{id}`; `POST /api/rooms {name}`; `PUT /api/rooms/{id} {name}`; `POST /api/rooms/join {invitationCode}`; `POST /api/rooms/{id}/reopen`, `/close`, `/open`; `DELETE /api/rooms/{id}/leave`; `DELETE /api/rooms/{id}/members/{memberId}`; `DELETE /api/rooms/{id}`. Response: `id, name, invitationCode, owner, createdAt, members[], memberCount, member, closed, offline` |
| Presence | `POST /api/rooms/{id}/presence {clientId}` every 10 s; `POST /api/rooms/{id}/presence/leave {clientId}`; owner "offline" = owner was seen, then expired or left |
| Categories | `GET`/`POST /api/rooms/{id}/categories` (`{title}`), `PUT`/`DELETE /api/categories/{id}`; title max 40; owner manages (UI rule) |
| Ideas | `GET /api/rooms/{id}/ideas?categoryId&search&sortBy=createdAt\|voteCount`; `POST /api/rooms/{id}/ideas`; `PUT`/`DELETE /api/ideas/{id}`; `POST`/`DELETE /api/ideas/{id}/vote`. Body `{text, title, description, x, y, color, categoryId}` (web sends `title` and `text` equal). Response adds `author, voteCount, voted, category, createdAt, updatedAt`. Content edits need the author; position-only update does not |
| Comments | `GET /api/ideas/{id}/comments` (replies nested in `replies`); `POST /api/ideas/{id}/comments`; `POST /api/comments/{id}/replies`; `PUT`/`DELETE /api/comments/{id}`; body `{content, text}`; max 2000 |
| Drawings | `GET`/`POST /api/rooms/{id}/drawings`; `PUT /api/rooms/{id}/drawings/{drawingId} {offsetX, offsetY}`; `DELETE` (author or room owner). Body `{id?, kind: pen\|highlighter\|rectangle\|circle\|triangle, color, width, opacity, payload (JSON string), offsetX?, offsetY?}`. Stroke payload = point array (or `{points}`); shape payload `{x, y, width, height}` |
| Closed / offline | All mutations in a closed or offline room return 403 |
| WS client → server | `auth {token}`, `pong`, `cursor-move {x,y}`, `cursor-leave`, `card-move {id,x,y}`, `stroke-points {strokeId,color,width,opacity,points}`, `drawing-move {id,offsetX,offsetY}`, `object-unlock {objectType,id}` |
| WS server → client | `auth.success`, `ping`, `presence {participants:[{userId,name}]}`, `cursor-move` (+`userId,name,color`), `cursor-leave`, `card-move` (+`by`), `drawing-move` (+`by`), `stroke-points` (+`userId`), `object-locked {objectType,id,userId,name}`, `object-unlocked`, `idea-created/updated/deleted`, `drawing-created/updated/deleted`, `comment-created/updated/deleted`, `category-created/updated/deleted` (REST-driven events go to everyone, including the sender; relays skip the sender) |
| WS closes | `4400` bad room id, `4401` auth/token, `4403` forbidden, `4404` room not found, `4408` ping timeout |

## Self-review notes

- **Task map vs the Flutter plan:** Compose Tasks 0-18 mirror Flutter Tasks 0-18 one to one (same order, same checkpoints 0-8), so both clients can be tracked side by side.
- **Differences found in the web code that the Flutter plan does not cover** (worth checking the Flutter client too): categories with server-side search, filter and sort; drawings are saved with an `opacity`, a JSON `payload` and an `offsetX/offsetY`, and can be moved (Select tool, `drawing-move`, drawing locks); ideas have no comment count; idea moves are saved with `PUT /api/ideas/{id}`; the server sends `ping` and expects `pong`; closing, reopening, removal and rename have **no WebSocket event**, so the web polls (3 s); an owner-offline room is **read-only** (the Flutter plan keeps it editable); web undo/redo is local-only, while this plan makes undo/redo of own drawings server-backed.
- **Decisions this plan fixes:** app in `mobile-compose/`; `applicationId` `com.mindspark.compose` so it can be installed beside the Flutter app; zoom `0.3..4`; Hand tool for pan/zoom (instead of two-finger zoom during drawing, to keep gesture code basic); `cursorIdle = 5 s`; web throttle values; tokens in plain `SharedPreferences` (known limitation); token refresh by plain OkHttp call to Keycloak instead of AppAuth refresh.
- **Review Focus** RF1-RF8 each have a test in Tasks 4, 10/13, 15, 8/9, 17, 10/13, 15, 16.
- **Revision 3 (workflow review fixes):** Exceptions table gains `graphicsLayer`, `@Synchronized`, clipboard/haptics/`BuildConfig`; gesture trap rewritten (`pointerInput(tool)`, one `detect*Gestures` per block, separate tap and drag modifiers); `JAVA_HOME` / `local.properties` note; Task 1 configures the existing project, pure-JVM `EnvConfig`, pinned versions with OkHttp 4.12.x; Task 2 records presence TTL and hit-test/simplify rules; Task 15 `applyRemote` takes a `PositionGuard`.
- **Revision 2 (external review fixes):** idea `(x, y)` is the card centre with a fixed card size; world box uses `requiredSize`; `hostIp` only enforced for assemble/install; keyed and factory-built ViewModels; leave-on-exit through `appScope` in `onCleared`; tombstone `unmark` for redo; `HttpException` vs `IOException`; callback instead of `SharedFlow`; idempotent loops; one-shot events via `StateFlow` + handled call; gesture rules (`rememberUpdatedState`, coordinate spaces, tool vs card); one `PositionGuard` for ideas and drawings; AppAuth with direct endpoints, async token exchange, `prompt=login`, cancel/error handling; transient action errors via `Snackbar`; fixtures via classloader; pinned official serialization converter and Kotlin plugins.
