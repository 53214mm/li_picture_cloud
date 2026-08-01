# Round 11 implementation record

## Goal

Introduce explicit bounded-context vocabulary and migrate the space-membership read seam used by authorization without changing external behavior.

## Completed design

1. Pure domain membership and role types own invariants.
2. Domain repository is the read seam.
3. MyBatis adapter owns persistence mapping.
4. Authorization consumes domain language instead of persistence entities.
5. Context map records dependencies between identity/access, space, picture, and collaboration.

## Compatibility boundary

Controllers and legacy CRUD services remain available. This is a branch-by-abstraction migration; callers can move incrementally and each round remains deployable.

## Verification

- Domain invariant tests.
- Persistence adapter mapping test.
- Full backend package and existing regression suite.
