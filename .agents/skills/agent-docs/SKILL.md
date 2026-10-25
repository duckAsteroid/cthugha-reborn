---
name: agent-docs
description: "Reference documentation for the Gradle plugin `io.github.duckasteroid.agent-docs`. Use this skill when configuring, writing, or troubleshooting Gradle builds that apply it. Reads the Agent-Docs manifest attribute from each direct dependency and each applied Gradle plugin's own jar, then extracts matching docs bundles into per-dependency skill folders under .agents/skills/."
metadata:
  pluginId: io.github.duckasteroid.agent-docs
  sources: none
---

# Agent Docs Resolve Plugin

Plugin ID: `io.github.duckasteroid.agent-docs`

For each direct dependency on the configured classpath (default `compileClasspath`), reads the
`Agent-Docs` manifest attribute from that dependency's own resolved jar. A dependency with no
attribute is skipped entirely — no further resolution attempt of any kind. `Agent-Docs:
classpath[:path]` extracts the docs bundle directly from that same jar; `Agent-Docs:
maven[:group:artifact:version]` resolves a separate `agent-docs@zip` sidecar.

The plugin also discovers docs for Gradle plugins applied via `plugins {}`: since a plugin jar
isn't resolved onto a dependency configuration, it walks the applied plugin classes and resolves
each one's jar via its classloader's code source instead. Only the `classpath` scheme is honored
for plugins — a `maven` declaration is skipped with a warning, since there's no consumer-side
resolution path for a plugin sidecar. This is scoped to binary plugins only (`buildSrc`/
`build-logic` precompiled script plugins are silently skipped — their jars are never stamped with
the manifest attribute).

Either way, the bundle is extracted into `.agents/skills/<skill-name>/`, the extracted `SKILL.md`
frontmatter is rewritten (canonical `name`, a generated description prefix with the author's own
description appended, and GAV or plugin-id metadata), and an `.agent-docs` ownership marker is
written. Stale marker-owned folders are removed when dependencies/plugins are dropped.

## Tasks added

- `resolveAgentDocs` — resolves and extracts all discoverable docs bundles; always re-runs (not
  cached; `@DisableCachingByDefault`, since it orchestrates filesystem state outside task outputs)

## Usage

```bash
./gradlew resolveAgentDocs
```

## Extension

```groovy
agentDocs {
  configurationName = 'compileClasspath'                                          // default
  skillsDirectory = rootProject.layout.projectDirectory.dir('.agents/skills')     // default
  includeSources = false                                                           // default
}
```

## Output layout

Each resolved dependency or plugin produces a skill folder:

```text
.agents/skills/
  <skill-name>/
    SKILL.md          ← frontmatter `name` rewritten to match folder name
    references/
    assets/
    scripts/
    .agent-docs       ← ownership marker; do not edit managed folders
```

When `includeSources = true` (dependency-sourced skills only), a `<group>:<artifact>:<version>:sources@jar`
is also resolved and unpacked into `assets/sources/` inside the skill folder:

```text
.agents/skills/
  <skill-name>/
    SKILL.md          ← frontmatter includes `metadata.sources: assets/sources/` or `metadata.sources: none`
    assets/
      sources/        ← unpacked sources jar (only present when a sources jar exists)
    .agent-docs
```

| `metadata.sources` value | Meaning |
|---|---|
| `assets/sources/` | Sources extracted — read from that subdirectory |
| `none` | Sources were requested but unavailable in the repository |
| absent | `includeSources` was not enabled when this skill was extracted |

`metadata.group`/`metadata.artifact`/`metadata.version` are always recorded for dependency-sourced
skills; `metadata.pluginId` is recorded instead for plugin-sourced skills. Everything else in
upstream frontmatter, including unrelated `metadata` keys, is preserved untouched.

Folder names are assigned per run in tiers, shortest-safe-first: artifact name alone, then
`group-artifact` if that collides with another dependency in the same run, then the full GAV as a
last resort (plugin-sourced skills use the plugin id as their base candidate). Each tier is
normalized to `[a-z0-9-]`, no edge or consecutive hyphens, max 64 characters with a deterministic
SHA-256 hash suffix when truncated. Dependency- and plugin-sourced candidates share one
collision-detection pass since both land in the same skills directory.

Do not hand-edit files inside marker-owned folders — they are overwritten on the next
`resolveAgentDocs` run.

## The `Agent-Docs` manifest attribute

| Value | Meaning |
|---|---|
| *(absent)* | No agent docs for this dependency/plugin; nothing is resolved. |
| `classpath` | Docs are embedded in the jar at `agent-docs/`. |
| `classpath:<path>` | Docs are embedded at a custom path instead of the default. |
| `maven` | A sidecar zip is published at this dependency's own coordinates (dependencies only). |
| `maven:<group>:<artifact>:<version>` | A sidecar zip is published at the given, explicitly-declared coordinates (dependencies only). |

This attribute doesn't require `agent-docs-publish-gradle-plugin` at all — any jar can hand-write
it in a `jar { manifest { attributes(...) } }` block.
