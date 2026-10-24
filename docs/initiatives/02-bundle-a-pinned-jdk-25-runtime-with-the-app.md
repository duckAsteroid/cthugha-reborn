---
type: build
status: draft
updated: 2026-10-24
---
# Bundle a pinned JDK 25 runtime with the app

## Goal

Give the app its own JVM instead of whatever JDK the machine has. Today the Gradle toolchain asks for Java 25 (app/build.gradle) and resolves to whichever local JDK matches (Corretto 25.0.3 via sdkman on the dev machine). Pin vendor + version through the Gradle toolchain resolver so builds are reproducible, and jlink a trimmed runtime into the installed distribution so `bin/cthugha-reborn` runs on it. JDK 25 LTS gives generational ZGC, which the launcher already selects (-XX:+UseZGC).

## Dependencies

_None._

## Open questions

_None._

## Acceptance criteria

_What must be true when this is done._
