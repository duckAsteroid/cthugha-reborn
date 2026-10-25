---
type: bug
status: draft
updated: 2026-10-25
tags: [render-core]
---
# Fix render-core's Agent-Docs manifest artifact name upstream

## Goal

render-core's jar manifest declares its docs sidecar as `Agent-Docs: maven:io.github.duckasteroid:core:<version>` (seen in 1.0.0-RC4 and 1.0.0-RC5), but the artifact is published as `io.github.duckasteroid:render-core`. agent-docs 3.x follows that attribute, finds no `core` sidecar, and removes the render-core skill from `.agents/skills/`. agent-docs 1.x ignores the attribute and resolves `<group>:<artifact>:<version>:agent-docs@zip` from the dependency's own name, which is why the skill works today. Fix the attribute in render-core's build so it names `render-core`, and publish a release with the fix.

## Dependencies

- [https://github.com/duckAsteroid/opengl-jogl-template/issues/31](https://github.com/duckAsteroid/opengl-jogl-template/issues/31) — upstream render-core fix and release

## Related

- [3 Update all dependencies to their latest releases](03-update-all-dependencies-to-their-latest-releases.md)
- [4 Fix AudioReader busy-spin upstream in render-core](04-fix-audioreader-busy-spin-upstream-in-render-core.md)
- [5 Stop render-core wave setters allocating and rebuilding every frame](05-stop-render-core-wave-setters-allocating-and-rebuilding-ever.md)

## Open questions

_None._

## Acceptance criteria

_What must be true when this is done._
