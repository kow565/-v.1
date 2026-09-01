#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || ! "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Usage: $0 X.Y.Z" >&2
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FILE="$ROOT/version.properties"
OLD_CODE="$(grep '^VERSION_CODE=' "$FILE" | cut -d= -f2)"
NEW_CODE=$((OLD_CODE + 1))

cat > "$FILE" <<EOF
VERSION_NAME=$1
VERSION_CODE=$NEW_CODE
EOF

echo "Bumped to v$1 (versionCode=$NEW_CODE)"
