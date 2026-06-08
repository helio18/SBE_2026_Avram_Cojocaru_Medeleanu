#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
HOMEWORK_DIR="$REPO_ROOT/Homework"

HOMEWORK_BIN="$HOMEWORK_DIR/bin"
PROJECT_BIN="$SCRIPT_DIR/bin"

if [[ "$(uname -s)" == MINGW* || "$(uname -s)" == MSYS* || "$(uname -s)" == CYGWIN* ]]; then
    CP_SEP=";"
else
    CP_SEP=":"
fi

mkdir -p "$HOMEWORK_BIN"
mkdir -p "$PROJECT_BIN"

(
    cd "$HOMEWORK_DIR"
    find src -name '*.java' -print0 | xargs -0 javac -d bin
)

(
    cd "$SCRIPT_DIR"
    find src -name '*.java' -print0 | xargs -0 javac -cp "$HOMEWORK_BIN" -d bin
)

echo "Compiled."
echo "Run with: java -cp \"$HOMEWORK_BIN${CP_SEP}$PROJECT_BIN\" project.ProjectApp"
