# Parameter System

The `params` package provides a hierarchical, typed, runtime-tunable parameter tree used throughout Cthugha Reborn. Every major component exposes its tuneable state as nodes in this tree, which is then driven by the animation system, exposed via keyboard bindings, and pushed to remote browser clients over SSE.

---

## Node tree structure

Nodes follow the **Composite** pattern. Interior nodes group child nodes; leaf nodes hold values or represent actions.

```
Node (interface)
├─ ParamNode               — composite grouping node (NodeType.CONTAINER)
│  ├─ AbstractValue           — leaf: bounded numeric value
│  │  ├─ BooleanParameter     — on/off flag (0 or 1 numerically)
│  │  ├─ DoubleParameter      — bounded double; supports range remapping
│  │  ├─ IntegerParameter     — bounded int
│  │  ├─ LongParameter        — bounded long
│  │  └─ ObjectParameter      — arbitrary object stored as an index
│  │     └─ EnumParameter     — specialisation for enum types / ordered lists
│  ├─ StringValue             — leaf: mutable String (no min/max)
│  │  └─ StringParameter
│  ├─ ContainerNode           — anonymous grouping wrapper (no logic)
│  └─ AbstractAction          — leaf: invokable operation (NodeType.ACTION)
└─ NodeType                   — enum discriminator (CONTAINER, BOOLEAN, DOUBLE, …, ACTION)
```

Sub-packages:

| Package | Contents |
|---|---|
| `params.values` | Concrete typed leaf nodes |
| `params.action` | Action system: `Action`, `AbstractAction`, `ActionContext` |
| `params.transform` | 2-D transform composites: `XYParam`, `PerspectiveParams`, `TransformParams` |

---

## Defining parameters

Declare child parameters as `public` fields. The no-arg constructor discovers them automatically via reflection:

```java
public class MyRenderer extends ParamNode {
    public final DoubleParameter speed = new DoubleParameter("Speed", 0.0, 10.0, 1.0);
    public final BooleanParameter enabled = new BooleanParameter("Enabled", true);

    // no-arg: name = simple class name, children discovered from public Node fields
    public MyRenderer() {}
}
```

Or call `initChildren(...)` explicitly from a named constructor:

```java
public MyRenderer(String name) {
    super(name);
    initChildren(speed, enabled);
}
```

---

## Actions

Actions are invokable leaf nodes. They appear as buttons in the remote UI and can be bound to keyboard shortcuts.

```java
new AbstractAction("Save preset", ctx -> {
    if (ctx instanceof TabActionContext tctx) {
        tctx.tabStore().save(tctx.currentBuffer(), tctx.resolution());
        ctx.notify("Saved");
    }
});
```

`ActionContext` is intentionally minimal (`notify`, `rng`). Domain-specific sub-interfaces in each package extend it:

| Interface | Package | Adds |
|---|---|---|
| `TabActionContext` | `tab` | `tabStore()`, `registry()`, `currentBuffer()`, `loadTabBuffer()` |
| `PaletteActionContext` | `map` | `currentPalette()`, `loadPalette()` |
| `CthughaActionContext` | `display` | top-level composite; implements all sub-interfaces |

Action bodies cast to the sub-interface they need. Sensitive actions (e.g. Quit) should be marked `withNoRemote()`.

---

## Change events

Two layers of change notification propagate outward from leaf value nodes:

```
AbstractValue.setValue(...)
  │
  ├─ fires AbstractValue.changeListeners   (leaf-level, Runnable)
  │    └─ used by RemoteEventBroadcaster to enqueue per-client SSE events
  │
  └─ fires ParamNode.fireSubtreeListeners(path)
       └─ bubbles up through every ancestor in the tree
            └─ used by RemoteEventBroadcaster to subscribe to subtrees
```

**Leaf listeners** (`AbstractValue.addChangeListener`) notify when a single parameter changes.

**Subtree listeners** (`ParamNode.addSubtreeListener`) notify any ancestor when any descendant changes, receiving the full slash-delimited path of the changed node. The remote SSE broadcaster attaches one subtree listener per connected browser client.

---

## Animation system

`AnimationSystem` and `AnimationBinding` (in `animation/`) drive any `AbstractValue` from a render-core `WaveFunction`. Each frame, `AnimationBinding.tick()`:

1. Evaluates the sine wave at the current `frequency` and `phase` parameters.
2. Maps the `[-1, 1]` output to `[0, 1]`.
3. Calls `target.setNormalisedValue(...)`, which maps the fraction linearly to `[min, max]`.

`AbstractValue.setControlled(true)` is called when a binding is active; the remote UI uses this to grey out controlled parameters.

```java
// Wire a parameter to a 0.3 Hz sine wave
animationSystem.addBinding("Speed oscillator", myRenderer.speed, 0.3);
```

---

## Remote API exposure

Every node in the tree is serialised to JSON by `ParamSerializer` and exposed via the Javalin HTTP server. Two mechanisms gate visibility:

- **`isRemoteAllowed()`** — returns `false` after calling `withNoRemote()`. The serialiser omits the node from the JSON payload; the server returns 403 for any direct request targeting it.
- **`UiHint`** — key/value strings attached via `withUiHint(key, value)` that tell the React UI how to render a node (widget type, icon, whether to skip children inline, etc.).

```java
myParam
    .withUiHint(UiHint.CONTROL_TYPE, UiHint.SLIDER)
    .withUiHint(UiHint.ICON, "rotate-cw");

sensitiveAction.withNoRemote();
```

A node can also carry an optional, purely descriptive `description` string via `withDescription(...)`,
included in the JSON payload when non-blank. It's for humans, not code — nothing parses or relies on
it. Because `withDescription`/`withUiHint`/etc. return `ParamNode` (not the concrete subtype), chain
them as separate statements rather than on a typed field declaration:

```java
public DoubleParameter zoom = new DoubleParameter("Zoom", 50, 4000, 250);

public MyGenerator() {
    // ...
    zoom.withDescription("Pixels per unit of the complex plane. Higher = more magnified.");
}
```

The remote UI renders it as a tap-to-expand info toggle next to the control (`InfoButton` in
`remote-ui/src/components`) rather than a hover tooltip, since the primary client is a phone.

See `REMOTE_CONTROL.md` for the full remote API design.

A third, independent gate — `isPersistExcluded()` / `withNoPersist()` — controls whether a node is
included when the whole tree is captured as a named "screen config" snapshot. It's deliberately
kept separate from `getUiHints()` since that map is serialised to the browser on every tree fetch,
while persistence exclusion is a backend-only concern. See `SCREEN_CONFIGS.md`.

---

## Data flow summary

```
Keyboard binding
  └─ Action.execute(ctx)
       └─ modifies state directly

AnimationSystem.tick()  (once per render frame)
  └─ AnimationBinding.tick()
       └─ AbstractValue.setNormalisedValue(...)
            └─ setValue(...)
                 ├─ AbstractValue.changeListeners  → RemoteEventBroadcaster (enqueue SSE)
                 └─ ParamNode.subtreeListeners  → RemoteEventBroadcaster (per subtree SSE)

RemoteServer  (Javalin HTTP)
  ├─ GET  /params          → ParamSerializer serialises full tree to JSON
  ├─ POST /params/{path}   → resolves node by path, calls setValue / execute
  └─ SSE  /events          → RemoteEventBroadcaster flushes queued events per client
```
