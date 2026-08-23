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
       ├─ EnumParameter leaf  → record path → String (the selected option's display label)
       ├─ AbstractValue leaf  → record path → Number
       ├─ StringValue leaf    → record path → String
       ├─ node.isPersistExcluded() → skip this subtree entirely
       ├─ node implements DynamicChildList → also record path → node.describe()
       └─ container           → recurse into children (dynamic-list children too — their
                                 own leaf values, e.g. a binding's `script`, are captured
                                 the normal way alongside the ChildSpec)
  └─ returns a Snapshot(values, dynamicChildren) — both LinkedHashMaps, insertion-ordered
     to match the walk
```

`EnumParameter` is checked before the general `AbstractValue` case: although it stores its
selection as an integer index internally (it extends `IntegerParameter`), that index is only
stable for the lifetime of one option list. Capturing `getSelectedLabel()` instead — the option's
`toString()` — makes the snapshot survive the option list being reordered or grown between save
and load. See "Enum selections: label, not index" below.

The insertion order is not incidental — it is the whole reason `apply()` is safe:

```
apply(root, snapshot)
  for each (path, specs) in snapshot.dynamicChildren():        ← runs FIRST, in full
      target = root.getChild(path.split("/"))
      if target implements DynamicChildList → target.recreate(specs)

  for each (path, value) in snapshot.values(), IN CAPTURE ORDER:
      target = root.getChild(path.split("/"))   ← fresh lookup, every time
      if target is EnumParameter and value is String → target.selectByLabel(value)
      if target is EnumParameter and value is Number → target.setValue(value)   ← legacy configs
      if target is AbstractValue and value is Number → target.setValue(value)
      if target is StringValue   and value is String → target.setValue(value)
      (unresolved or type-mismatched paths are silently ignored)
```

Dynamic-child recreation always runs to completion before a single leaf value is applied, so by
the time the leaf-value pass reaches e.g. `Bindings/Trigger 1/condition`, `recreate()` has already
rebuilt a child actually named "Trigger 1" for it to land on. See "Recreating dynamic child lists"
below.

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

## Recreating dynamic child lists (Bindings, future Waves)

`ScreenConfigParams` only knows how to walk and set values on leaf nodes that already *exist* in
the live tree. That's fine for the static part of the tree, but some subtrees have children
created and destroyed at runtime through their own add/remove API rather than fixed at
construction — `BindingSystem` (the "Bindings" tab) is the one real example today: its list of
`Binding`s (`ContinuousBinding`/`EdgeTriggeredBinding`) grows and shrinks via `addContinuous` /
`addEdgeTriggered` / `removeBinding`. Loading a config saved with two bindings into a session that
currently has zero can't just walk `Bindings/Trigger 1/condition` — there is no child named
"Trigger 1" to walk to.

`DynamicChildList` (`params/DynamicChildList.java`) is the opt-in contract that closes this gap,
deliberately kept generic rather than hardcoded to bindings — a planned `WaveSystem` (multiple
named wave instances) will implement it the same way:

```java
public interface DynamicChildList {
    List<ChildSpec> describe();          // current children → recreation specs, in order
    void recreate(List<ChildSpec> specs); // clear + rebuild children from specs, in order

    record ChildSpec(String name, String type, Map<String, String> fields) {}
}
```

`ChildSpec` is deliberately flat and string-typed (round-trips through JSON with no bespoke
(de)serialisation): `name` is the exact name the recreated child must have (so later leaf-value
paths captured in the same snapshot resolve against it), `type` is a free-form discriminator the
implementer defines and interprets (`BindingSystem` uses the `BindingMode` name), and `fields`
holds whatever primitive strings the implementer's own `addX(...)` call needs.

`BindingSystem implements DynamicChildList`:

- `describe()` returns one `ChildSpec` per current binding — `type` is `"CONTINUOUS"` or
  `"EDGE_TRIGGERED"`, `fields` holds `target` plus `script` (continuous) or
  `condition`/`cooldown`/`value` (edge-triggered).
- `recreate(specs)` removes every current binding (via `removeBinding`, so any live target
  association releases synchronously) then replays `specs` in order through `addContinuous` /
  an internal name-preserving `addEdgeTriggeredNamed` — no behavioural change to `BindingSystem`
  itself, just a new entry point config-loading can call.

The `fields` captured in a `ChildSpec` overlap with what the ordinary leaf-value walk already
captures for that same binding (`target`, `script`, `condition`, …) — that's intentional
redundancy, not a bug: `recreate()` alone produces a fully-functional binding without depending on
the leaf-value pass that runs after it, and the subsequent leaf-value pass then simply reapplies
the same values (a harmless no-op) plus anything `ChildSpec` doesn't carry (e.g. `enabled`).

Configs saved before this contract existed simply have no `dynamicChildren` entry for `Bindings`
in their JSON; `ScreenConfigStore.load` treats a missing/`null` map the same as empty, so loading
such a file just skips dynamic-child recreation (bindings restore as before: not at all, since
none exist to recreate) without erroring.

---

## Enum selections: label, not index

`EnumParameter` (`params/values/EnumParameter.java`) backs every "pick one of a list" control in
the tree — the active palette (`Render/Palette/Map`), the active tab generator
(`Tab/Translate Source/Generator`), the selected quote, image, video, and audio input device, plus
every Java-enum-backed control (`RenderMode`, `ChannelMode`, `WaveType`, …). Internally it's always
an `IntegerParameter`: the selection is stored as an index into an ordered `List<T>`.

Several of those lists are built at runtime by scanning something external — `.MAP` files under
`maps/`, images under `images/`, videos, saved quotes, audio devices, `ServiceLoader`-discovered
`TabGenerator`s — and sorted for a stable display order. That sorting is exactly what makes the
index fragile: dropping a new file into the middle of an alphabetically-sorted directory shifts
every index after it. A screen config that captured "index 3" for the palette selector would, after
such a change, load and select whatever now happens to sit at index 3 — silently the wrong palette,
with no error.

`ScreenConfigParams` closes this by capturing and applying `EnumParameter` selections through
`getSelectedLabel()` / `selectByLabel(String)` (both on `EnumParameter`) instead of the raw index:

- `getSelectedLabel()` returns the selected option's display label — `values.get(index).toString()`
  — the same string `getOptions()` already exposes to the remote UI, so no new naming scheme was
  introduced.
- `selectByLabel(label)` linearly searches the current option list for a matching label and selects
  it if found, leaving the current selection untouched (like any other unresolved path) if not —
  e.g. the option was renamed or deleted since the config was saved.

This trades one failure mode for a strictly narrower one: a label survives reordering and insertion,
and only breaks if the option's own label changes — which, unlike a silent reshuffle, is a
deliberate rename the user is more likely to notice a config didn't survive.

**Backward compatibility, deprecated**: configs saved before this change have a plain integer for
these paths. `ScreenConfigParams.apply` still accepts a `Number` for an `EnumParameter` target and
applies it as a raw index — the old, index-fragile behaviour — so existing `.json` files under
`configs/` keep loading (with the pre-existing limitation) rather than breaking outright. This path
is considered deprecated: every config saved from now on writes the label instead, and every time
the index-based fallback actually runs it logs a `WARN` naming the path and the value, so
still-index-based configs (and where they live) show up in the log rather than silently persisting
forever. Re-saving a config (Load, then Save over it) is enough to migrate it off the index path.

Duplicate labels within one option list (two palettes that happen to share a display name) are not
guarded against — `selectByLabel` selects the first match. None of the current runtime-scanned
lists can actually produce a duplicate (filenames within one directory are unique), so this is a
theoretical gap, not an observed one.

---

## Excluding transient state: `persistExclude`

Not every leaf in the tree belongs in a snapshot. A "Save Name" text field is UI state, not part
of the look. `Node` carries a persistence flag parallel to (but independent of) the existing
remote-visibility flag:

| Mechanism | Purpose | Default | Setter |
|---|---|---|---|
| `isRemoteAllowed()` | Hide/reject a node over the remote HTTP API | `true` | `withNoRemote()` |
| `isPersistExcluded()` | Skip a node when capturing/applying a screen config | `false` | `withNoPersist()` |
| `isStructureHashExcluded()` | Count a subtree as one opaque unit in `structureHash` (see below) | `true` iff `DynamicChildList` | `withNoStructureHash()` |
| `getUiHints()` | Presentation hints for the remote React UI (widget type, icon, …) | `{}` | `withUiHint(key, value)` |

These are deliberately separate mechanisms rather than one shared map: `getUiHints()` is
serialised wholesale to the browser on every tree fetch (`ParamSerializer`), so folding
persistence-only metadata into it would either leak backend-only concerns over the wire or require
filtering logic to strip them back out. `persistExclude` and `structureHashExcluded` stay
backend-only, and answer different questions — a subtree can be persisted in full (its values
*are* saved and restored) while still being excluded from the structural hash, because its shape
legitimately varies with ordinary use rather than with a code change. See "Detecting structural
drift" below.

`persistExclude` is set at container roots, not leaf-by-leaf, and is inherited implicitly by never
descending into an excluded subtree — so a new leaf param added under an already-excluded
container (e.g. a future field on the Configs tab itself) is automatically excluded without any
extra bookkeeping. Two places currently opt out:

- `ScreenConfigLibraryNode` itself (the whole Configs tab) — a config shouldn't be able to
  reference itself.
- `GeneratorRegistry.saveName` — the tab-preset "Save Name" input field.

---

## Detecting structural drift: `structureHash`

A screen config can silently stop matching the parameter tree it was saved against — a parameter
gets renamed, removed, or changes type in a later code change — and until now the only symptom was
the per-path `WARN`s from `apply()` (see above), discovered only by actually loading the config and
reading the log. `ScreenConfigParams.structureHash(Node)` gives a cheap, up-front way to detect
that a config's tree *shape* has drifted, before or alongside applying it.

**What it hashes.** A SHA-256 digest of every non-excluded leaf's `path:kind` (e.g.
`Wave/Oscilloscope/amplitude:DOUBLE`, `Render/Palette/Map:enum`) — deliberately *not* the leaf's
current value, so tweaking a slider never changes the hash, only adding, removing, renaming, or
retyping a parameter does. Tokens are collected into a sorted set before hashing, so sibling
declaration order in code doesn't affect the result either — only which paths exist, and as what
kind.

**What it deliberately ignores**, per `isStructureHashExcluded()` above: any subtree whose shape
varies with ordinary use rather than a code change.

- Every `DynamicChildList` (`Bindings`, `Wave`) is opaque *by default* — adding a binding or a wave
  instance is exactly what the feature is for, not a schema change, so it must never look like one.
  This needed no code change in `BindingSystem`/`WaveSystem` themselves; it falls out of
  `Node.isStructureHashExcluded()`'s default (`this instanceof DynamicChildList`).
- Tab-preset listings (`SavedPresetsNode`, `AllPresetsNode`) opt out explicitly via
  `withNoStructureHash()`, the same flag, since saving or deleting a preset isn't a structural
  change either and they aren't `DynamicChildList`s.

One flag, one rule (default-participate, opt out), applied automatically to one category
(`DynamicChildList`) and explicitly to everything else that needs it — deliberately not two
separate mechanisms for what is conceptually the same exclusion.

**Where it's stored and checked.** `ScreenConfig.structureHash` holds the hash from capture time,
written by `ScreenConfigStore.save()` (and by `CurrentStateStore` for the auto-persisted "current"
state). `ScreenConfigParams.apply()` recomputes the hash of the *live* tree up front and compares:
a mismatch logs one `WARN` naming both hashes, before the per-path warnings that explain what
specifically didn't apply. A `null` stored hash (a config saved before this existed) skips the
check entirely — `apply()` has no baseline to compare against, so it says nothing rather than
reporting a false mismatch. Either way, loading still proceeds: this is a diagnostic signal, not a
gate.

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
    "Tab/Translate Source/Generator" : "Mandelbrot",
    "Tab/Translate Source/Mandelbrot/Zoom" : 250.0,
    "Render/Palette/Map" : "SUNSET",
    "Render/Blur/Enabled" : 1,
    "Bindings/anim/target" : "Wave/Oscilloscope/amplitude",
    "Bindings/anim/script" : "sine(0.05)",
    "Bindings/Trigger 1/condition" : "bass() > 0.7",
    "Bindings/Trigger 1/cooldown" : 0.15
  },
  "dynamicChildren" : {
    "Bindings" : [
      { "name" : "anim", "type" : "CONTINUOUS",
        "fields" : { "target" : "Wave/Oscilloscope/amplitude", "script" : "sine(0.05)" } },
      { "name" : "Trigger 1", "type" : "EDGE_TRIGGERED",
        "fields" : { "condition" : "bass() > 0.7", "target" : "Flash White", "cooldown" : "0.15", "value" : "" } }
    ]
  },
  "structureHash" : "3f9c1a7e2b6d4850..."
}
```

The map is written with insertion order preserved (Jackson serialises a `LinkedHashMap` as-is,
and reads a JSON object back into one by default), so a config saved on one run applies its
entries in the same order on the next. `dynamicChildren` is a separate, sibling map — one entry
per opted-in `DynamicChildList` subtree, keyed by that subtree's own path (here just `"Bindings"`,
since it hangs directly off the root) — applied in full before `params`, per `apply()` above.
`structureHash` is the tree-shape digest from "Detecting structural drift" above; `null`/absent on
configs saved before it existed.

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
| `ScreenConfigParams` | Static capture/apply tree-walking utility; `Snapshot` record pairs the leaf-value map, the dynamic-child-list map, and the `structureHash` |
| `EnumParameter` (`params/values/`) | "Pick one of a list" leaf; `getSelectedLabel()`/`selectByLabel()` give screen configs a reorder-safe key in place of the raw index |
| `DynamicChildList` (`params/`) | Opt-in contract (`describe()`/`recreate()`/`ChildSpec`/`pruneOrphaned()`) for a subtree with runtime-created children; implemented by `BindingSystem` and `WaveSystem`; opaque to `structureHash` by default |
| `ScreenConfig` | Plain JSON-serialisable snapshot (name + ordered param map + dynamic-children map + `structureHash`) |
| `ScreenConfigStore` | Disk I/O: `list()`, `save()`, `load()`, `delete()` |
| `ScreenConfigNode` | Per-saved-config tree node (Load / Delete actions) |
| `ScreenConfigLibraryNode` | Root "Configs" tab node: list + Save Name + Save |

---

## Known limitations

- ~~Index-based selectors assume a stable ordering.~~ **Fixed** — `EnumParameter` selections
  (active palette, active tab generator, quote, image, video, audio source, etc.) are now captured
  and applied by display label rather than list index; see "Enum selections: label, not index"
  above. Configs saved before this change still round-trip via the old index-based path.
- **Unrecognised paths are skipped and logged, not repaired.** A target node that no longer exists
  (e.g. a parameter was renamed/removed by a later code change), a type mismatch against the saved
  value, an enum label with no matching option, and a legacy index-based enum value are all logged
  at `WARN` by `ScreenConfigParams.apply` as they're skipped — plus a one-line summary if any were,
  and (see "Detecting structural drift" above) an upfront `WARN` if the tree's `structureHash` no
  longer matches what the config was saved against. That covers *detection*: a partially-failed
  load is now visible in the log rather than silently producing a subtly wrong result. There is
  still no *migration* story — nothing attempts to map an old path to a renamed one, so recovering
  from a real mismatch still means manually re-editing the saved config or resaving it from scratch.
- **No partial capture.** A screen config always snapshots the whole non-excluded tree; there's no
  way to save "just the wave settings" as a distinct kind of preset.

---

See `PARAMS.md` for the parameter tree fundamentals (`Node`, `ParamNode`, `AbstractValue`) that
this feature builds on, and `REMOTE_CONTROL.md` for how the Configs tab is reachable remotely.
