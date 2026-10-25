---
type: bug
status: done
updated: 2026-10-25
docs_impact: "none: build metadata only"
tags: [render-core]
---
# Fix render-core's Agent-Docs manifest artifact name upstream

## Goal

render-core's jar manifest declared its docs sidecar as `Agent-Docs: maven:io.github.duckasteroid:core:<version>` (RC4, RC5), but the artifact is published as `io.github.duckasteroid:render-core`, so agent-docs 3.x found no sidecar and removed the render-core skill. render-core 1.0.0-RC7 renames its subproject to `render-core`, so the manifest now names the published artifact.

## Dependencies

- [https://github.com/duckAsteroid/opengl-jogl-template/issues/31](https://github.com/duckAsteroid/opengl-jogl-template/issues/31) — upstream render-core fix and release

## Related

- [3 Update all dependencies to their latest releases](03-update-all-dependencies-to-their-latest-releases.md)
- [4 Fix AudioReader busy-spin upstream in render-core](04-fix-audioreader-busy-spin-upstream-in-render-core.md)
- [5 Stop render-core wave setters allocating and rebuilding every frame](05-stop-render-core-wave-setters-allocating-and-rebuilding-ever.md)

## Open questions

_None._

## Acceptance criteria

- [x] A render-core release (1.0.0-RC7) declares `Agent-Docs: maven:io.github.duckasteroid:render-core:<version>`.
