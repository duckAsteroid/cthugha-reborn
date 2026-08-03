# Key Input, Cross-Thread Actions, and Colours

## Key input

```java
// Alphabetic keys (char-based factory):
ctx.getKeyRegistry().registerKeyAction(
    KeyCombination.simple('A'),
    () -> { /* runs on GL thread when A is pressed */ },
    "Human-readable description for help display");

// Alphabetic key with modifiers:
ctx.getKeyRegistry().registerKeyAction(
    KeyCombination.simpleWithMods('A', "SHIFT"),
    () -> { /* Shift+A */ },
    "Description");

// Non-alphabetic keys (name-based factory — uses the GLFW constant with GLFW_KEY_ stripped):
ctx.getKeyRegistry().registerKeyAction(
    KeyCombination.named("PRINT_SCREEN"),
    this::captureNextFrame,
    "Save screenshot");

// Non-alphabetic key with modifiers:
ctx.getKeyRegistry().registerKeyAction(
    KeyCombination.namedWithMods("PRINT_SCREEN", "SHIFT"),
    () -> startRecording(Duration.ofSeconds(5)),
    "Record 5s video");
```

`KeyCombination.named(String)` looks up the GLFW key by the name after stripping `GLFW_KEY_`
(e.g. `"PRINT_SCREEN"` → `GLFW_KEY_PRINT_SCREEN`, `"ESCAPE"` → `GLFW_KEY_ESCAPE`).
Use `named`/`namedWithMods` for any key that doesn't have a printable character equivalent.

---

## GLWindow published actions

`GLWindow` exposes protected action methods that `registerKeys()` should bind rather than accessing
internal state directly. This keeps all key bindings in one place while GLWindow owns the logic.

| Method | What it does |
|---|---|
| `exit()` | Request a clean shutdown |
| `toggleClock()` | Pause/unpause the render clock |
| `stepClock(double seconds)` | Advance (+) or rewind (−) the clock by N seconds |
| `toggleFullscreen()` | Toggle between windowed and fullscreen |
| `resetWindowSize()` | Restore the window to its original size |
| `scaleWindowUp()` | Double the window size |
| `scaleWindowDown()` | Halve the window size |

```java
// Typical wiring in registerKeys():
kr.registerKeyAction(GLFW_KEY_ESCAPE, this::exit,         "Exit");
kr.registerKeyAction(GLFW_KEY_SPACE,  this::toggleClock,  "Pause/unpause the clock");
kr.registerKeyAction(GLFW_KEY_LEFT,   () -> stepClock(-10.0),  "Rewind 10 s");
kr.registerKeyAction(KeyCombination.namedWithMods("LEFT", "SHIFT"), () -> stepClock(-100.0), "Rewind 100 s");
```

`GLWindow` registers **no** key bindings. All bindings — including `PRINT_SCREEN` (screenshot),
`SHIFT+PRINT_SCREEN` (recording), clock control, and window management — are wired in
`Main.registerKeys()`. This keeps every key mapping visible in one place.

---

## Cross-thread render actions (RenderActionQueue)

Use this when a non-GL thread (e.g. key callback, audio thread) needs to mutate GL state.
Singleton types keep only the most recent enqueued action, discarding stale intermediate values.

```java
// Fields:
private static final String ACTION_COLOUR = "colour";  // singleton — only latest matters
private static final String ACTION_FLASH  = "flash";   // non-singleton — every event matters
private final RenderActionQueue renderActions =
    new RenderActionQueue(ACTION_COLOUR);               // pass singleton names to constructor

// From any thread:
renderActions.enqueue(ACTION_COLOUR, ctx -> uColour.set(newColour));
renderActions.enqueue(ACTION_FLASH,  ctx -> triggerFlash());

// In doRender() — must be called every frame:
renderActions.processAll(ctx);
```

---

## Colours

`StandardColors` is a pre-built enum of common RGBA colours:

```java
// As Vector4f:
Vector4f red  = StandardColors.RED.color;
Vector4f half = StandardColors.BLUE.withAlpha(0.5f);

// All values (useful for random colour selection):
StandardColors[] all = StandardColors.values();
Vector4f random = all[rng.nextInt(all.length)].color;
```
