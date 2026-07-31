1. Have a kind of global transform applied to the render so we can animate it.
2. A palette map design tool with colours placed along the line with curves/gradients indicated. Like the powerpoint gradient designer.
   The resulting palette file would be large enough to accomodate every step - not just blindly 256
3. Make quote "dwell time" configurable (maybe make it a "one time" trigger on an enabled state?)
4. Make quote mode an enumeration (so it can be scripted)
5. Add an action to load a random saved tab preset (pick at random across all saved presets under
   `tabs/`), mirroring the existing "New Source" random-generator action but for presets instead
   of generators.
6. Add a "Regenerate" action for partly-random tab generators (e.g. Shatter) that reseeds the
   random layout (e.g. Voronoi points) without needing to nudge an existing parameter to trigger
   a recompute.
7. Per-notification-type toggle — e.g. suppress just the "tab calculation in progress" message —
   in addition to the existing global Notifications toggle. Worth noting the in-progress notice
   can also fire repeatedly during rapid slider drags, which may be the real irritant.