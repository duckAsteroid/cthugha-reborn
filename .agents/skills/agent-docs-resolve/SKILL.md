---
name: agent-docs-resolve
description: Resolves agent-docs sidecar artifacts for project dependencies and extracts them as local agent skill folders.
---

# Agent Docs Resolve Plugin

Plugin ID: `io.github.duckasteroid.agent-docs`

For each direct dependency on the configured classpath, resolves `<group>:<artifact>:<version>:agent-docs@zip` using the project's configured repositories. Resolved sidecars are extracted into `.agents/skills/<gav-skill-name>/`. Stale managed skill folders (identified by a `.agent-docs` ownership marker) are removed when their dependencies are dropped.

## Tasks added

- `resolveAgentDocs` — resolves and extracts all resolvable sidecars; always re-runs (not cached)
- `installAgentDocsResolveSkill` — writes this file into the local agent skills folder

## Usage

```bash
./gradlew resolveAgentDocs
```

## Extension

```groovy
agentDocs {
  configurationName = 'compileClasspath'                                          // default
  skillsDirectory = rootProject.layout.projectDirectory.dir('.agents/skills')     // default
}
```

## Output layout

Each resolved dependency produces a skill folder:

```text
.agents/skills/
  <gav-skill-name>/
    SKILL.md          ← frontmatter `name` rewritten to match folder name
    references/
    assets/
    scripts/
    .agent-docs       ← ownership marker; do not edit managed folders
```

Folder names are derived from GAV coordinates: lowercased, non-alphanumeric characters replaced with `-`, max 64 characters with a SHA-256 hash suffix when truncation is needed.

Do not hand-edit files inside marker-owned folders — they are overwritten on the next `resolveAgentDocs` run.
