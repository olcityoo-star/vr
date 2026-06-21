#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GRADLE_VERSION="8.10.2"
DIST_DIR="$ROOT_DIR/.gradle/bootstrap"
GRADLE_HOME="$DIST_DIR/gradle-$GRADLE_VERSION"
ZIP_FILE="$DIST_DIR/gradle-$GRADLE_VERSION-bin.zip"
DIST_URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"

if [[ ! -x "$GRADLE_HOME/bin/gradle" ]]; then
    mkdir -p "$DIST_DIR"
    if [[ ! -f "$ZIP_FILE" ]]; then
        if command -v curl >/dev/null 2>&1; then
            curl -fL "$DIST_URL" -o "$ZIP_FILE"
        elif command -v wget >/dev/null 2>&1; then
            wget "$DIST_URL" -O "$ZIP_FILE"
        else
            echo "Neither curl nor wget is available to download Gradle." >&2
            exit 1
        fi
    fi
    python3 - "$ZIP_FILE" "$DIST_DIR" <<'PY'
import sys
import zipfile

zip_file, dist_dir = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(zip_file) as archive:
    archive.extractall(dist_dir)
PY
fi

chmod +x "$GRADLE_HOME/bin/gradle"

exec "$GRADLE_HOME/bin/gradle" "$@"
