#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
IOS_ASSETS="$ROOT_DIR/ios/AnimaXBenchmark/Assets/export_output"

rm -rf "$IOS_ASSETS"
mkdir -p "$IOS_ASSETS/lotties"
cp "$ROOT_DIR/assets/manifest.json" "$IOS_ASSETS/manifest.json"
cp "$ROOT_DIR/assets/lotties/"*.json "$IOS_ASSETS/lotties/"

cp "$ROOT_DIR/assets/lotties/hamburger_arrow.json" "$ROOT_DIR/ios/AnimaXBenchmark/Assets/simple_shape.json"
cp "$ROOT_DIR/assets/lotties/lottie_logo_2.json" "$ROOT_DIR/ios/AnimaXBenchmark/Assets/dp.json"

echo "Synced assets to $IOS_ASSETS"
