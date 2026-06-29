# Remote Control — Implementation Plan

Full spec: [REMOTE_CONTROL.md](REMOTE_CONTROL.md)

Work is split into six phases, each independently buildable and testable. Phases 3 and 5 can be developed in parallel once Phase 1 is complete.

---

## Phase 1 — Gradle Multi-project Restructure

_Prerequisite for everything. No functional change._

| # | Task |
|---|------|
| 1.1 | Create `settings.gradle` at repo root: `include ':app', ':remote-ui'` |
| 1.2 | Create `app/` directory; move `src/`, `build.gradle`, `src/dist/` into it |
| 1.3 | Update paths in `app/build.gradle` (workingDir, startScripts, etc.) |
| 1.4 | Move `gradlew`, `gradle/` wrapper files to root (they already live there — verify) |
| 1.5 | Create `remote-ui/` skeleton with a stub `build.gradle` that defines empty `npmInstall` and `npmBuild` tasks |
| 1.6 | Add `processResources` wiring in `app/build.gradle` to depend on `:remote-ui:npmBuild` and copy `remote-ui/dist/` → `resources/remote/` |
| 1.7 | Verify `./gradlew :app:run` still works end-to-end |

---

## Phase 2 — Parameter Model Extensions

_Adds `controlled` flag and `uiHint` to `AbstractValue`. No UI or server work yet._

| # | Task |
|---|------|
| 2.1 | Create `params/UiHint.java` enum: `SLIDER`, `KNOB` |
| 2.2 | Add `AtomicBoolean controlled` field to `AbstractValue`; add `isControlled()` / `setControlled(boolean)` |
| 2.3 | Add `UiHint uiHint = UiHint.SLIDER` field to `AbstractValue`; add `getUiHint()` and fluent `withUiHint(UiHint)` |
| 2.4 | Add a value-change listener hook to `AbstractValue`: `addChangeListener(Runnable)` / `removeChangeListener(Runnable)`, called on every value write in each `AbstractValue` subclass |
| 2.5 | Wire `AnimatorPool`: call `param.setControlled(true)` on bind, `param.setControlled(false)` on release — also fires the change listener so SSE picks up the state change |
| 2.6 | Annotate existing `DoubleParameter`, `IntegerParameter`, etc. constructors in each component with appropriate `.withUiHint(UiHint.KNOB)` where dial-like behaviour is intended |
| 2.7 | Unit tests: verify `controlled` toggling, `uiHint` default + override, listener notification |

---

## Phase 3 — Java Remote Server

_The embedded HTTP server, token management, SSE broadcaster, and REST API._

### 3a — Dependencies

| # | Task |
|---|------|
| 3a.1 | Add `io.javalin:javalin` to `app/build.gradle` |
| 3a.2 | Add `com.fasterxml.jackson.core:jackson-databind` (Javalin's default JSON engine) |
| 3a.3 | Add `io.nayuki:qrcodegen` (or vendor the single Java source file — it has no build system) |

### 3b — Core infrastructure

| # | Task |
|---|------|
| 3b.1 | `remote/RemoteConfig.java` — parses `--remote`, `--remote-port=N`, `--remote-qr-timeout=N` from `String[] args`; exposes `boolean enabled`, `int port`, `int qrTimeoutSeconds` |
| 3b.2 | `remote/TokenStore.java` — holds current token as `String`; `rotate()` generates a new 256-bit `SecureRandom` value encoded as URL-safe Base64 and returns the new URL; `validate(String token)` checks equality |
| 3b.3 | `remote/RemoteEventBroadcaster.java` — holds `CopyOnWriteArrayList<ClientRecord>` where `ClientRecord` pairs a Javalin `SseClient` with `List<String> prefixes`; `register(SseClient, List<String>)` / `unregister(SseClient)`; `broadcast(String eventName, String jsonPayload)` iterates clients, checks prefix match, sends, removes dead connections lazily; `broadcastAll(String, String)` bypasses prefix check (used for `ping` and `tokenRotated`) |

### 3c — JSON serialisation

| # | Task |
|---|------|
| 3c.1 | `remote/ParamSerializer.java` — converts a `Node` to a `Map<String,Object>` (recursively): `name`, `type`, `children` for containers; `name`, `type`, `value`, `min`, `max`, `controlled`, `uiHint` for leaves |
| 3c.2 | Helper `pathOf(Node)` — walks `getPath()` ancestors to produce a slash-delimited string (needed for SSE payloads and for routing PATCH responses) |

### 3d — REST routes (Javalin)

| # | Task |
|---|------|
| 3d.1 | `remote/RemoteServer.java` — creates and configures a `Javalin` instance |
| 3d.2 | Auth `before` filter: skip `GET /`, `GET /static/*`; otherwise validate `Authorization: Bearer <token>` via `TokenStore`; on first valid request after QR display, signal `QrOverlay` to hide |
| 3d.3 | `GET /api/v1/info` — returns `{"version":"1.0","appVersion":"..."}` (no auth required) |
| 3d.4 | `GET /api/v1/params` — serialise root `Node` tree |
| 3d.5 | `GET /api/v1/params/{path}` — look up node by path segments; 404 if absent |
| 3d.6 | `PATCH /api/v1/params/{path}` — parse `{"value":...}`; validate leaf + range; set value; return node JSON with optional `"warning":"controlled_by_animator"` |
| 3d.7 | `POST /api/v1/params/{path}/randomise` — call `node.randomise(rng)` |
| 3d.8 | `GET /api/v1/events` — open SSE; parse `path` query params; register with `RemoteEventBroadcaster`; on client close unregister |
| 3d.9 | Static file serving: serve classpath `resources/remote/` at `/`; `index.html` as SPA catch-all |

### 3e — Wiring into CthughaWindow

| # | Task |
|---|------|
| 3e.1 | Parse `RemoteConfig` from `main(String[] args)` in `CthughaWindow` |
| 3e.2 | If `enabled`: instantiate `TokenStore`, `RemoteEventBroadcaster`, `RemoteServer`; start server |
| 3e.3 | Wire `AbstractValue` change listener → `RemoteEventBroadcaster.broadcast("paramChanged", ...)` for value changes and `"paramControlled"` for controlled-flag changes |
| 3e.4 | Register `R` key in `registerKeys()` only when `enabled`: calls `TokenStore.rotate()`, passes new URL to `QrOverlay`, starts timeout timer |
| 3e.5 | On app shutdown: stop Javalin server |

---

## Phase 4 — QR Code Overlay

_Renders the access URL as an OpenGL texture overlay._

| # | Task |
|---|------|
| 4.1 | `remote/QrOverlay.java` — given a URL string, calls `QrCode.encodeText(url, Ecc.MEDIUM)` and extracts the module grid into a `ByteBuffer` (1 byte per module: 0x00 or 0xFF) |
| 4.2 | Upload to a `GL_R8` texture sized to the QR module count; render as a fullscreen-centred quad with a white border (quiet zone). Scale up with `NEAREST` filtering so modules are crisp pixels |
| 4.3 | `show(String url)` / `hide()` — toggle a `visible` boolean; `show` also resets the auto-hide countdown |
| 4.4 | Auto-hide: in the render loop, decrement a countdown each frame (based on elapsed time); call `hide()` when it expires (if `qrTimeoutSeconds > 0`) |
| 4.5 | Auth-triggered hide: `RemoteServer` auth filter calls `qrOverlay.hide()` on first successful authentication after `show()` |
| 4.6 | Integrate into `CthughaWindow.render()`: draw `QrOverlay` after the palette renderer, before buffer swap |

---

## Phase 5 — React SPA (`remote-ui`)

_The browser UI. Can be developed in parallel with Phase 3 once Phase 1 is done._

### 5a — Project scaffold

| # | Task |
|---|------|
| 5a.1 | `npm create vite@latest remote-ui -- --template react-ts` inside `remote-ui/` |
| 5a.2 | Install Tailwind CSS + shadcn/ui and initialise |
| 5a.3 | Install `react-knob-headless` |
| 5a.4 | Implement `remote-ui/build.gradle`: `npmInstall` task (runs `npm ci`, inputs `package-lock.json`, output `node_modules`); `npmBuild` task (runs `npm run build`, output `dist/`) |
| 5a.5 | Configure Vite `base: '/'` and output to `dist/` |

### 5b — API & token layer

| # | Task |
|---|------|
| 5b.1 | `src/token.ts` — module-level `let token: string \| null`; `initToken()` reads `?token=` from URL, stores it, calls `history.replaceState` to strip it |
| 5b.2 | `src/api.ts` — typed fetch wrapper: attaches `Authorization: Bearer <token>` header; exports `getParams()`, `getParam(path)`, `patchParam(path, value)`, `randomise(path)`, `getInfo()` |
| 5b.3 | `src/useSSE.ts` — custom hook: opens `EventSource` to `/api/v1/events?path=...`; parses `paramChanged` and `paramControlled` events; exposes a state map of `path → {value, controlled}`; reconnects with new subscription paths when prop changes; closes on unmount |

### 5c — Type definitions

| # | Task |
|---|------|
| 5c.1 | `src/types.ts` — TypeScript interfaces mirroring the Java JSON: `ParamNode` (container), `LeafNode` (with `value`, `min`, `max`, `controlled`, `uiHint: 'SLIDER' \| 'KNOB'`), `NodeType` union |

### 5d — UI components

| # | Task |
|---|------|
| 5d.1 | `SliderControl` — wraps shadcn `Slider`; debounced `onValueChange` at ~150 ms; numeric readout; disabled when `controlled` |
| 5d.2 | `KnobControl` — wraps `react-knob-headless`; SVG arc rendering; numeric readout; disabled when `controlled` |
| 5d.3 | `ToggleControl` — shadcn `Switch` for `BOOLEAN` nodes |
| 5d.4 | `EnumControl` — shadcn `Select` for `ENUM` nodes |
| 5d.5 | `ParamLeaf` — dispatches to the correct control based on `type` + `uiHint`; shows lock icon when `controlled` |
| 5d.6 | `ParamContainer` — collapsible section (shadcn `Collapsible`); renders children recursively |
| 5d.7 | `ParamTree` — fetches root via `GET /api/v1/params` on mount; opens SSE for all paths; renders `ParamContainer` at the root level |

### 5e — App shell & session handling

| # | Task |
|---|------|
| 5e.1 | `App.tsx` — calls `initToken()`; shows "Scan the QR code" screen if no token; otherwise renders main layout |
| 5e.2 | Bottom tab bar: **Tree** (v1) \| **Quick Controls** (v2 placeholder) |
| 5e.3 | Disconnect overlay: on `tokenRotated` SSE event or any `401` response, show full-screen "Session ended — scan the QR code to reconnect" overlay and disable all controls |
| 5e.4 | Dark theme via Tailwind config |

---

## Phase 6 — Integration & Manual Testing

| # | Task |
|---|------|
| 6.1 | `./gradlew :app:run --args="--remote"` — verify server starts, port printed to console |
| 6.2 | Press `R` in-app — verify QR overlay appears on screen |
| 6.3 | Open URL manually in browser — verify SPA loads, token accepted, QR hides |
| 6.4 | Adjust a slider/knob — verify parameter changes in the running visualisation |
| 6.5 | Verify `controlled` params appear greyed out when animator is active |
| 6.6 | Press `R` again — verify old browser tab shows disconnect overlay; new QR appears |
| 6.7 | Test on a physical phone: scan QR with camera, verify end-to-end flow |
| 6.8 | Verify `./gradlew :app:run` (without `--remote`) starts normally with no server and no `R` key binding |

---

## Dependency Map

```
Phase 1 (restructure)
    └── Phase 2 (param model)
            ├── Phase 3 (Java server)  ─┐
            │       └── Phase 4 (QR)   ├── Phase 6 (integration)
            └── Phase 5 (React SPA)   ─┘
```

Phases 3 and 5 can proceed in parallel. Phase 4 requires Phase 3's `TokenStore` and `RemoteServer` auth hook.
