---
type: build
status: designed
updated: 2026-10-24
---
# Bundle a pinned JDK 25 runtime with the app

## Goal

Give the app its own JVM instead of whatever JDK the machine has. Today the Gradle toolchain asks for Java 25 (app/build.gradle) and resolves to whichever local JDK matches (Corretto 25.0.3 via sdkman on the dev machine). Pin vendor + version through the Gradle toolchain resolver so builds are reproducible, and jlink a trimmed runtime into the installed distribution so `bin/cthugha-reborn` runs on it. JDK 25 LTS gives generational ZGC, which the launcher already selects (-XX:+UseZGC).

## Dependencies

_None._

## Decisions

- **Pinning is major version + vendor: Java 25, Amazon Corretto.** The toolchain spec (`app/build.gradle` `java.toolchain`) gets `vendor = JvmVendorSpec.AMAZON`, and the foojay toolchain resolver plugin is added in `settings.gradle` so a machine without Corretto 25 downloads it. Patch updates (25.0.3 → 25.0.4) come in on their own. Gradle toolchains cannot pin an exact patch level.
- **The bundled runtime is the default, with an explicit override.** `bin/cthugha-reborn` runs `$APP_HOME/runtime/bin/java` unless `CTHUGHA_JAVA_HOME` is set, in which case it runs `$CTHUGHA_JAVA_HOME/bin/java`. Plain `JAVA_HOME` is ignored, because sdkman sets it in every shell.
- **The runtime ships in all three distribution outputs:** `installDist`, `distZip` and `distTar`.
- (implementation) **The runtime is built with a hand-rolled `jlink` task that uses the toolchain's own JDK** (`javaToolchains.launcherFor`), not a third-party plugin such as `org.beryx.runtime`. This avoids a plugin dependency that may lag behind Gradle 9.x. The module list comes from `jdeps --print-module-deps` over the runtime classpath, plus any modules that are only loaded reflectively (e.g. `jdk.unsupported` for LWJGL/javacpp, `jdk.crypto.ec` for TLS) that a smoke test shows are needed. The image is built with `--strip-debug --no-header-files --no-man-pages`.

## Open questions

_None._

## Acceptance criteria

- [ ] `app/build.gradle`'s toolchain requests Java 25 from vendor Amazon (Corretto), and `settings.gradle` applies the foojay toolchain resolver, so a clean machine with no matching JDK can still build.
- [ ] A jlink task builds a trimmed Corretto 25 runtime from the toolchain JDK. `installDist`, `distZip` and `distTar` all contain it under `runtime/`.
- [ ] `bin/cthugha-reborn` launches on `runtime/bin/java` even when `JAVA_HOME`/`PATH` point at a different JDK (check with `JAVA_HOME` set to a Java 21 install).
- [ ] With `CTHUGHA_JAVA_HOME` set, `bin/cthugha-reborn` launches on that JVM instead.
- [ ] `./gradlew smokeTest` passes against the installed distribution (it runs `bin/cthugha-reborn`, so it exercises the bundled runtime), including the remote API, audio capture and video playback through javacv.
- [ ] The start script's existing behaviour is unchanged: the `--audio-source` handling, the PRIME environment variables, the `renderJvmArgs` ZGC/heap flags and `--enable-native-access`.
- [ ] Project docs describe the bundled runtime and the `CTHUGHA_JAVA_HOME` override.
