# Remote Control Design Specification

## Overview

The remote control feature allows a phone or tablet on the same local network to control Cthugha's runtime parameters via a browser-based UI. A lightweight embedded HTTP server exposes the parameter tree as a REST API; a React single-page application (served from the same server) provides the UI. Access is secured with a rotating bearer token displayed as a QR code on screen.

---

## Architecture

```
┌─────────────────────────────────────┐
│          Cthugha JVM process        │
│                                     │
│  ┌─────────────┐  ┌──────────────┐  │
│  │ Parameter   │  │ Embedded     │  │
│  │ Tree (Node) │◄─│ HTTP Server  │  │
│  └─────────────┘  │   (Javalin)  │  │
│                   └──────┬───────┘  │
│  ┌─────────────┐         │          │
│  │ Token Store │◄────────┘          │
│  └─────────────┘                    │
│  ┌─────────────┐                    │
│  │ QR Overlay  │                    │
│  │ (OpenGL,    │                    │
│  │  QrPhase)   │                    │
│  └─────────────┘                    │
└─────────────────────────────────────┘
           ▲  HTTP (LAN only)
           │
    ┌──────┴──────┐
    │  Browser    │
    │  React SPA  │
    └─────────────┘
```

### Sub-projects (Gradle multi-project build)

| Module | Language | Purpose |
|--------|----------|---------|
| `:app` | Java | The Cthugha application |
| `:remote-ui` | TypeScript / React | Static SPA; built by npm/Vite; output bundled into `:app` jar |

`:app`'s `processResources` task depends on `:remote-ui:npmBuild` and copies the SPA's `dist/` output into `resources/remote/` before packaging into the jar. Javalin serves it from the classpath at runtime (`cfg.staticFiles.add("/remote", Location.CLASSPATH)`).

---

## Security Model

- **Plain HTTP** — transport is unencrypted. This is intentional: the feature is LAN-only and avoids self-signed certificate browser warnings. Do not expose the server port externally.
- **Bearer token** — a random 256-bit value encoded as URL-safe Base64 (`TokenStore.rotate`). Every API request under `/api/` (except `GET /api/v1/info` and `GET /api/v1/maps/preview/*`, both intentionally public) must supply it either as `Authorization: Bearer <token>` or as a `?token=` query parameter — the latter exists because browsers' `EventSource` cannot set custom headers, so SSE connections rely on the query param.
- **Token lifecycle** — invoking the "Rotate Token" action (bound to `R` by default) regenerates the token instantly via `TokenStore.rotate`. All clients holding the old token lose access immediately; comparison uses `MessageDigest.isEqual` (constant-time).
- **Multi-client** — multiple browsers may connect simultaneously with the same valid token. The embedded Jetty thread pool is deliberately small (`min_threads`/`max_threads` in `[remote]`, defaults 2/8) since the server is expected to serve at most a couple of phones, not a public audience.
- **No persistence** — the token lives only in `TokenStore`'s in-memory field and is never written to disk.

---

## Token Flow

```
Server starts (remote.enabled = true)
      │
      ▼
Generate initial 256-bit token  (SecureRandom → URL-safe Base64)
Compose URL:  http://<detected-ip>:<port>/?token=<base64-token>
Render QR code as OpenGL overlay (QrPhase / QrOverlay) — shown immediately at startup
      │
      ▼
User scans QR on phone → browser opens → SPA loads
SPA reads ?token= from the URL into an in-memory module variable (never localStorage)
SPA makes API/SSE requests using that token
      │
      ▼
Server's auth filter detects the first request that passes the token check
  → invokes the registered onFirstAuth callback → QrPhase.hide()
      │
      ▼
User presses R (or triggers "Remote / Rotate Token" from any already-connected client)
      │
      ▼
TokenStore.rotate() issues a new token; RemoteServer.resetFirstAuth() re-arms the latch
QrPhase.show(newUrl) redisplays the QR overlay
broadcaster.broadcastAll("tokenRotated", "{}") tells every connected SSE client its token is dead
      │
      ▼
SPA, on receiving `tokenRotated` (or any 401), fires a `session-expired` browser event,
clears its token and shows a full-screen "scan the QR code to reconnect" overlay
```

Unlike an opt-in "press a key to start advertising" model, the server starts and shows its QR code automatically whenever remote control is enabled — there is no separate "go visible" step.

The QR overlay also has its own auto-hide timeout (`qr_display_seconds` in `[remote]`, default 30s in code but set to `true`/unset per `cthugha.ini`'s shipped values); whichever of "someone authenticated" or "timeout elapsed" happens first hides it.

---

## HTTP Server

**Library:** Javalin (embeds Jetty). Chosen for clean routing, built-in SSE support, and minimal boilerplate.

**Port:** configurable via `[remote] port` in `cthugha.ini`; the shipped default is `8363` (an homage to the Amiga).

**Startup:** `CthughaWindow` starts the server when `remoteConfig.enabled` is true. It binds to all interfaces via `app.start(config.port)`; the QR URL's host is whichever local IP `NetworkUtils.detectBaseUrl` resolves (optionally pinned to a specific NIC via `[remote] network_interface`, e.g. `wlp59s0`).

### Authentication

A Javalin `before` filter (`RemoteServer.authFilter`) applies to every path under `/api/` except `GET /api/v1/info` and `GET /api/v1/maps/preview/*`. Static SPA assets under `/remote/**` and the SPA root `/` are never gated (they contain no state).

Missing or invalid token → `401` with body `{"error": "invalid_token"}`. The first request that *does* pass the check after a token rotation fires the `onFirstAuth` callback exactly once (latched by an `AtomicBoolean`, re-armed by `resetFirstAuth()` on the next rotation).

---

## Parameter Model Extensions

### `controlled` flag

The animation system (`AnimationBinding`) drives parameters continuously. A remote client writing to such a parameter would fight the animator and produce no visible effect. Rather than blocking writes, the parameter surfaces this state so the UI can communicate it clearly.

`AbstractValue` (`params/AbstractValue.java`) holds the flag directly:

```java
boolean isControlled();
void setControlled(boolean controlled);
```

`AnimationBinding` calls `target.setControlled(true)` when it starts driving a parameter each tick and `target.setControlled(false)` when the binding is removed (see `animation/AnimationBinding.java`).

`PATCH /api/v1/params/{path}` still accepts writes even when `controlled = true` — the write goes through to the parameter value; whether the animation binding then overwrites it on the next tick is its own business. When this happens the response includes `"warning": "controlled_by_animator"`. The remote UI (`ParamLeaf.tsx`) greys out the control and shows a lock icon whenever `controlled` is true, but always keeps the live value visible via SSE.

### UI hints — `Map<String, String>`, not a single enum

Rather than one `uiHint` enum field, every `Node` carries an open `Map<String, String>` (`Node.getUiHints()`, populated via `ParamNode.withUiHint(key, value)`). Keys and well-known values are defined in `params/UiHint.java`:

| Key | Meaning |
|---|---|
| `control-type` | Which widget to render. Values: `SLIDER` (default for numeric leaves), `KNOB`, `CAROUSEL` (enum with prev/next arrows and optional image preview per option), `TABS` (container renders its container children as a horizontal tab strip), `EXPANDER` (a `TABS` child that renders below the strip as a collapsible section instead), `CODE_EDITOR` (a `StringNode` renders as a resizable script editor) |
| `skip-children` | `"true"` → remote UI should not render this container's children inline |
| `hidden` | `"true"` → remote UI omits this node entirely (e.g. actions kept only for keyboard-binding lookups) |
| `icon` | A Lucide icon name (kebab-case, e.g. `"music"`, `"volume-2"`, `"wifi"`) shown beside the label |
| `scale` | Applies to `SLIDER`/`KNOB`: `"log"` maps the control's position through a power curve (`1-(1-t)^3`) for finer resolution near the top of the range; absent = linear |

```java
zoom.withUiHint(UiHint.CONTROL_TYPE, UiHint.KNOB)
    .withUiHint(UiHint.ICON, "zoom-in");
```

This map is serialised wholesale as `uiHints` on every node in the JSON tree (see `ParamSerializer`), so it is deliberately kept separate from the backend-only `isRemoteAllowed()` / `isPersistExcluded()` flags — see `PARAMS.md` for the rationale.

### `description` — on-demand hint, not a tooltip

`Node.getDescription()` / `ParamNode.withDescription(String)` attaches an optional human-readable explanation to any node. It is purely descriptive — nothing parses or relies on it — and is included in the serialised JSON as `description` whenever non-blank. Many parameters across the tree now carry one.

The remote UI renders it as a tap-to-expand info toggle (`InfoButton.tsx`, used by `ParamLeaf.tsx`, `ActionButton.tsx`, and `StringLeaf.tsx`) rather than a hover tooltip, since the primary client is a phone, where hover doesn't exist.

### API exposure

Leaf-node JSON responses look like:

```jsonc
{
  "name": "speed",
  "type": "DOUBLE",
  "value": 0.42,
  "min": 0.0,
  "max": 1.0,
  "controlled": true,          // ← animation binding currently owns this
  "uiHints": { "control-type": "KNOB", "icon": "gauge" },
  "description": "How fast the flame spreads, in buffer-heights per second."
}
```

`ENUM` leaves additionally carry an `options` array of `{label, preview?}` (the `preview` URL, when present, is served by the unauthenticated `GET /api/v1/maps/preview/*` endpoint — used for palette-map thumbnails).

### Change events

`AbstractValue.setValue(...)` fires `ParamNode.fireSubtreeListeners(path)`, which bubbles up through every ancestor. `RemoteEventBroadcaster.register()` attaches one `SubtreeChangeListener` per subscribed node per connected SSE client. See `PARAMS.md` for the full change-event data flow and `SCREEN_CONFIGS.md` for how the same tree is captured/replayed as named snapshots — both build on this same `Node`/`ParamNode` foundation.

---

## REST API

Base path: `/api/v1/`

### Parameter Tree

#### `GET /api/v1/params`
Returns the full parameter tree as JSON, rooted at the top-level container. Nodes that fail `isRemoteAllowed()` are omitted entirely (recursively).

#### `GET /api/v1/params/{path}`
Returns a single node by slash-delimited path, e.g. `/api/v1/params/Wave/Oscilloscope/amplitude`. Returns `404` if the path does not exist.

#### `PATCH /api/v1/params/{path}`
Sets the value of a leaf node.

```json
{ "value": 0.75 }
```

- `StringValue` / `StringParameter` nodes take a string `value`; if the node is a `CompilableValue` (e.g. an animation-script string) and compilation fails, the response includes a `compileError` field but the raw text is still stored.
- Numeric `AbstractValue` nodes take a numeric `value`; out-of-range values return `400 value_out_of_range`.
- Returns `404` if the path doesn't exist, `403` if `isRemoteAllowed()` is false, `400` if the node is a container or neither a `StringValue` nor an `AbstractValue`.
- Writes succeed even when `controlled = true`; the response then includes `"warning": "controlled_by_animator"`.

#### `POST /api/v1/params/{path}/execute`
Invokes an `Action` node (`Action.execute(actionContext)`). Returns `{}` on success, `400 not_an_action` if the target isn't an action, `403 not_allowed` if `isRemoteAllowed()` is false, `404` if the path doesn't exist. Path may be empty (root) or a nested path, e.g. `Configs/my_favourite/Load/execute`.

#### `POST /api/v1/params/{path}/randomise`
Calls `node.randomise(rng)` on the specified node (a fresh `java.util.Random` per call). Works on containers (randomises all descendants) or individual leaves. Returns the updated node JSON for a leaf, `{}` for a container.

### Palette Preview Images

#### `GET /api/v1/maps/preview/{name}`
Unauthenticated. Serves a pre-rendered PNG preview of a `.MAP` palette file from `maps/{name}.MAP.png`, used as the `preview` thumbnail for palette `EnumParameter` options in the UI's `CAROUSEL` control. `404` if the file doesn't exist.

### Server-Sent Events

#### `GET /api/v1/events`
Long-lived SSE stream (`app.sse`, kept alive via Javalin's `client.keepAlive()`). Clients declare which subtrees they want pushed to them via one or more `path` query parameters, e.g. `?path=Wave&path=Tab/Translate%20Source`. Each value is resolved to a `Node` and, if it's a `ParamNode`, a `SubtreeChangeListener` is attached to it for the lifetime of the connection (`RemoteEventBroadcaster.register`). Omitting `path` entirely subscribes to the whole tree.

Because `EventSource` cannot set request headers, the bearer token is passed as `?token=` on this endpoint (see Security Model above).

**Delivery is batched, not synchronous.** A `SubtreeChangeListener` fires on the render thread and only enqueues a `{path, value, controlled}` snapshot into a per-client pending map (last-write-wins — rapid repeated changes to the same param collapse to one event). A dedicated `sse-flusher` daemon thread drains each client's pending map and performs the actual `sendEvent` I/O at a fixed interval, configurable via `[remote] animation_broadcast_interval_ms` (default `100` = 10 fps) — this keeps the render thread free of network I/O and throttles the update rate of animated parameters, which would otherwise change every frame.

| Event name | Trigger | Payload |
|---|---|---|
| `paramChanged` | A subscribed leaf's value or `controlled` flag changes | `{"path": "Wave/Oscilloscope/amplitude", "value": 0.75, "controlled": false}` |
| `tokenRotated` | The token is rotated (`R` / Remote → Rotate Token) | `{}` — the SPA treats this as a session-expired signal |
| `treeChanged` | The tab-generator tree structure changes (e.g. `GeneratorRegistry` swaps in a different generator) | `{}` — the SPA re-fetches `GET /api/v1/params` |

There is no separate `paramControlled` event — a change to `controlled` alone is delivered on the same `paramChanged` event as the value, since both are captured together per change. `tokenRotated` and `treeChanged` are sent to every connected client via `broadcaster.broadcastAll(...)`, bypassing the per-client subscription/batching path entirely. There is no dedicated `ping` event; Javalin's `client.keepAlive()` handles the underlying connection heartbeat.

### Server Info

#### `GET /api/v1/info`
Unauthenticated. Returns `{"version": "1.0"}`. Safe to call to check liveness before a token is known.

---

## React SPA (`remote-ui`)

### Build

- **Vite + React + TypeScript**, styled with Tailwind utility classes; Radix UI primitives (`Collapsible`, `Tabs`) plus `lucide-react` icons and small bespoke controls under `components/controls/` (`SliderControl`, `KnobControl`, `ToggleControl`, `EnumControl`, `CarouselControl`).
- Output: `dist/` → copied into `:app`'s jar at `resources/remote/`.
- Javalin serves `index.html` at `GET /` (`spaRoot.addFile`) and static assets under `/remote/` (`staticFiles.add`).

### Token Bootstrap (`token.ts`)

On load, `initToken()`:
1. Reads `?token=<value>` from the URL and stores it in a module-level variable — never `localStorage`, so a stale token can't survive a rotation across page reloads.
2. If no token is present, `App.tsx` renders a "Scan the QR code displayed on screen to connect" prompt instead of the parameter tree.

Every subsequent API call (`api.ts`) attaches `Authorization: Bearer <token>` when a token is known; a `401` response dispatches a `session-expired` window event, which `App.tsx` listens for to clear state and show the reconnect overlay.

### UI Structure

- **`ParamTree.tsx`** — fetches the full tree once via `GET /api/v1/params` on mount, re-fetching (without a loading spinner) on a `tree-changed` event.
- **`ParamContainer.tsx`** — collapsible tree node (Radix `Collapsible`). When expanded, opens an SSE subscription scoped to that container's own path (`useSSE([path])`) so all descendant changes are caught without over-subscribing collapsed branches. A container with `uiHints['control-type'] === 'TABS'` instead renders via `TabsContainer.tsx`.
- **`TabsContainer.tsx`** — renders a container's `CONTAINER` children as a horizontal tab strip (Radix `Tabs`), except children hinted `EXPANDER`, which render below the strip as ordinary collapsible sections. Only the active tab's subtree is SSE-subscribed.
- **`ParamLeaf.tsx`** — renders the type/hint-appropriate control (`SliderControl`, `KnobControl`, `ToggleControl`, `EnumControl`, or `CarouselControl` for enums with `control-type=CAROUSEL`). Shows a lock icon and disables the control when `controlled` is true; live value/`controlled` state comes from the container's SSE subscription, overriding the initially-fetched value.
- **`StringLeaf.tsx`** — plain text input, or (when `control-type=CODE_EDITOR`) a multi-line script editor with a dirty-state indicator, Cancel/Update buttons, an inline script-reference help panel, and `compileError` display.
- **`ActionButton.tsx`** — button that calls `POST .../execute`, with a busy spinner while in flight.
- **`InfoButton.tsx`** — shared tap-to-expand toggle used by all three leaf components above to reveal a node's `description`.
- Nodes hinted `hidden: "true"` are filtered out of rendering entirely; `NodeIcon.tsx` maps an `icon` hint string to a `lucide-react` component.

### Disconnect / Token Rotation Handling

On a `tokenRotated` SSE event (`useSSE.ts`) or any `401` from `api.ts`, a `session-expired` window event fires: `App.tsx` clears its "has token" state and shows a full-screen "Session ended — scan the QR code to reconnect" overlay. Separately, `useSSE.ts` also tracks raw `EventSource` connect/error state and fires `connection-lost` / `connection-restored` events (with a 3s debounce on loss, to avoid flashing on transient hiccups), driving a distinct "app unreachable" overlay.

---

## QR Code Rendering

**Library:** [nayuki/qrcodegen](https://github.com/nayuki/QR-Code-generator) Java port — zero dependencies, MIT licensed.

`QrOverlay` (`remote/QrOverlay.java`) renders the `QrCode`'s module grid as a monochrome RGBA OpenGL texture, centred on screen at 2/3 of the smaller screen dimension, scaled to an integer pixel-per-module factor for crisp edges. It optionally blits a logo (`/logo/cthugha.png`) into the centre, sized as a configurable percentage of the QR area (`qr_logo_size` in `[remote]`, 0–30%); the error-correction level is chosen automatically based on that percentage (`MEDIUM` ≤15%, `QUARTILE` ≤25%, `HIGH` above that) so the code stays scannable despite the occlusion. `QrPhase` (`display/phase/QrPhase.java`) is a thin `RenderPhase` wrapper that renders `QrOverlay` during the screen pass and exposes thread-safe `show(url)` / `hide()` proxies; it is only added to the phase list when remote control is enabled.

**Hide triggers (whichever comes first):**
1. The first authenticated API/SSE request arrives — `RemoteServer`'s `onFirstAuth` callback calls `QrPhase.hide()`.
2. `qr_display_seconds` seconds elapse since the overlay was shown (checked every frame in `QrOverlay.doRender`; 0 = never auto-hide).

Rotating the token (see Token Flow) always redisplays the QR overlay via `QrPhase.show(url)`, regardless of its current visibility.

---

## Key Binding

Remote control has no hardcoded key binding in code. Instead, the server registers an ordinary `Action` node (`General/Remote/Rotate Token`) in the parameter tree, and `[keys]` in `cthugha.ini` maps a physical key to it like any other action:

```ini
[keys]
R = General/Remote/Rotate Token
```

If `remote.enabled` is false the `Remote` node (and its action) is never created, so the binding — even if still listed in `[keys]` — has nothing to invoke.

---

## Gradle Multi-project Structure

```
cthugha-reborn/              ← settings.gradle root (no build.gradle here)
├── app/                     ← Java application
│   ├── build.gradle
│   └── src/
├── remote-ui/                  ← React sub-project
│   ├── build.gradle          ← wraps npm tasks
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
└── settings.gradle          ← includes ':app', ':remote-ui'
```

### `:remote-ui` build.gradle

Two `Exec` tasks under the `base` plugin:

- `npmInstall` — runs `npm ci`; only runs when `package-lock.json` exists (`onlyIf`).
- `npmBuild` — runs `npm run build`, depends on `npmInstall`. Output: `dist/`.

### `:app` build.gradle

```groovy
processResources {
    dependsOn ':remote-ui:npmBuild'
    from(project(':remote-ui').file('dist')) {
        into 'remote'
    }
}
```

The SPA assets land at `resources/remote/` and are served from the classpath by Javalin.

---

## Enabling the Remote Server

Unlike an opt-in flag, the remote server is **enabled by default** in the shipped `cthugha.ini` and disabled via a command-line override:

```ini
[remote]
enabled = true
port = 8363
network_interface = wlp59s0
qr_logo_size = 10
```

```
cthugha [--no-remote] [--remote-port=N] [--remote-qr-timeout=N] [--remote-interface=NAME] [--remote-token=VALUE]
```

| `[remote]` ini key | CLI flag | Default | Meaning |
|---|---|---|---|
| `enabled` | `--no-remote` (forces off) | `false` (code default; shipped ini sets `true`) | Start the embedded HTTP server |
| `port` | `--remote-port=N` | `8080` (code default; shipped ini sets `8363`) | TCP port to bind |
| `qr_display_seconds` | `--remote-qr-timeout=N` | `30` | Seconds before the QR overlay auto-hides (0 = never) |
| `qr_logo_size` | — | `0` | Logo area as % of QR modules (0–30) |
| `network_interface` | `--remote-interface=NAME` | auto-detect | NIC whose IP is advertised in the QR URL; trusts the named interface even if the OS marks it virtual (e.g. USB ethernet adapters) |
| `token` | `--remote-token=VALUE` | random per-rotation | Pins the bearer token to a fixed value instead of a random one, so the same `?token=` URL keeps working across app restarts. **Local development only** — a fixed token is not secret-strength and defeats the point of rotation; never set this for a machine reachable beyond your own dev LAN. |
| `min_threads` / `max_threads` | — | `2` / `8` | Jetty thread pool bounds |
| `animation_broadcast_interval_ms` | — | `100` | SSE flush interval (see Change Events above) |

When `token` is set, `TokenStore.rotate` (called at startup and by the "Rotate Token" action) always (re)sets the token back to the fixed value rather than drawing new randomness — so pressing `R` redisplays the QR code but the URL/token itself doesn't change.

`RemoteConfig.parse` reads the ini first, then lets CLI flags override individual fields (`--no-remote` short-circuits `enabled` to `false` regardless of the ini). When the server is disabled, no port is bound, `QrPhase` is never added to the render phase list, and the `Remote` param-tree node (and its `Rotate Token` action) is never created.

---

## Implementation Notes

### SSE Broadcaster

`RemoteEventBroadcaster` (`remote/RemoteEventBroadcaster.java`) holds a `CopyOnWriteArrayList<ClientRecord>`, one per connected SSE client. Each `ClientRecord` pairs the Javalin `SseClient` handle with the list of `SubtreeChangeListener`s it installed (one per subscribed `ParamNode`) and a `ConcurrentHashMap<String, PendingEvent>` of not-yet-flushed changes.

A listener fires synchronously on the render thread when a subscribed descendant changes, but does no I/O — it just overwrites the pending map entry for that path (last-write-wins). A dedicated `sse-flusher` daemon thread (`startFlushing(intervalMs)`) drains and sends each client's batch at the configured interval, keeping all socket I/O off the render thread. `broadcastAll` bypasses this queue entirely for rare, must-arrive-now system events (`tokenRotated`, `treeChanged`). Terminated clients are detected via `SseClient#terminated()` before each send (skipping calls that would otherwise just log a duplicate Javalin warning) and pruned lazily via `unregister` on send failure or `client.onClose`.
