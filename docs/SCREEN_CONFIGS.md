# Screen Configs

## Overview

A **screen config** is a named snapshot of the entire visual setup — wave renderers, their
animations, blur, the active palette, and the active tab (translation) generator with its own
params — captured from the live parameter tree and restored on demand. It is the "fast preset"
feature: dial in a look, hit Save, give it a name, and recall it later with one click, from a
different render resolution if you like.

It lives in the `screenconfig` package and is exposed as the **Configs** tab alongside Wave, Tab,
Render, and General.

---

## Why not just reuse tab presets?

`TabStore` (see `tab/TabStore.java`) already saves named presets, but only for a single
`TabGenerator`'s own params — it does not touch wave amplitude, blur, the active palette, or
animation bindings. A screen config is one level up: it snapshots the *whole* tree, and treats
"which tab generator is selected, with which params" as just one part of that snapshot rather
than the whole thing.

---

## Resolution independence

Screen configs never store pixel data. The active tab generator's params are captured like any
other leaf value (e.g. `Tab/Translate Source/Mandelbrot/Zoom`); on load they're replayed back into
the live `TabGenerator` instance, which regenerates its translation map at whatever resolution is
currently active via the existing `TabGenerator.generate(width, height, rng)` path. Loading the
same config at 1280×720 and at 4000×2000 produces the same *shape*, scaled to the buffer — there
is no cached, resolution-bound binary the way `TabStore`'s `.tab` files work.

---

## Capturing and applying the tree

`ScreenConfigParams` (the core utility) does the actual tree walking:

```
capture(root)
  └─ walks root's subtree in child order
       ├─ AbstractValue leaf  → record path → Number
       ├─ StringValue leaf    → record path → String
       ├─ node.isPersistExcluded() → skip this subtree entirely
       └─ container           → recurse into children
  └─ returns a LinkedHashMap<String, Object>, insertion-ordered to match the walk
```

The insertion order is not incidental — it is the whole reason `apply()` is safe:

```
apply(root, params)
  for each (path, value) in params, IN CAPTURE ORDER:
      target = root.getChild(path.split("/"))   ← fresh lookup, every time
      if target is AbstractValue and value is Number → target.setValue(value)
      if target is StringValue   and value is String → target.setValue(value)
      (unresolved or type-mismatched paths are silently ignored)
```

### Why per-entry re-resolution, not one recursive descent

Some nodes restructure their own children when a value changes. `GeneratorRegistry` (the "Tab"
group's active-generator selector) is the clearest example: setting its `Generator` enum swaps in
a different `TabGenerator` instance as a child, live, via `rebuildChildren()`. If `apply()` walked
the tree once recursively — holding an iterator over a container's children while applying its own
selector value — that selector's side effect would mutate the very children list the walk is
mid-iteration over.

Because the captured entries are already in tree order (a selector is always captured *before* the
subtree it selects, since it's registered as the earlier sibling), replaying them one at a time
with a fresh `getChild()` lookup sidesteps this entirely: applying `Tab/Translate Source/Generator`
first triggers the rebuild, and the very next entries — which target paths like
`Tab/Translate Source/Mandelbrot/Zoom` — resolve correctly against the *post-rebuild* tree.

This is covered by `ScreenConfigParamsTest.appliesThroughSelectorDrivenTreeRestructuring()`, which
exercises the real `GeneratorRegistry`, not a mock.

---

## Excluding transient state: `persistExclude`

Not every leaf in the tree belongs in a snapshot. A "Save Name" text field is UI state, not part
of the look. `Node` carries a persistence flag parallel to (but independent of) the existing
remote-visibility flag:

| Mechanism | Purpose | Default | Setter |
|---|---|---|---|
| `isRemoteAllowed()` | Hide/reject a node over the remote HTTP API | `true` | `withNoRemote()` |
| `isPersistExcluded()` | Skip a node when capturing/applying a screen config | `false` | `withNoPersist()` |
| `getUiHints()` | Presentation hints for the remote React UI (widget type, icon, …) | `{}` | `withUiHint(key, value)` |

These are deliberately three separate mechanisms rather than one shared map: `getUiHints()` is
serialised wholesale to the browser on every tree fetch (`ParamSerializer`), so folding
persistence-only metadata into it would either leak backend-only concerns over the wire or require
filtering logic to strip them back out. `persistExclude` stays backend-only.

`persistExclude` is set at container roots, not leaf-by-leaf, and is inherited implicitly by never
descending into an excluded subtree — so a new leaf param added under an already-excluded
container (e.g. a future field on the Configs tab itself) is automatically excluded without any
extra bookkeeping. Two places currently opt out:

- `ScreenConfigLibraryNode` itself (the whole Configs tab) — a config shouldn't be able to
  reference itself.
- `GeneratorRegistry.saveName` — the tab-preset "Save Name" input field.

---

## On-disk format

```
configs/
  my_favourite.json
  chill_mode.json
```

Each file is a `ScreenConfig`:

```json
{
  "name" : "My Favourite!",
  "params" : {
    "Wave/Oscilloscope/enabled" : 1,
    "Wave/Oscilloscope/amplitude" : 0.5,
    "Wave/Oscilloscope/lineWidth" : 2.0,
    "Tab/Translate Source/Generator" : 3,
    "Tab/Translate Source/Mandelbrot/Zoom" : 250.0,
    "Render/Palette/Map" : 7,
    "Render/Blur/Enabled" : 1
  }
}
```

The map is written with insertion order preserved (Jackson serialises a `LinkedHashMap` as-is,
and reads a JSON object back into one by default), so a config saved on one run applies its
entries in the same order on the next.

There is no checksum and no per-resolution binary, unlike `TabConfig`/`.tab` files — a screen
config is pure param data, so there's nothing to invalidate.

---

## Param tree layout

```
Configs (ScreenConfigLibraryNode — excluded from persistence itself)
  ├── <saved config name>   [ScreenConfigNode]
  │      ├── Load     [Action] → ScreenConfigStore.load → ScreenConfigParams.apply
  │      └── Delete   [Action] → ScreenConfigStore.delete, then refresh()
  ├── ⋮  (one ScreenConfigNode per saved config, sorted by name)
  ├── Save Name   [StringParameter, excluded from persistence]
  └── Save        [Action] → ScreenConfigStore.save(name, treeRoot)
```

Reachable over the remote HTTP API like any other node — `POST /api/v1/params/Configs/Save/execute`
to save, `POST /api/v1/params/Configs/<name>/Load/execute` to load. See `REMOTE_CONTROL.md` for the
general REST API shape.

---

## Key classes

| Class | Role |
|---|---|
| `ScreenConfigParams` | Static capture/apply tree-walking utility |
| `ScreenConfig` | Plain JSON-serialisable snapshot (name + ordered param map) |
| `ScreenConfigStore` | Disk I/O: `list()`, `save()`, `load()`, `delete()` |
| `ScreenConfigNode` | Per-saved-config tree node (Load / Delete actions) |
| `ScreenConfigLibraryNode` | Root "Configs" tab node: list + Save Name + Save |

---

## Known limitations

- **Index-based selectors assume a stable ordering.** The active palette and active tab generator
  are captured as integer indices into `PaletteLibraryNode`'s file list and
  `GeneratorRegistry`'s generator list respectively. Adding or removing a `.MAP` palette file or a
  `TabGenerator` implementation between saving and loading a config can shift those indices and
  select the wrong entry. This is an existing limitation of the underlying `EnumParameter`
  mechanism (also present in tab presets today), not something new to screen configs.
- **Unrecognised paths are silently ignored**, both when a target node no longer exists (e.g. a
  parameter was renamed/removed by a later code change) and when its type no longer matches the
  saved value. There is no migration or versioning story yet.
- **No partial capture.** A screen config always snapshots the whole non-excluded tree; there's no
  way to save "just the wave settings" as a distinct kind of preset.

---

See `PARAMS.md` for the parameter tree fundamentals (`Node`, `ParamNode`, `AbstractValue`) that
this feature builds on, and `REMOTE_CONTROL.md` for how the Configs tab is reachable remotely.
