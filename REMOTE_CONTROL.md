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
│  │ (OpenGL)    │                    │
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
| `:app` | Java | Existing Cthugha application (current root project becomes this) |
| `:remote-ui` | TypeScript / React | Static SPA; built by npm/Vite; output bundled into `:app` jar |

The `:app` build depends on `:remote-ui` so that the SPA's `dist/` output is copied into `src/main/resources/remote/` before `processResources` and packaged into the jar.

---

## Security Model

- **Plain HTTP** — transport is unencrypted. This is intentional: the feature is LAN-only and avoids self-signed certificate browser warnings. Do not expose the server port externally.
- **Bearer token** — a random 256-bit value encoded as URL-safe Base64. Every API request (except the static SPA files) must supply it as `Authorization: Bearer <token>`.
- **Token lifecycle** — pressing `R` regenerates the token instantly. All clients holding the old token lose access immediately.
- **Multi-client** — multiple browsers may connect simultaneously with the same valid token. No scaling guarantees beyond what a single JVM thread pool can handle.
- **No persistence** — the token is ephemeral and never written to disk.

---

## Token Flow

```
User presses R
      │
      ▼
Generate new 256-bit token  (SecureRandom → URL-safe Base64)
Invalidate previous token
      │
      ▼
Compose URL:  http://<local-ip>:<port>/?token=<base64-token>
      │
      ▼
Render QR code as OpenGL overlay on the visualisation
QR auto-hides after qr_display_seconds (if non-zero)
      │
      ▼
User scans QR on phone → browser opens → SPA loads
SPA makes first authenticated API request
      │
      ▼
Server detects first successful authentication → immediately hides QR overlay
      │
      ▼
SPA stores token in memory (NOT localStorage) and attaches it to all API calls
SPA strips the token from the URL bar via history.replaceState
```

"First successful authentication" is defined as the first request that passes the bearer token check after the QR was displayed. This hides the QR promptly once someone has scanned it, regardless of the auto-hide timeout.

---

## HTTP Server

**Library:** Javalin (embeds Jetty). Chosen for clean routing, built-in SSE support, and minimal boilerplate.

**Port:** configurable in `cthugha.ini`; default `8080`.

**Startup:** the server starts when `remote.enabled = true` in `cthugha.ini`. It binds to `0.0.0.0` (all interfaces) so phones on the same LAN can reach it.

### Authentication

A Javalin `before` filter applies to all paths except `GET /` and `GET /static/**`:

```
Authorization: Bearer <token>
```

Missing or invalid token → `401 Unauthorized` with body `{"error": "invalid_token"}`.

---

## Parameter Model Extensions

Two new fields are added to `AbstractValue` for remote control purposes.

### `controlled` flag

The animation system (`Animator` / `AnimatorPool`) drives parameters continuously. A remote client writing to such a parameter would fight the animator and produce no visible effect. Rather than blocking writes, we surface this state so the UI can communicate it clearly.

`AbstractValue` gains two methods:

```java
/** True when an animator (or any other internal system) currently owns this parameter. */
boolean isControlled();

/** Called by AnimatorPool when it binds/unbinds a parameter. */
void setControlled(boolean controlled);
```

`AbstractValue` holds an `AtomicBoolean controlled` field (default `false`). `AnimatorPool` calls `param.setControlled(true)` when it starts driving a parameter and `param.setControlled(false)` when it releases it.

### `uiHint` field

An optional hint that tells the remote UI which widget to use for a numeric parameter. Only meaningful on `DOUBLE`, `INTEGER`, and `LONG` leaf nodes; ignored on `BOOLEAN` and `ENUM` (their widget is implied by type).

```java
public enum UiHint { SLIDER, KNOB }

// in AbstractValue:
private UiHint uiHint = UiHint.SLIDER;  // default

public UiHint getUiHint() { return uiHint; }
public AbstractValue withUiHint(UiHint hint) { this.uiHint = hint; return this; }
```

Each component sets its preferred hint at construction time using the fluent setter, e.g.:

```java
new DoubleParameter("amplitude", 0.0, 1.0, 0.5).withUiHint(UiHint.KNOB)
```

| `uiHint` | Widget | Best for |
|----------|--------|---------|
| `SLIDER` (default) | Horizontal slider + numeric readout | Linear ranges, counts, levels |
| `KNOB` | Rotary knob + numeric readout | Amplitude, frequency, pan, anything "dial-like" |

### API exposure

Both fields appear in all leaf-node JSON responses:

```jsonc
{
  "name": "speed",
  "type": "DOUBLE",
  "value": 0.42,
  "min": 0.0,
  "max": 1.0,
  "controlled": true,   // ← animator currently owns this
  "uiHint": "KNOB"      // ← render as a rotary knob
}
```

`PATCH /api/v1/params/{path}` **accepts** writes even when `controlled = true` — the write goes through to the parameter value. Whether the animator then immediately overwrites it on the next tick is the animator's business; the API does not interfere. The UI is responsible for showing the control as read-only when `controlled` is `true`.

### SSE events for control state changes

When `controlled` changes (animator binds or unbinds), an SSE event is emitted:

```
event: paramControlled
data: {"path": "flame/speed", "controlled": true}
```

This lets the UI update the greyed-out state in real time without polling.

---

## REST API

Base path: `/api/v1/`

### Parameter Tree

#### `GET /api/v1/params`
Returns the full parameter tree as JSON.

```jsonc
{
  "name": "root",
  "type": "CONTAINER",
  "children": [
    {
      "name": "flame",
      "type": "CONTAINER",
      "children": [
        {
          "name": "speed",
          "type": "DOUBLE",
          "value": 0.42,
          "min": 0.0,
          "max": 1.0,
          "controlled": false,
          "uiHint": "SLIDER"
        },
        {
          "name": "amplitude",
          "type": "DOUBLE",
          "value": 0.8,
          "min": 0.0,
          "max": 1.0,
          "controlled": true,
          "uiHint": "KNOB"
        }
      ]
    }
  ]
}
```

#### `GET /api/v1/params/{path}`
Returns a single node by slash-delimited path, e.g. `/api/v1/params/flame/speed`.
Returns `404` if the path does not exist.

#### `PATCH /api/v1/params/{path}`
Sets the value of a leaf node.

```json
{ "value": 0.75 }
```

Returns the updated node JSON. Writes succeed even when `controlled = true`; in that case the response includes a `warning` field:

```jsonc
{
  "name": "speed",
  "type": "DOUBLE",
  "value": 0.75,
  "min": 0.0,
  "max": 1.0,
  "controlled": true,
  "warning": "controlled_by_animator"
}
```

Returns `404` if the path doesn't exist, `400` if the node is a container (not a leaf) or the value is out of range.

#### `POST /api/v1/params/{path}/randomise`
Calls `node.randomise(rng)` on the specified node. Works on containers (randomises all descendants) or individual leaves.

### Server-Sent Events

#### `GET /api/v1/events`
Long-lived SSE stream. Clients declare which parts of the parameter tree they want pushed to them via one or more `path` query parameters:

```
GET /api/v1/events?path=flame&path=wave/amplitude
```

- Each `path` value is matched as a **prefix**: subscribing to `flame` receives updates for `flame/speed`, `flame/blur`, etc.
- Multiple `path` parameters are ORed — a change matching any subscribed prefix is delivered.
- Omitting `path` entirely subscribes to **all** parameter changes (useful for debugging or a "show everything" view).

The server maintains a `Map<SseClient, List<String>>` of active connections to their subscribed prefixes. When a parameter value or `controlled` flag changes, the server checks each client's prefix list before sending — clients that have not subscribed to that path receive nothing.

To change the subscription (e.g. the user navigates to a different view in the SPA), the client closes the current SSE connection and opens a new one with updated `path` parameters.

| Event name | Trigger | Payload |
|---|---|---|
| `paramChanged` | A subscribed leaf value changes | `{"path": "flame/speed", "value": 0.75}` |
| `paramControlled` | `controlled` flag changes on a subscribed leaf | `{"path": "flame/speed", "controlled": true}` |
| `tokenRotated` | User presses R | `{}` — clients should treat this as a disconnect signal |
| `ping` | Every 15 s | `{}` |

`ping` and `tokenRotated` are always sent regardless of subscriptions.

On `tokenRotated`, the SSE connection is closed server-side immediately after the event is flushed. Clients that receive this event know their token is now invalid and should display a "reconnect by scanning the QR again" message.

### Server Info

#### `GET /api/v1/info`
Returns server metadata. Does **not** require authentication (safe to call to check liveness).

```json
{
  "version": "1.0",
  "appVersion": "..."
}
```

---

## React SPA (`remote-ui`)

### Build

- **Vite + React + TypeScript**
- Output: `dist/` → copied into `:app` jar at `resources/remote/`
- The Javalin server serves `index.html` at `GET /` and static assets under `/static/`

### Token Bootstrap

On load, the SPA:
1. Reads `?token=<value>` from the URL.
2. Stores it in a module-level variable (not `localStorage` — avoids stale tokens persisting across token rotations).
3. Calls `history.replaceState` to strip the token from the URL bar (prevents accidental sharing via browser history/screenshots).
4. If no token is present, shows a "Scan the QR code on the Cthugha screen" message.

### UI Structure

**Parameter Tree view**
- Collapsible tree mirroring the `Node` hierarchy.
- Leaf nodes render as type-appropriate controls:
  - `DOUBLE` / `INTEGER` / `LONG` with `uiHint: "SLIDER"` → horizontal slider + numeric readout (Radix UI `Slider` via shadcn)
  - `DOUBLE` / `INTEGER` / `LONG` with `uiHint: "KNOB"` → rotary knob + numeric readout (React Knob Headless)
  - `BOOLEAN` → toggle switch
  - `ENUM` → dropdown / segmented control
- When `controlled = true`, the control is visually greyed out and interaction is disabled. The current value is still displayed and kept live via SSE.
- Value changes are sent on `input` events for sliders (debounced ~150 ms) and immediately for toggles/dropdowns.
- The full tree is fetched once via `GET /api/v1/params` on load. The SSE connection is opened with `?path=<root>` (subscribe to everything) so all live changes are reflected.
- When the user collapses a subtree the SPA does **not** reconnect — the subscription is intentionally over-broad at the root level for the tree view, since the user may expand any branch at any time.

**Quick Controls view** *(v2)*
- User-pinnable subset of parameters for fast access.

**Presets** *(stretch goal)*
- Named snapshots of the full parameter tree, stored in the browser.

### Disconnect / Token Rotation Handling

On receiving a `tokenRotated` SSE event (or a `401` from any API call):
- All controls are disabled.
- A full-screen overlay appears: "Session ended — scan the QR code to reconnect."

---

## QR Code Rendering

**Library:** [nayuki/qrcodegen](https://github.com/nayuki/QR-Code-generator) Java port — zero dependencies, MIT licensed, ~30 KB jar.

The `QrCode` object's `getModules()` grid is rendered as a monochrome OpenGL texture overlay, centred on screen with a white quiet zone border.

**Hide triggers (whichever comes first):**
1. The first authenticated API request arrives — QR hides immediately.
2. `--remote-qr-timeout` seconds elapse (if non-zero).

Pressing `R` again rotates the token and shows a fresh QR regardless of the current overlay state.

---

## Key Binding

| Key | Action |
|-----|--------|
| `R` | Rotate token + display QR overlay |

The `R` binding is **only registered when the remote server is active** (i.e. `--remote` was passed on the command line). If remote is disabled the key is simply absent — no error, no message.

Registered in `CthughaWindow.registerKeys()` alongside existing bindings.

---

## Gradle Multi-project Structure

The current single-project build is split into a two-project build:

```
cthugha-reborn/              ← settings.gradle root (no build.gradle here)
├── app/                     ← current source tree moved here
│   ├── build.gradle         ← current build.gradle (adjusted)
│   └── src/
├── remote-ui/               ← new React sub-project
│   ├── build.gradle         ← wraps npm tasks
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
└── settings.gradle          ← includes ':app', ':remote-ui'
```

### `:remote-ui` build.gradle

Defines two tasks:

- `npmInstall` — runs `npm ci` (only if `node_modules` is absent or `package-lock.json` changed).
- `npmBuild` — runs `npm run build`, depends on `npmInstall`. Output: `dist/`.

### `:app` build.gradle

```groovy
def remoteUiBuild = project(':remote-ui').tasks.named('npmBuild')

processResources {
    dependsOn remoteUiBuild
    from(project(':remote-ui').file('dist')) {
        into 'remote'
    }
}
```

The SPA assets land at `resources/remote/` and are served from the classpath by Javalin.

---

## Enabling the Remote Server

The remote server is **opt-in via a command-line flag**:

```
cthugha --remote [--remote-port=8080] [--remote-qr-timeout=30]
```

| Flag | Default | Meaning |
|------|---------|---------|
| `--remote` | (absent = disabled) | Start the embedded HTTP server |
| `--remote-port=N` | `8080` | TCP port to bind |
| `--remote-qr-timeout=N` | `30` | Seconds before QR overlay auto-hides (0 = never) |

When `--remote` is absent the server does not start, no port is bound, and the `R` key binding is not registered. Optional tuning values (`port`, `qr-timeout`) may also be placed in `cthugha.ini` under `[remote]` as defaults, with command-line flags taking precedence.

---

## Implementation Notes

### SSE Broadcaster

A `RemoteEventBroadcaster` class holds a `CopyOnWriteArrayList<SseClient>`. Each entry carries the client's subscribed prefix list alongside the Javalin `SseClient` handle. `AbstractValue` gains a change-listener hook; `AnimatorPool` also calls it when the `controlled` flag changes. On each change event, `RemoteEventBroadcaster` iterates the list, checks prefix membership, and sends to matching clients only. Dead connections (client disconnected) are removed lazily on the next send attempt.
