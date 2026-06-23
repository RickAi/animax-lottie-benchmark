#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
ANIMAX_DIR="$ROOT_DIR/third_party/animax"

git -C "$ROOT_DIR" submodule update --init --recursive

if [[ ! -x "$ANIMAX_DIR/tools/hab" ]]; then
  echo "AnimaX submodule is incomplete at third_party/animax." >&2
  exit 1
fi

pushd "$ANIMAX_DIR" >/dev/null
tools/hab sync .
popd >/dev/null

echo "AnimaX dependencies are ready under third_party/animax."
