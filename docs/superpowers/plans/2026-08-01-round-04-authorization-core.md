# Round 04 Authorization Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract deterministic permission rules into a pure Java module reusable by HTTP and WebSocket adapters.

**Architecture:** Callers provide an `AuthorizationSubject` and a pre-resolved `SpaceAuthorizationResource` to the `AuthorizationManager` interface. `SpaceAuthorizationManager` hides public, private, and team-space policy and delegates only team-role mapping to `SpaceUserAuthManager`.

**Tech Stack:** Java 21, JUnit 5, AssertJ, Spring Boot 3.5

## Global Constraints

- Preserve `viewer`, `editor`, and `admin` as the only persisted team roles.
- Keep existing endpoints operational during this round.
- Public and private resources never grant collaboration permissions.
- Unknown roles and missing subjects fail closed with an empty permission set.
- All permission collections returned to callers are immutable.

---

### Task 1: Define permission vocabulary and immutable inputs

**Files:**
- Modify: `src/main/java/com/li/lipicturecloud/manager/auth/model/SpaceUserPermissionConstant.java`
- Create: `src/main/java/com/li/lipicturecloud/manager/auth/model/AuthorizationSubject.java`
- Create: `src/main/java/com/li/lipicturecloud/manager/auth/model/SpaceAuthorizationResource.java`
- Create: `src/main/java/com/li/lipicturecloud/manager/auth/model/SpaceAuthorizationResourceType.java`

**Interfaces:**
- Produces: immutable subject and resource facts used by the policy.

- [ ] Add space and collaboration permission constants.
- [ ] Add record types with constructor validation and static factories for public picture, private space, and team space.
- [ ] Compile tests to verify the desired types are available.

### Task 2: Implement the permission policy test-first

**Files:**
- Create: `src/main/java/com/li/lipicturecloud/manager/auth/AuthorizationManager.java`
- Create: `src/main/java/com/li/lipicturecloud/manager/auth/SpaceAuthorizationManager.java`
- Modify: `src/main/java/com/li/lipicturecloud/manager/auth/SpaceUserAuthManager.java`
- Modify: `src/main/resources/biz/spaceUserAuthConfig.json`
- Create: `src/test/java/com/li/lipicturecloud/manager/auth/SpaceAuthorizationManagerTest.java`

**Interfaces:**
- Produces: `Set<String> getPermissions(AuthorizationSubject, SpaceAuthorizationResource)` and default `boolean hasPermission(...)`.

- [ ] Write parameterized tests for public, private, and team rules.
- [ ] Run the focused test and confirm compilation fails because the policy does not exist.
- [ ] Implement the smallest policy satisfying the matrix.
- [ ] Return immutable defensive copies from role mapping.
- [ ] Run focused and full tests.

### Task 3: Document, verify, commit, and push

**Files:**
- Verify: `docs/轮次/第04轮-权限领域核心.md`
- Verify: all files from Tasks 1 and 2

**Interfaces:**
- Consumes: focused tests and repository-wide package command.
- Produces: a reviewed round commit on `main` and a green remote CI run.

- [ ] Run `git diff --check`.
- [ ] Run `mvnw -B -Dtest=SpaceAuthorizationManagerTest test`.
- [ ] Run `mvnw -B -Dspring.profiles.active=test package`.
- [ ] Commit with `feat: establish authorization policy core`.
- [ ] Push `main` and verify GitHub Actions succeeds.
