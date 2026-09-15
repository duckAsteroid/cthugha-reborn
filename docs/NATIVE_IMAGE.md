# Native Image (GraalVM)

## Overview

`:app` can be ahead-of-time compiled to a standalone Linux executable with GraalVM Native
Image, via the `org.graalvm.buildtools.native` Gradle plugin. The resulting binary starts in a
fraction of the time of the JVM version and needs no JDK installed to run — but getting there
required working around several real incompatibilities between this app's dependencies and
Native Image's closed-world, ahead-of-time model. This doc covers how to build and run it, and
why the setup looks the way it does.

This is experimental and Linux-only, matching the rest of this personal project. Treat it as a
faster, throwaway way to launch the app locally — not (yet) a release artifact.

---

## Prerequisites

You need a GraalVM JDK with `native-image` **new enough that its reachability-metadata schema
matches the `org.graalvm.buildtools` metadata repository** — the version bundled with an older
GraalVM 25.0.0 install is too old and fails with a schema-mismatch error. Install a current
point release via [sdkman](https://sdkman.io/):

```bash
sdk install java 25.3.4-r25.graal   # Oracle GraalVM; GraalVM CE (…-graalce) also works
```

Point Gradle's toolchain resolution at it in a project-local `gradle.properties` (already
checked in, but the path is machine-specific — adjust it for your install):

```properties
org.gradle.java.installations.paths=/home/<you>/.sdkman/candidates/java/25.3.4+1.r25-graalce
org.gradle.java.installations.auto-detect=false
```

Restricting to exactly this one JDK avoids Gradle picking a different, non-`native-image`-capable
JDK 25 install (e.g. Corretto or Temurin) when several are present — it's genuinely ambiguous
otherwise, since a plain "give me language version 25" toolchain request matches all of them.

`auto-detect=false` only affects *toolchain resolution*; the JVM you use to invoke Gradle itself
is unaffected, so plain `./gradlew run` / `test` keep using whatever `java` is on your `PATH`.

---

## Building

```bash
./gradlew :app:nativeCompile
```

Run this **with `JAVA_HOME` pointed at the GraalVM install** (matching `gradle.properties`
above) so the Gradle daemon's own JVM is the one with `native-image` on it:

```bash
JAVA_HOME=/home/<you>/.sdkman/candidates/java/25.3.4+1.r25-graalce ./gradlew :app:nativeCompile
```

The build takes 1.5–2 minutes and produces:

```
app/build/native/nativeCompile/cthugha-reborn
```

## Running

The binary still needs the runtime working directory (`config`, `maps/`, `images/`, `glsl/`,
`tabs/`) exactly like the JVM version — run it from `app/src/dist`:

```bash
cd app/src/dist
/path/to/app/build/native/nativeCompile/cthugha-reborn \
  --enable-native-access=ALL-UNNAMED \
  -Djava.home=/home/<you>/.sdkman/candidates/java/25.0.3-amzn \
  --remote-token=YOUR_TOKEN
```

`-Djava.home` can point at **any** real JDK install (it doesn't need to be the GraalVM one) — see
[AWT needs `java.home`](#awt-needs-javahome) below for why this is required at all.

---

## Why the build looks the way it does

Four separate incompatibilities had to be worked around to get a running binary. If you touch
the `graalvmNative` block in `app/build.gradle` or bump a pinned dependency, re-read this list —
regressing any one of these reintroduces a build failure or a runtime crash.

### 1. Ancient `package-info.class` files crash the classpath scanner

Native Image's classpath scan (used to find `@Platforms`/`@AutomaticFeature`-annotated classes)
loads *every* class on the classpath, including `package-info.class`. Some dependencies — `joml`
1.10.9 is the one that surfaced it — ship a `package-info.class` compiled to classfile major
version 46 (Java 1.2), a decades-old javac quirk for annotation-only package declarations.
Native Image's class loader throws `ClassFormatError: Illegal class name` on that version.
Confirmed on both Oracle GraalVM and GraalVM CE 25.3.4, so this is an upstream Native Image bug,
not something fixable via a flag.

**Workaround**: `app/build.gradle` defines a `stripPackageInfoForNativeImage` task that copies
the runtime classpath jars with all `package-info.class` entries removed, and points
`graalvmNative.binaries.main.classpath` at that copy instead of the normal classpath. Those
entries carry compile-time nullability annotations only, never runtime behaviour, so removing
them is safe.

### 2. LWJGL 3.4.x's FFM backend generates a runtime class

LWJGL 3.4.x defaults to a Java Foreign Function & Memory (FFM) API-based native-calling backend
on JDK 22+. The first time it's used, it generates a *hidden class* bundling native downcall
handles (`org.lwjgl.system.ffm.FFM.generate` → `MethodHandles.Lookup.defineHiddenClassWithClassData`).
Native Image forbids defining new classes at runtime by default, so this fails immediately with
`UnsupportedFeatureError: Classes cannot be defined at runtime`.

Forcing that generation to happen at **build** time instead
(`--initialize-at-build-time=org.lwjgl`) gets past that error, but the generated downcall handles
end up holding native function pointers resolved against the *builder* process's own library
mappings — which are meaningless (and unmapped) in the actual run process. The result is a
segfault on the very first GLFW/GL call.

**Workaround**: pinned LWJGL to **3.3.6** (`enforcedPlatform('org.lwjgl:lwjgl-bom:3.3.6')` — a
plain `platform(...)` isn't enough, since `render-core` transitively imports the 3.4.2 BOM and
Gradle's default conflict resolution picks the higher version otherwise). 3.3.6 predates the
FFM-by-default switch and uses the classic JNI native backend, which has proven Native Image
support (see the [lwjgl3-helloworld-native](https://github.com/chirontt/lwjgl3-helloworld-native)
reference project, which uses `--initialize-at-run-time=org.lwjgl` — the *opposite* flag — for
exactly this reason).

If LWJGL is ever bumped past 3.3.x again, expect this class of failure to resurface, and expect
it to need a real fix upstream (or in LWJGL's own Native Image support) rather than a Gradle
flag.

### 3. Reflection/resource config for `ini4j`, Jackson, Javalin, etc.

Native Image's closed-world analysis can't see `Class.newInstance()`-style reflection (ini4j's
`ServiceLoader`-based INI parser lookup), Jackson's reflective (de)serialization, or Javalin's
route/JSON handling unless told about them explicitly.

**Workaround**: generated via the Native Image tracing agent, running the JVM build with the
agent attached and exercising the app (startup/config parsing, and hitting the remote HTTP API's
`/api/v1/params`, `/api/v1/maps`, `/api/v1/images`, `/api/v1/videos` endpoints):

```bash
JAVA_HOME=/path/to/graalvm ... \
JAVA_OPTS="-agentlib:native-image-agent=config-output-dir=/tmp/agent-cfg --enable-native-access=ALL-UNNAMED" \
app/build/install/cthugha-reborn/bin/cthugha-reborn --remote-token=SOME_TOKEN
```

The resulting `reachability-metadata.json` is checked in at
`app/src/main/resources/META-INF/native-image/io.github.duckasteroid/cthugha-reborn/` — Native
Image picks it up automatically from that location.

**This coverage is not exhaustive.** It only reflects the code paths exercised during agent
capture: startup, config parsing, and those four read endpoints. Bindings, screen configs, tab
generators, and video playback (`javacv`/`ffmpeg`) haven't been exercised under the agent yet —
expect new `ClassNotFoundException`/`NoSuchMethodException`-style errors as more of the app runs
under native-image, and re-run the agent (with `config-merge-dir` instead of `config-output-dir`
to add to, not replace, the existing config) to close each gap as it's found.

### 4. AWT needs `java.home`

The font-rendering path (`QuotePhase`, used for baked/overlay quote text) goes through
`java.desktop`'s `sun.awt.FontConfiguration`, which unconditionally reads the `java.home` system
property to locate font config data. Native Image binaries have no real "JDK home" and leave this
property unset, so the first font metrics lookup throws
`java.lang.Error: java.home property not set`.

**Workaround**: pass `-Djava.home=<any real JDK install>` on the command line at runtime. It
doesn't need to be the GraalVM JDK — any JDK's directory structure satisfies the lookup.

---

## Known gaps

- **Not yet exercised under native-image**: bindings (Janino script compilation), tab
  generators, screen config save/load, video overlay playback. These likely need additional
  reachability metadata — see [§3](#3-reflectionresource-config-for-ini4j-jackson-javalin-etc)
  above for the process to extend it.
- **`gradle.properties` hardcodes a machine-specific path** to the GraalVM install. Fine for a
  single-developer machine; would need generalizing (e.g. `GRAALVM_HOME` env var) to be portable.
- **No wrapper script** — `-Djava.home=...` has to be passed by hand each run. A thin launch
  script (mirroring `startScripts` in `app/build.gradle`) would be a reasonable follow-up.
