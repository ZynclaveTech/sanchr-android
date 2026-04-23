# Proto Source of Truth

The `.proto` files in this directory are **mirrored** from
`backend-oss/crates/sanchr-proto/proto/`. Do not edit them here.

To re-sync after backend protos change:

    ./scripts/sync-protos.sh write

CI runs `./scripts/sync-protos.sh check` on every build. Drift fails the build.
