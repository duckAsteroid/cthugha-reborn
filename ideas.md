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
8. Add a mechanism to document the config "tree" - a kind of tree/table that lists the IDs and their settings. Maybe do 
   this through the React.JS frontend?
9. Add an MCP interface so an agent can be used to control the configuration, and to allow for scripting of the configuration.  
   Might need a mechanism for the agent to "see" the rendered output?
10. Add a live quote (e.g. from agent) rather than always from static resources?
11. Named packages of animation (e.g. for translations - with their own dials) so you can easily do trippy transform based animations