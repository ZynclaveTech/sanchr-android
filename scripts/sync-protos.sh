#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# Canonical proto source. Defaults to the sibling backend checkout used in
# local development; CI has no sibling layout and sets SANCHR_PROTO_SRC to a
# sparse checkout of the backend instead.
SRC="${SANCHR_PROTO_SRC:-$ANDROID_ROOT/../../backend/crates/sanchr-proto/proto}"
DST="$ANDROID_ROOT/proto/src/main/proto"

if [[ ! -d "$SRC" ]]; then
  if [[ -n "${CI:-}" ]]; then
    # On a runner the backend is only present when BACKEND_READ_TOKEN is
    # configured (both repos are private). Degrade visibly rather than fail
    # every job: the gate still runs locally for anyone with the sibling
    # checkout, and becomes real in CI the moment the secret exists.
    echo "::warning title=proto drift gate skipped::canonical proto dir not present ($SRC); configure the BACKEND_READ_TOKEN secret to enable the gate in CI"
    exit 0
  fi
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
