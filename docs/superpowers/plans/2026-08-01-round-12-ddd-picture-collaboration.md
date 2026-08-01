# Round 12 implementation record

## Goal

Move authorization's picture read model and collaboration's state transitions behind pure domain interfaces while preserving transport contracts.

## Design

- `PictureAsset` is the minimal authorization projection.
- `PictureAssetRepository` separates domain reads from legacy entity CRUD.
- The MyBatis adapter implements both interfaces during incremental migration.
- `CollaborationSession` owns state-transition invariants.
- The application service owns idempotency, metrics, concurrency locking, and protocol exception translation.

## Verification

- Pure picture-domain tests.
- Pure collaboration aggregate tests.
- Existing collaboration application tests.
- Existing MyBatis picture adapter tests.
- Full backend package regression.
