---
type: chore
status: draft
updated: 2026-10-24
---
# Update all dependencies to their latest releases

## Goal

Bring every library and Gradle plugin in :app and :remote-ui up to its latest stable release (LWJGL, JOML, Javalin, Jackson, Logback/SLF4J, JTransforms, javacv, JUnit/Mockito, the React/Vite toolchain, the Gradle wrapper), and clear the "multi-string dependency notation" deprecation warning. The LWJGL 3.3.6 pin only existed for the dropped native-image build, so LWJGL is free to move. Verify with the unit tests, the smoke test and a FrameTimeStats benchmark run.

## Dependencies

_None._

## Related

- [2 Bundle a pinned JDK 25 runtime with the app](02-bundle-a-pinned-jdk-25-runtime-with-the-app.md)
- [4 Fix AudioReader busy-spin upstream in render-core](04-fix-audioreader-busy-spin-upstream-in-render-core.md)
- [5 Stop render-core wave setters allocating and rebuilding every frame](05-stop-render-core-wave-setters-allocating-and-rebuilding-ever.md)

## Open questions

_None._

## Acceptance criteria

_What must be true when this is done._
