#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SRC="$ANDROID_ROOT/../../backend-oss/crates/sanchr-proto/proto"
DST="$ANDROID_ROOT/proto/src/main/proto/sanchr"

if [[ ! -d "$SRC" ]]; then
  echo "ERROR: canonical proto dir not found: $SRC" >&2
  exit 1
fi

MODE="${1:-check}"

mkdir -p "$DST"

case "$MODE" in
  write)
    rsync -a --delete --include='*.proto' --exclude='*' "$SRC/" "$DST/"
    echo "synced protos: $SRC -> $DST"
    ;;
  check)
    TMP="$(mktemp -d)"
    trap 'rm -rf "$TMP"' EXIT
    rsync -a --include='*.proto' --exclude='*' "$SRC/" "$TMP/"
    if ! diff -r "$TMP" "$DST" >/dev/null 2>&1; then
      echo "ERROR: android protos drifted from $SRC" >&2
      echo "Run: ./scripts/sync-protos.sh write" >&2
      diff -r "$TMP" "$DST" || true
      exit 2
    fi
    echo "protos in sync"
    ;;
  *)
    echo "usage: $0 [check|write]" >&2
    exit 64
    ;;
esac
