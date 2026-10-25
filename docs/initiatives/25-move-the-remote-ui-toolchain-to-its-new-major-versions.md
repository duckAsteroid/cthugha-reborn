---
type: chore
status: draft
updated: 2026-10-25
tags: [upgrades, remote-ui]
---
# Move the remote-ui toolchain to its new major versions

## Goal

Bring the `:remote-ui` build toolchain across its major-version jumps, as reported by `npm outdated` on 2026-10-24: Vite 6 → 8, `@vitejs/plugin-react` 4 → 6, TypeScript 5.6 → 7 (the native compiler rewrite, so `tsc -b` in the `build` script and the `tsconfig*.json` files need checking), and `lucide-react` 0.469 → 1.x (icon names and exports may have changed). The built SPA bundled into the jar under `remote/` must behave the same on a phone.

## Dependencies

- [3 Update all dependencies to their latest releases](03-update-all-dependencies-to-their-latest-releases.md) — start from dependencies that are already up to date within their current major

## Open questions

_None._

## Acceptance criteria

_What must be true when this is done._
