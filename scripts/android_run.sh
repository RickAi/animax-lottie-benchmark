#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
ITERATIONS=3
ENGINE=all
CASE_DURATION_MS=10000
OUT_DIR="$ROOT_DIR/results/android"
BUILD_ONLY=0

usage() {
  cat <<EOF
Usage: $0 [options]

Options:
  --iterations N       Iterations per engine/case. Default: 3.
  --engine NAME        all, animax, or lottie. Default: all.
  --case-duration-ms N Time to keep each launched case on screen. Default: 10000.
  --out DIR            Output directory. Default: results/android.
  --build-only         Build APK but do not install/run.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --iterations) ITERATIONS="$2"; shift 2 ;;
    --engine) ENGINE="$2"; shift 2 ;;
    --case-duration-ms) CASE_DURATION_MS="$2"; shift 2 ;;
    --out) OUT_DIR="$2"; shift 2 ;;
    --build-only) BUILD_ONLY=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 1 ;;
  esac
done

mkdir -p "$OUT_DIR"

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

pushd "$ROOT_DIR/android" >/dev/null
./gradlew :app:assembleNoasanDebug
popd >/dev/null

APK="$ROOT_DIR/android/app/build/outputs/apk/noasan/debug/app-noasan-debug.apk"
if [[ "$BUILD_ONLY" == "1" ]]; then
  echo "Built $APK"
  exit 0
fi

adb install -r "$APK"
adb shell am force-stop com.animax.benchmark >/dev/null
adb shell am start \
  -n com.animax.benchmark/.BenchmarkActivity \
  --ez autorun true \
  --ei iterations "$ITERATIONS" \
  --el caseDurationMs "$CASE_DURATION_MS" \
  --es engine "$ENGINE" >/dev/null

echo "Case runner started. Waiting for final result..."
deadline=$((SECONDS + 3600))
latest=""
while (( SECONDS < deadline )); do
  latest="$(adb shell run-as com.animax.benchmark sh -c 'ls -t files/results/*.json 2>/dev/null | head -1' | tr -d '\r' || true)"
  if [[ -n "$latest" ]]; then
    tmp="$OUT_DIR/.latest.json"
    adb exec-out run-as com.animax.benchmark cat "$latest" > "$tmp" || true
    if python3 - "$tmp" <<'PY'
import json, sys
try:
    with open(sys.argv[1]) as f:
        sys.exit(0 if json.load(f).get("final") is True else 1)
except Exception:
    sys.exit(1)
PY
    then
      out="$OUT_DIR/$(basename "$latest")"
      mv "$tmp" "$out"
      echo "Result: $out"
      exit 0
    fi
  fi
  sleep 5
done

echo "Timed out waiting for final result" >&2
exit 1
