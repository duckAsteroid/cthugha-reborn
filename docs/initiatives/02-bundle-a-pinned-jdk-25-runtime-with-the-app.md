---
type: build
status: done
updated: 2026-10-24
docs_impact:
  - CLAUDE.md
---
# Bundle a pinned JDK 25 runtime with the app

## Goal

Give the app its own JVM instead of whatever JDK the machine has. Today the Gradle toolchain asks for Java 25 (app/build.gradle) and resolves to whichever local JDK matches (Corretto 25.0.3 via sdkman on the dev machine). Pin vendor + version through the Gradle toolchain resolver so builds are reproducible, and jlink a trimmed runtime into the installed distribution so `bin/cthugha-reborn` runs on it. JDK 25 LTS gives generational ZGC, which the launcher already selects (-XX:+UseZGC).

## Dependencies

_None._

## Decisions

- **Pinning is major version + vendor: Java 25, Amazon Corretto.** The toolchain spec (`app/build.gradle` `java.toolchain`) sets `vendor = JvmVendorSpec.AMAZON`, and the foojay toolchain resolver plugin (`settings.gradle`) downloads Corretto 25 on a machine that lacks it. Patch updates (25.0.3 → 25.0.4) come in on their own. Gradle toolchains cannot pin an exact patch level.
- **The bundled runtime is the default, with an explicit override.** `bin/cthugha-reborn` runs `$APP_HOME/runtime/bin/java` unless `CTHUGHA_JAVA_HOME` is set, in which case it runs `$CTHUGHA_JAVA_HOME/bin/java`. Plain `JAVA_HOME` is ignored, because sdkman sets it in every shell. The start script points `JAVA_HOME` at the chosen home just before Gradle's stock Java lookup, so that lookup's validation and error messages still apply.
- **The runtime ships in all three distribution outputs:** `installDist`, `distZip` and `distTar`. The archives normalise file permissions to 0644, so the runtime's `bin/*` and `lib/jspawnhelper` are explicitly marked executable.
- (implementation) **The runtime is built with a hand-rolled `jlinkRuntime` Exec task that uses the toolchain's own JDK** (`javaToolchains.launcherFor(java.toolchain)`), not a third-party plugin such as `org.beryx.runtime`, with `--strip-debug --no-header-files --no-man-pages`. The module list is an explicit `runtimeModules` list in `app/build.gradle`, not derived from `jdeps --print-module-deps`. jdeps cannot resolve the classpath because javacv's module descriptor requires javafx/libfreenect modules that aren't shipped. The list was taken from each library's module descriptor plus `jdeps -s` per jar: `java.base`, `java.desktop`, `java.instrument`, `java.logging`, `java.management`, `java.naming`, `java.prefs`, `java.sql`, `java.xml`, `jdk.unsupported`. The resulting runtime is 92 MB.

## Open questions

_None._

## Acceptance criteria

- [x] `app/build.gradle`'s toolchain requests Java 25 from vendor Amazon (Corretto), and `settings.gradle` applies the foojay toolchain resolver (1.0.0), so a clean machine with no matching JDK can still build. The configuration is in place, but the download itself was not exercised: on the dev machine Gradle still resolved the local sdkman Corretto even with auto-detection, `fromEnv` and `paths` overridden and the daemon on Temurin.
- [x] A jlink task builds a trimmed Corretto 25 runtime (92 MB) from the toolchain JDK. `installDist`, `distZip` and `distTar` all contain it under `runtime/`, with `bin/java` and `lib/jspawnhelper` executable in both archives.
- [x] `bin/cthugha-reborn` launches on `runtime/bin/java` with `JAVA_HOME` pointing at Corretto 21, with Java 21 first on `PATH`, and with no Java installed at all.
- [x] With `CTHUGHA_JAVA_HOME` set (to Corretto 21), `bin/cthugha-reborn` launches on that JVM instead.
- [x] `./gradlew smokeTest` passes against the installed distribution (it runs `bin/cthugha-reborn`, so it exercises the bundled runtime), including the remote API. Audio capture through `javax.sound` and video decoding through javacv/FFmpeg plus `Java2DFrameConverter` were checked separately on the bundled runtime with a probe: it read 4096 bytes from a real capture line and decoded a 712×480 H.264 frame. `./gradlew test` passes (172 tests).
- [x] The start script's existing behaviour is unchanged: the `--audio-source` handling, the PRIME environment variables, the `renderJvmArgs` ZGC/heap flags and `--enable-native-access`.
- [x] Project docs describe the bundled runtime and the `CTHUGHA_JAVA_HOME` override (CLAUDE.md, Build & Run).
