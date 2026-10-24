---
type: chore
status: designed
updated: 2026-10-24
---
# Update all dependencies to their latest releases

## Goal

Bring every library and Gradle plugin in :app and :remote-ui up to its latest stable release within its current major version: LWJGL, JOML, Jackson 2.x, Javalin 6.x, Logback/SLF4J, commons-lang3, JTransforms, Janino, javacv/javacpp/ffmpeg, JUnit 5.x/Mockito, the `com.github.ben-manes.versions` plugin, the React/Vite/Tailwind/Radix toolchain, and the Gradle wrapper. Also clear the "multi-string dependency notation" deprecation warning, which comes from the `group:/name:/version:` map notation still used for slf4j-api, commons-lang3, ini4j and junit-jupiter in `app/build.gradle`. Verify with the unit tests, the smoke test and a FrameTimeStats benchmark run.

## Dependencies

_None._

## Related

- [2 Bundle a pinned JDK 25 runtime with the app](02-bundle-a-pinned-jdk-25-runtime-with-the-app.md)
- [4 Fix AudioReader busy-spin upstream in render-core](04-fix-audioreader-busy-spin-upstream-in-render-core.md)
- [5 Stop render-core wave setters allocating and rebuilding every frame](05-stop-render-core-wave-setters-allocating-and-rebuilding-ever.md)
- [22 Migrate to Jackson 3](22-migrate-to-jackson-3.md)
- [23 Migrate the remote server to Javalin 7](23-migrate-the-remote-server-to-javalin-7.md)
- [24 Migrate tests to JUnit 6](24-migrate-tests-to-junit-6.md)
- [25 Move the remote-ui toolchain to its new major versions](25-move-the-remote-ui-toolchain-to-its-new-major-versions.md)

## Decisions

- **Scope is the latest release within each dependency's current major version.** Major-version jumps get their own tickets: Jackson 3 (ct#22), Javalin 7 (ct#23), JUnit 6 (ct#24), and the remote-ui toolchain majors Vite 8 / plugin-react 6 / TypeScript 7 / lucide-react 1 (ct#25). TypeScript stays within 5.x (latest 5.x, not 7) and lucide-react within 0.x.
- **render-core is in scope:** it moves to the latest version published to GitHub Packages at implementation time, whatever that is (a later RC or a final release). ct#4 and ct#5 bump it again when their upstream fixes are released.
- (implementation) **Benchmark comparison:** take a FrameTimeStats run on the pre-update build as the baseline, then a run on the updated build under the same conditions (fullscreen on DP-4, same screen config and audio). "No regression" means mean and p99 frame times are within run-to-run noise of the baseline.

## Open questions

_None._

## Acceptance criteria

- [ ] Every dependency and plugin in `app/build.gradle`, `settings.gradle` and `remote-ui/package.json` is on the latest release within its current major version, render-core is on the latest version published to GitHub Packages, and the Gradle wrapper is on the latest 9.x.
- [ ] `./gradlew dependencyUpdates` and `npm outdated` report only the major-version upgrades deferred to ct#22–ct#25.
- [ ] `remote-ui/package-lock.json` is regenerated and committed.
- [ ] The build emits no "multi-string dependency notation" deprecation warning (every dependency uses `group:artifact:version` string notation).
- [ ] `./gradlew build` and `./gradlew test` pass.
- [ ] `./gradlew smokeTest` passes.
- [ ] A FrameTimeStats benchmark run shows no regression against a baseline taken before the update.
- [ ] The remote UI, served from the rebuilt jar, loads and controls the app from a phone.
