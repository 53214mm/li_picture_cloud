# Redis Collaboration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace JVM-local collaboration state and broadcasts with Redis-backed atomic state, idempotency, and cross-instance Pub/Sub while preserving the WebSocket protocol.

**Architecture:** `CollaborationSessionService` delegates state changes to a `CollaborationStateStore` seam. A Lua-backed Redis adapter is authoritative in normal operation, while an in-memory adapter supports isolated tests. WebSocket events travel through a publisher/subscriber seam so every backend instance broadcasts Redis events only to its own connected sessions.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data Redis, Redis Lua, Jackson, JUnit 5, Mockito, Docker Redis, GitHub Actions.

## Global Constraints

- Preserve `/api/ws/collaboration` and all existing request/response JSON fields.
- Redis failures must fail closed; never fall back to divergent local state.
- State and successful command results expire after 24 hours without activity.
- State mutation and command idempotency must be atomic across backend instances.
- Test profile remains runnable without a locally installed Redis; CI executes real Redis integration tests.

---

### Task 1: State store seam and in-memory adapter

**Files:**
- Create: `src/main/java/com/li/lipicturecloud/collaboration/store/CollaborationStateStore.java`
- Create: `src/main/java/com/li/lipicturecloud/collaboration/store/ApplyCollaborationResult.java`
- Create: `src/main/java/com/li/lipicturecloud/collaboration/store/InMemoryCollaborationStateStore.java`
- Create: `src/test/java/com/li/lipicturecloud/collaboration/store/CollaborationStateStoreContract.java`
- Create: `src/test/java/com/li/lipicturecloud/collaboration/store/InMemoryCollaborationStateStoreTest.java`
- Modify: `src/main/java/com/li/lipicturecloud/collaboration/CollaborationSessionService.java`
- Modify: `src/test/java/com/li/lipicturecloud/collaboration/CollaborationSessionServiceTest.java`

**Interfaces:**
- Consumes: `CollaborationCommand`, `CollaborationState`, domain `CollaborationSession`.
- Produces: `current(Long)`, `apply(CollaborationCommand)`, `activeSessionCount()` and `ApplyCollaborationResult(state, newlyApplied)`.

- [x] **Step 1: Write failing store contract tests**

Cover initial state, all four actions, stale versions, repeated command IDs, and separate pictures. The production change that makes each test pass is a state store implementing the wished-for interface.

- [x] **Step 2: Verify RED**

Run `./mvnw -B -Dtest=InMemoryCollaborationStateStoreTest test`; expect compilation failure because the store types do not exist.

- [x] **Step 3: Implement the in-memory adapter**

Move the current per-picture locking and command-result map behind `CollaborationStateStore`. Return `newlyApplied=false` for repeats and translate domain stale-version failures to `CollaborationVersionConflictException`.

- [x] **Step 4: Inject the seam into the application service**

Keep validation and metrics in `CollaborationSessionService`. Increment applied or duplicate counters from `ApplyCollaborationResult`; derive session count from the store.

- [x] **Step 5: Verify GREEN**

Run focused store and application tests; expect all existing behavior and metrics assertions to pass.

### Task 2: Redis Lua atomic state adapter

**Files:**
- Create: `src/main/java/com/li/lipicturecloud/collaboration/store/RedisCollaborationStateStore.java`
- Create: `src/main/resources/redis/collaboration-apply.lua`
- Create: `src/test/java/com/li/lipicturecloud/collaboration/store/RedisCollaborationStateStoreTest.java`
- Modify: `src/test/resources/application-test.yaml`

**Interfaces:**
- Consumes: `StringRedisTemplate`, `ObjectMapper`, state-store interface.
- Produces: the same observable contract as the in-memory adapter.

- [x] **Step 1: Add real Redis integration tests**

Connect to the Redis host and port supplied by system properties and assert initialization, operation state, version conflict, duplicate result, and positive TTL. Local verification uses a disposable Docker Redis; CI supplies a Redis service container.

- [x] **Step 2: Verify RED**

Run `./mvnw -B -Dtest=RedisCollaborationStateStoreTest test`; expect failure because the Redis adapter and Lua resource do not exist.

- [x] **Step 3: Implement Redis reads and key construction**

Use state key `picture-cloud:collaboration:state:{<pictureId>}` and command key `picture-cloud:collaboration:command:{<pictureId>}:<commandId>`. Initialize absent states as rotation `0`, scale `1.0`, version `0`.

- [x] **Step 4: Implement the Lua mutation**

The script checks the command key, validates the base version, calculates/clamps the operation, increments version, writes the hash and serialized command result, applies millisecond TTL to both keys, and returns a typed status plus state fields.

- [x] **Step 5: Map Lua results and Redis errors**

Map duplicate/applied/version-conflict statuses to existing Java results and exceptions. Let Redis connectivity failures surface as collaboration errors; do not invoke the in-memory adapter.

- [x] **Step 6: Verify GREEN**

Run the Redis integration test against the container and the in-memory contract tests; expect identical state behavior.

### Task 3: Cross-instance event bus

**Files:**
- Create: `src/main/java/com/li/lipicturecloud/collaboration/event/CollaborationEventEnvelope.java`
- Create: `src/main/java/com/li/lipicturecloud/collaboration/event/CollaborationEventPublisher.java`
- Create: `src/main/java/com/li/lipicturecloud/collaboration/event/RedisCollaborationEventPublisher.java`
- Create: `src/main/java/com/li/lipicturecloud/collaboration/event/CollaborationEventSubscriber.java`
- Create: `src/main/java/com/li/lipicturecloud/config/CollaborationRedisConfig.java`
- Create: `src/test/java/com/li/lipicturecloud/collaboration/event/CollaborationEventSubscriberTest.java`
- Modify: `src/main/java/com/li/lipicturecloud/collaboration/websocket/CollaborationWebSocketHandler.java`

**Interfaces:**
- Consumes: existing `CollaborationEvent`, Redis channel, local-room broadcast callback.
- Produces: `publish(pictureId, event)` and subscriber delivery into `broadcastLocal(pictureId, event)`.

- [x] **Step 1: Write failing subscriber propagation test**

Create two subscriber endpoints on an in-test event publisher and assert an event published from instance A reaches the local broadcast callback representing instance B exactly once.

- [x] **Step 2: Verify RED**

Run the focused event test; expect missing event seam types.

- [x] **Step 3: Implement event envelope and Redis publisher**

Envelope fields are `eventId`, `sourceInstanceId`, `pictureId`, and `event`. Serialize with the shared `ObjectMapper` and publish to the configured channel.

- [x] **Step 4: Configure Redis listener container**

Deserialize messages and call the subscriber. Malformed messages are logged and discarded without terminating the listener.

- [x] **Step 5: Route WebSocket broadcasts through the event bus**

JOIN, LEAVE, and successful OPERATION publish once. Subscriber callbacks call a public local-broadcast method. STATE and ERROR responses remain point-to-point and are not published.

- [x] **Step 6: Verify GREEN**

Run event and existing WebSocket tests; expect no protocol changes and no double delivery.

### Task 4: Configuration, CI, documentation, and final verification

**Files:**
- Modify: `src/main/resources/application.yaml`
- Modify: `src/main/resources/application-prod.yaml`
- Modify: `src/test/resources/application-test.yaml`
- Modify: `.github/workflows/ci.yml`
- Modify: `docs/未决问题.md`
- Create: `docs/轮次/第14轮-Redis多实例协同编辑.md`

**Interfaces:**
- Consumes: environment variables `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, collaboration TTL/channel properties.
- Produces: deterministic profile selection and CI Redis integration coverage.

- [x] **Step 1: Add conditional store configuration**

Select Redis adapter for `app.collaboration.store=redis` and memory adapter for `memory`. Set test profile to memory for the normal suite; tag Redis container tests so CI can run them explicitly.

- [x] **Step 2: Add CI Redis service and integration command**

Start `redis:7.4-alpine` with a health check, run normal Maven verification, then run the Redis integration test with its enabled property.

- [x] **Step 3: Update operations documentation**

Explain keys, TTL, Lua atomicity, Pub/Sub recovery, required environment variables, local verification, monitoring, Redis outage behavior, and rolling-deployment constraints in beginner-friendly language.

- [x] **Step 4: Resolve the recorded limitation**

Remove the cross-instance item from `docs/未决问题.md`; leave the document structure intact and state that no unresolved implementation blockers remain if applicable.

- [x] **Step 5: Run complete verification**

Run Maven `verify`, Redis integration tests, frontend lint/test/build/bundle audit, `git diff --check`, and confirm the JaCoCo report exists.

- [x] **Step 6: Commit and push**

Stage only this round’s files, commit as `feat: support Redis-backed multi-instance collaboration`, push `main` to `origin`, and verify a clean working tree.
