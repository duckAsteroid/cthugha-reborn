---
type: chore
status: done
updated: 2026-10-25
docs_impact:
  - CLAUDE.md
tags: [upgrades]
---
# Move the agent-docs Gradle plugin to 3.x

## Goal

Move `io.github.duckasteroid.agent-docs` from 1.0.11 to 3.x (3.0.3) together with render-core 1.0.0-RC7, whose `Agent-Docs` manifest names the right artifact. 3.x resolves docs from each dependency's `Agent-Docs` manifest attribute, writes the resolve skill to `.agents/skills/agent-docs/` and the render-core skill to an unversioned `.agents/skills/render-core/` (sources under `assets/sources/`), and accepts the existing `agentDocs { includeSources = true }` block unchanged. 3.x does not remove the 1.x `agent-docs-resolve/` folder, so that is deleted by hand.

## Dependencies

- [26 Fix render-core's Agent-Docs manifest artifact name upstream](26-fix-render-core-s-agent-docs-manifest-artifact-name-upstream.md) — 3.x follows render-core's Agent-Docs manifest attribute, which names the wrong artifact until that's fixed upstream

## Related

- [3 Update all dependencies to their latest releases](03-update-all-dependencies-to-their-latest-releases.md)

## Open questions

_None._

## Acceptance criteria

- [x] `app/build.gradle` uses agent-docs 3.0.3.
- [x] `./gradlew :app:resolveAgentDocs` leaves a populated `.agents/skills/render-core/` (SKILL.md, reference docs, 156 source files for 1.0.0-RC7).
- [x] The stale `.agents/skills/agent-docs-resolve/` and versioned rc5 skill folders are gone.
