#!/usr/bin/env bash
#   ./playground/run.sh                       # build + run
#   ./playground/run.sh /path/to/ktfmt.jar    # run with a prebuilt jar

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/.." && pwd)"

jar="${1:-}"
if [[ -z "$jar" ]]; then
  echo "Building the ktfmt jar..."
  (cd "$repo_dir" && ./gradlew :ktfmt:shadowJar)

  jars=("$repo_dir"/core/build/libs/ktfmt-*-with-dependencies.jar)
  jar="${jars[0]}"
fi

if [[ ! -f "$jar" ]]; then
  echo "No ktfmt jar at: $jar" >&2
  exit 1
fi

exec python3 "$script_dir/server.py" "$jar"
