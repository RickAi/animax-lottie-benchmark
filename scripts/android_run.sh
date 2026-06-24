#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
ADB="${ADB:-adb}"
ENGINE="animax"
COUNT="8"
CASE_ID=""
ANIMAX_MULTITHREAD="false"
ANIMAX_IMAGE_MODE="false"
LOTTIE_ASYNC_UPDATES="false"
BUILD_ONLY=0
HOME_ONLY=0

usage() {
  cat <<EOF
Usage: $0 [options]

Options:
  --engine NAME   animax or lottie. Default: animax.
  --count N       8, 12, 16, or 20. Default: 8.
  --case ID       count-8, count-12, count-16, or count-20.
                  Overrides --count when set.
  --animax-multithread
                  Enable AnimaX multi-thread acceleration. Default: false.
  --animax-image-mode
                  Use AnimaXImageView instead of AnimaXView. Default: false.
  --lottie-async-updates
                  Enable Android Lottie async updates. Default: false.
  --home          Launch the home screen instead of a scene.
  --build-only    Build APK but do not install/run.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --engine) ENGINE="$2"; shift 2 ;;
    --count) COUNT="$2"; shift 2 ;;
    --case) CASE_ID="$2"; shift 2 ;;
    --animax-multithread) ANIMAX_MULTITHREAD="true"; shift ;;
    --animax-image-mode) ANIMAX_IMAGE_MODE="true"; shift ;;
    --lottie-async-updates) LOTTIE_ASYNC_UPDATES="true"; shift ;;
    --home) HOME_ONLY=1; shift ;;
    --build-only) BUILD_ONLY=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 1 ;;
  esac
done

if [[ "$ENGINE" != "animax" && "$ENGINE" != "lottie" ]]; then
  echo "--engine must be animax or lottie" >&2
  exit 1
fi

if [[ -n "$CASE_ID" && "$CASE_ID" != "count-8" && "$CASE_ID" != "count-12" && "$CASE_ID" != "count-16" && "$CASE_ID" != "count-20" ]]; then
  echo "--case must be count-8, count-12, count-16, or count-20" >&2
  exit 1
fi

if [[ -z "$CASE_ID" && "$COUNT" != "8" && "$COUNT" != "12" && "$COUNT" != "16" && "$COUNT" != "20" ]]; then
  echo "--count must be 8, 12, 16, or 20" >&2
  exit 1
fi

java_major="$(java -version 2>&1 | awk -F[\".] '/version/ {print $2; exit}')"
if [[ -z "$java_major" || "$java_major" -lt 17 ]]; then
  if [[ -x /usr/libexec/java_home ]]; then
    candidate_java_home="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
    if [[ -n "$candidate_java_home" && -x "$candidate_java_home/bin/java" ]]; then
      export JAVA_HOME="$candidate_java_home"
      export PATH="$JAVA_HOME/bin:$PATH"
      java_major="$(java -version 2>&1 | awk -F[\".] '/version/ {print $2; exit}')"
    fi
  fi
fi

if [[ -z "$java_major" || "$java_major" -lt 17 ]]; then
  echo "Android benchmark requires JDK 17 or newer for Gradle 8.11.1/AGP 8.9.1. Current java major: ${java_major:-unknown}" >&2
  echo "Set JAVA_HOME to a JDK 17+ installation before running this script." >&2
  exit 1
fi

if ! command -v "$ADB" >/dev/null 2>&1; then
  if [[ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]]; then
    ADB="$HOME/Library/Android/sdk/platform-tools/adb"
  else
    echo "adb was not found. Set ADB or add Android platform-tools to PATH." >&2
    exit 1
  fi
fi

pushd "$ROOT_DIR/android" >/dev/null
./gradlew :app:assembleNoasanDebug
popd >/dev/null

APK="$ROOT_DIR/android/app/build/outputs/apk/noasan/debug/app-noasan-debug.apk"
if [[ "$BUILD_ONLY" == "1" ]]; then
  echo "Built $APK"
  exit 0
fi

"$ADB" install -r "$APK"
"$ADB" shell am force-stop com.animax.benchmark >/dev/null

if [[ "$HOME_ONLY" == "1" ]]; then
  "$ADB" shell am start -n com.animax.benchmark/.BenchmarkActivity >/dev/null
  echo "Launched benchmark home screen"
else
  start_args=(
    -n com.animax.benchmark/.BenchmarkActivity
    --ez autorun true
    --es engine "$ENGINE"
    --ez animaxMultiThread "$ANIMAX_MULTITHREAD"
    --ez animaxImageMode "$ANIMAX_IMAGE_MODE"
    --ez lottieAsyncUpdates "$LOTTIE_ASYNC_UPDATES"
  )
  if [[ -n "$CASE_ID" ]]; then
    start_args+=(--es caseId "$CASE_ID")
    scene_label="$CASE_ID"
  else
    start_args+=(--ei count "$COUNT")
    scene_label="x$COUNT"
  fi
  "$ADB" shell am start "${start_args[@]}" >/dev/null
  echo "Launched $ENGINE $scene_label scene"
fi
