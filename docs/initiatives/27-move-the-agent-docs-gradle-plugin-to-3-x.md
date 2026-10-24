---
type: chore
status: draft
updated: 2026-10-24
---
# Move the agent-docs Gradle plugin to 3.x

## Goal

Bump `io.github.duckasteroid.agent-docs` from 1.0.11 to 3.x (3.0.1 as of 2026-10-24) and move render-core to a release with the corrected `Agent-Docs` manifest. 3.x resolves docs from each dependency's `Agent-Docs` manifest attribute, renames the resolve-skill folder from `agent-docs-resolve/` to `agent-docs/`, and accepts the existing `agentDocs { includeSources = true }` block unchanged. Done means `./gradlew :app:resolveAgentDocs` on 3.x leaves a populated render-core skill folder in `.agents/skills/`.

## Dependencies

- [26 Fix render-core's Agent-Docs manifest artifact name upstream](26-fix-render-core-s-agent-docs-manifest-artifact-name-upstream.md) — 3.x follows render-core's Agent-Docs manifest attribute, which names the wrong artifact until that's fixed upstream

## Related

- [3 Update all dependencies to their latest releases](03-update-all-dependencies-to-their-latest-releases.md)

## Open questions

_None._

## Acceptance criteria

_What must be true when this is done._
