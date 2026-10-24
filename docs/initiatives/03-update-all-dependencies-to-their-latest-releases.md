---
type: chore
status: done
updated: 2026-10-24
docs_impact:
  - CLAUDE.md
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

- **Scope is the latest release within each dependency's current major version.** Major-version jumps get their own tickets: Jackson 3 (ct#22), Javalin 7 (ct#23), JUnit 6 (ct#24), the remote-ui toolchain majors Vite 8 / plugin-react 6 / TypeScript 7 / lucide-react 1 (ct#25), and agent-docs 3.x (ct#27). TypeScript stays within 5.x (latest 5.x, not 7) and lucide-react within 0.x.
- **The agent-docs plugin stays on 1.0.11.** 3.x follows render-core's `Agent-Docs` manifest attribute, which names the wrong artifact (`core` where it should be `render-core`), so on 3.x the render-core skill disappears from `.agents/skills/`. The upstream fix is ct#26, and the bump is ct#27, which depends on it.
- **render-core is in scope:** it moves to the latest version published to GitHub Packages at implementation time, whatever that is (a later RC or a final release). ct#4 and ct#5 bump it again when their upstream fixes are released.
- (implementation) **slf4j-api resolves to 2.1.0-alpha1 despite the 2.0.20 declaration,** because render-core RC5 depends on that alpha and Gradle conflict resolution picks the highest version. This is left as is: changing it belongs in render-core.
- (implementation) **The versions plugin moves to its new id, `io.github.ben-manes.versions`.** 0.66.0 deprecates `com.github.ben-manes.versions`, and 0.47.0 crashed on Gradle 9.
- (implementation) **Benchmark comparison:** take a FrameTimeStats run on the pre-update build as the baseline, then a run on the updated build under the same conditions (fullscreen on DP-4, same screen config and audio). "No regression" means mean and p99 frame times are within run-to-run noise of the baseline. The two builds are run back to back, with the pre-update install kept aside. The screen must be unlocked, since a locked session throttles the window to 1 fps.

## Open questions

_None._

## Acceptance criteria

- [x] Every dependency and plugin in `app/build.gradle`, `settings.gradle` and `remote-ui/package.json` is on the latest release within its current major version, render-core is on the latest version published to GitHub Packages (1.0.0-RC5), and the Gradle wrapper is on the latest 9.x (9.8.2).
- [x] `./gradlew dependencyUpdates` and `npm outdated` report only the major-version upgrades deferred to ct#22–ct#25 and ct#27.
- [x] `remote-ui/package-lock.json` is regenerated and committed.
- [x] The build emits no "multi-string dependency notation" deprecation warning (every dependency uses `group:artifact:version` string notation), and `--warning-mode all` shows no Gradle deprecations.
- [x] `./gradlew build` and `./gradlew test` pass on Gradle 9.8.2 (172 tests).
- [x] `./gradlew smokeTest` passes on Gradle 9.8.2.
- [x] A FrameTimeStats benchmark run shows no regression against a baseline taken before the update. Each build ran for 75 s fullscreen on DP-4, back to back (seven 10 s windows each):

  | | Pre-update | Updated |
  |---|---|---|
  | Mean frame time (average of windows) | 17.67 ms | 17.25 ms |
  | p50 | 16.67–16.79 ms | 16.66–16.84 ms |
  | p99 (median of windows) | 22.56 ms | 22.73 ms |
  | Average fps | 56.7 | 58.0 |
  | Janky frames (total) | 22 | 18 |
  | Worst single frame | 596 ms | 423 ms |

  Both builds show the same occasional 200–600 ms stalls, which come from the full-4K render buffer plus the PRIME copy (ct#1), not from this update.
- [x] The remote UI, served from the rebuilt jar, loads and controls the app from a phone (confirmed by the user on 2026-10-24).
