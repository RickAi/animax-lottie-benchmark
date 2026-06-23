#!/usr/bin/env bash
# Copyright 2025 The Lynx Authors. All rights reserved.
# Licensed under the Apache License Version 2.0 that can be found in the
# LICENSE file in the root directory of this source tree.

set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
repo_root=$(cd "$script_dir/../.." && pwd -P)
bundle_path="$repo_root/.bundle/vendor"
command=(pod install --verbose --repo-update)
project_name="AnimaXExample.xcodeproj"

export COCOAPODS_CONVERT_GIT_TO_HTTP=false
export LANG=en_US.UTF-8
export SDKROOT=${SDKROOT:-/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk}
if [[ "${RUBYOPT:-}" != *"-rlogger"* ]]; then
  export RUBYOPT="${RUBYOPT:+$RUBYOPT }-rlogger"
fi

usage() {
  echo "Usage: $0 [OPTIONS]"
  echo "Options:"
  echo "  -h, --help         Show this help message"
  echo "  --enable-trace     Accepted for compatibility; binary pods decide trace support"
  echo "  --disable-trace    Accepted for compatibility; binary pods decide trace support"
}

handle_options() {
  for option in "$@"; do
    case "$option" in
      -h|--help)
        usage
        exit 0
        ;;
      --enable-trace|--disable-trace)
        ;;
      *)
        usage
        exit 1
        ;;
    esac
  done
}

handle_options "$@"

cd "$script_dir"

if command -v bundle >/dev/null && [[ -f Gemfile ]]; then
  bundle install -V --path="$bundle_path"
  bundle exec pod deintegrate "$project_name" || true
  rm -rf Podfile.lock
  bundle exec "${command[@]}"
else
  pod deintegrate "$project_name" || true
  rm -rf Podfile.lock
  "${command[@]}"
fi
