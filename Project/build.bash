#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
HOMEWORK_DIR="$REPO_ROOT/Homework"

HOMEWORK_BIN="$HOMEWORK_DIR/bin"
PROJECT_BIN="$SCRIPT_DIR/bin"
PROTOBUF_JAR="$SCRIPT_DIR/lib/protobuf-java-4.28.3.jar"

if [[ "$(uname -s)" == MINGW* || "$(uname -s)" == MSYS* || "$(uname -s)" == CYGWIN* ]]; then
    CP_SEP=";"
    # javac on Windows requires Windows-style paths in -cp values; convert.
    if command -v cygpath >/dev/null 2>&1; then
        HOMEWORK_BIN="$(cygpath -w "$HOMEWORK_BIN")"
        PROJECT_BIN="$(cygpath -w "$PROJECT_BIN")"
        PROTOBUF_JAR="$(cygpath -w "$PROTOBUF_JAR")"
    fi
else
    CP_SEP=":"
fi

if [[ ! -f "$PROTOBUF_JAR" ]]; then
    echo "Missing protobuf jar: $PROTOBUF_JAR" >&2
    exit 1
fi

mkdir -p "$HOMEWORK_BIN"
mkdir -p "$PROJECT_BIN"

(
    cd "$HOMEWORK_DIR"
    find src -name '*.java' -print0 | xargs -0 javac -d bin
)

(
    cd "$SCRIPT_DIR"
    # Compile project sources (src + src-gen) with protobuf-java on classpath.
    find src src-gen -name '*.java' -print0 \
        | xargs -0 javac -cp "$HOMEWORK_BIN${CP_SEP}$PROTOBUF_JAR" -d bin
)

echo "Compiled."
echo "Run with: java -cp \"$HOMEWORK_BIN${CP_SEP}$PROJECT_BIN${CP_SEP}$PROTOBUF_JAR\" project.ProjectApp"
