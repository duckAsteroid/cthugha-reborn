#!/usr/bin/env bash
# Runs the GraalVM native-image build of cthugha-reborn (see docs/NATIVE_IMAGE.md).
#
# Build it first with:
#   JAVA_HOME=/home/chris/.sdkman/candidates/java/25.3.4+1.r25-graalce ./gradlew :app:nativeCompile
#
# Any arguments given to this script (e.g. --remote-token=..., --audio-source=...) are
# forwarded to the binary as-is.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
binary="$repo_root/app/build/native/nativeCompile/cthugha-reborn"
workdir="$repo_root/app/src/dist"

if [[ ! -x "$binary" ]]; then
	echo "error: native binary not found at $binary" >&2
	echo "build it first: JAVA_HOME=/home/chris/.sdkman/candidates/java/25.3.4+1.r25-graalce ./gradlew :app:nativeCompile" >&2
	exit 1
fi

# AWT's font config lookup needs java.home set to *some* real JDK install (native-image
# binaries have no JDK home of their own) - see docs/NATIVE_IMAGE.md #4.
java_home="${JAVA_HOME:-$(readlink -f /home/chris/.sdkman/candidates/java/current 2>/dev/null || true)}"
if [[ -z "$java_home" || ! -d "$java_home" ]]; then
	echo "error: no JDK found for -Djava.home (set JAVA_HOME, or check ~/.sdkman/candidates/java/current)" >&2
	exit 1
fi

# Same audio-routing dance as the `run` Gradle task: capture the current default source,
# switch to the current sink's monitor for the run, then restore it on exit.
prev_source="$(pactl get-default-source 2>/dev/null || true)"
default_sink="$(pactl get-default-sink 2>/dev/null || true)"
if [[ -n "$default_sink" ]]; then
	pactl set-default-source "${default_sink}.monitor" 2>/dev/null || true
fi
restore_audio() {
	if [[ -n "$prev_source" ]]; then
		pactl set-default-source "$prev_source" 2>/dev/null || true
	fi
}
trap restore_audio EXIT

xauthority="${XAUTHORITY:-}"
if [[ -z "$xauthority" ]]; then
	xauthority="$(find "/run/user/$(id -u)" -maxdepth 1 -name '.mutter-Xwaylandauth*' 2>/dev/null | head -1)"
fi

# Fixed remote-control token for local dev, so the same browser session/URL survives
# restarts (matches the Gradle `run` task's default). Overridable by passing your own
# --remote-token=... argument to this script.
args=("$@")
has_token=0
for a in "${args[@]}"; do
	[[ "$a" == --remote-token=* ]] && has_token=1 && break
done
[[ "$has_token" -eq 0 ]] && args+=("--remote-token=THIS_IS_A_TEST")

cd "$workdir"
export __NV_PRIME_RENDER_OFFLOAD=1
export __GLX_VENDOR_LIBRARY_NAME=nvidia
[[ -n "$xauthority" ]] && export XAUTHORITY="$xauthority"

exec "$binary" \
	--enable-native-access=ALL-UNNAMED \
	-Djava.home="$java_home" \
	"${args[@]}"
