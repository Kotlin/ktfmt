#!/usr/bin/env bash
#   ./playground/run.sh                   # build + run
#   ./playground/run.sh /path/to/ktfmt    # run with predefinied binary

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/.." && pwd)"

binary="${1:-}"
if [[ -z "$binary" ]]; then
  binary="$repo_dir/core/build/native/nativeCompile/ktfmt"
  echo "Building the Native Image binary (this takes a few minutes on a cold build)..."
  (cd "$repo_dir" && ./gradlew :ktfmt:nativeCompile)
fi

if [[ ! -x "$binary" ]]; then
  echo "No executable ktfmt binary at: $binary" >&2
  exit 1
fi

exec python3 "$script_dir/server.py" "$binary"
