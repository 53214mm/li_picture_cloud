# Read-Only Collaboration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let team viewers open picture details and observe live collaboration while preserving server-enforced read-only behavior.

**Architecture:** Separate four capabilities already represented by permission strings: picture viewing, room joining, collaboration commands, and picture replacement. Reuse the current WebSocket room and editor canvas, adding a read-only rendering mode instead of creating a second collaboration protocol.

**Tech Stack:** Java 21, Spring Boot 3, JUnit 5, Mockito, Vue 3, Node 22 test runner, WebSocket, Redis.

## Global Constraints

- Viewer receives `picture:view` and `collaboration:join`, never `picture:edit` or `collaboration:edit`.
- WebSocket handshake checks join permission; every operation message independently checks edit permission.
- Read-only clients receive authoritative state, operation, join, leave, and error events.
- Read-only UI cannot rotate, zoom, save, or emit collaboration commands.
- Existing editor/admin collaboration behavior and WebSocket message formats remain unchanged.
- Do not stage the local `src/main/resources/application.yaml` change.

---

### Task 1: Permit Team Viewers to Open Picture Details

**Files:**
- Modify: `src/main/java/com/li/lipicturecloud/controller/PictureController.java`
- Modify: `src/test/java/com/li/lipicturecloud/controller/PictureControllerTeamAccessTest.java`

**Interfaces:**
- Consumes: `SpaceAuthorizationAccessService.check(PICTURE_VIEW, spaceId, null, null, request)`.
- Produces: `/picture/get/vo` returns `PictureVO` for a team viewer with `picture:view`.

- [ ] **Step 1: Add a failing detail test**

Stub `pictureService.getById(9L)` with a picture in team space `7L`. Call `getPictureVOById(9L, request)` as non-owner user `22L`, and assert no exception plus verification of `PICTURE_VIEW`:

```java
verify(authorizationAccessService).check(
        SpaceUserPermissionConstant.PICTURE_VIEW, 7L, null, null, request);
```

- [ ] **Step 2: Verify RED**

```powershell
.\mvnw.cmd "-Dtest=PictureControllerTeamAccessTest" test
```

Expected: failure because the current endpoint calls `pictureService.checkPictureAuth`, which requires edit permission, and never calls the access service with `PICTURE_VIEW`.

- [ ] **Step 3: Replace the legacy detail check**

For a picture with a non-null `spaceId`, call:

```java
authorizationAccessService.check(
        SpaceUserPermissionConstant.PICTURE_VIEW,
        picture.getSpaceId(), null, null, request);
```

Keep public-picture behavior unchanged.

- [ ] **Step 4: Verify GREEN**

```powershell
.\mvnw.cmd "-Dtest=PictureControllerTeamAccessTest,SpaceAuthorizationManagerTest" test
```

Expected: all focused tests pass.

---

### Task 2: Separate Room Join from Editing

**Files:**
- Modify: `src/main/resources/biz/spaceUserAuthConfig.json`
- Modify: `src/main/java/com/li/lipicturecloud/collaboration/websocket/CollaborationHandshakeInterceptor.java`
- Modify: `src/test/java/com/li/lipicturecloud/collaboration/websocket/CollaborationHandshakeInterceptorTest.java`
- Modify: `src/test/java/com/li/lipicturecloud/manager/auth/SpaceAuthorizationManagerTest.java`

**Interfaces:**
- Consumes: role permissions and `COLLABORATION_JOIN` / `COLLABORATION_EDIT` constants.
- Produces: viewer can handshake, but handler continues checking edit on each command.

- [ ] **Step 1: Write failing role and handshake tests**

Change the handshake expectation to:

```java
verify(access).checkForUser(SpaceUserPermissionConstant.COLLABORATION_JOIN, 7L, 8L);
```

Add authorization assertions that viewer permissions contain `COLLABORATION_JOIN` and exclude `COLLABORATION_EDIT` and `PICTURE_EDIT`.

- [ ] **Step 2: Verify RED**

```powershell
.\mvnw.cmd "-Dtest=CollaborationHandshakeInterceptorTest,SpaceAuthorizationManagerTest" test
```

Expected: handshake still requests edit and viewer role lacks join.

- [ ] **Step 3: Implement permission separation**

Add `collaboration:join` to viewer permissions in `spaceUserAuthConfig.json`. Replace only the handshake check with `COLLABORATION_JOIN`. Do not alter this handler check:

```java
accessService.checkForUser(
        SpaceUserPermissionConstant.COLLABORATION_EDIT, pictureId, userId);
```

- [ ] **Step 4: Verify GREEN and command protection**

```powershell
.\mvnw.cmd "-Dtest=CollaborationHandshakeInterceptorTest,SpaceAuthorizationManagerTest,CollaborationSessionServiceTest" test
```

Expected: join tests pass and command behavior remains green.

---

### Task 3: Add a Read-Only Collaboration UI

**Files:**
- Modify: `li-picture-cloud-frontend/src/utils/spaceAccess.js`
- Modify: `li-picture-cloud-frontend/tests/spaceAccess.test.mjs`
- Modify: `li-picture-cloud-frontend/src/views/PictureDetailView.vue`
- Modify: `li-picture-cloud-frontend/src/components/ImageEditModal.vue`
- Modify: `li-picture-cloud-frontend/src/components/ImageEditor.vue`

**Interfaces:**
- Consumes: permissions returned by `/spaceUser/permissions`.
- Produces: `collaborationMode(permissions, isTeamSpace)` returns `'edit'`, `'view'`, or `null`; `ImageEditModal.readOnly` and `ImageEditor.readOnly` prevent mutations.

- [ ] **Step 1: Write a failing pure permission test**

```javascript
test('chooses edit, view, or no collaboration mode', () => {
  assert.equal(collaborationMode(['collaboration:join', 'collaboration:edit'], true), 'edit')
  assert.equal(collaborationMode(['collaboration:join'], true), 'view')
  assert.equal(collaborationMode(['collaboration:join'], false), null)
  assert.equal(collaborationMode([], true), null)
})
```

- [ ] **Step 2: Verify RED**

```powershell
npm test -- --test-name-pattern="collaboration mode"
```

Expected: missing-export failure.

- [ ] **Step 3: Implement the pure mode helper**

```javascript
export function collaborationMode(permissions, isTeamSpace) {
  if (!isTeamSpace || !hasPermission(permissions, 'collaboration:join')) return null
  return hasPermission(permissions, 'collaboration:edit') ? 'edit' : 'view'
}
```

- [ ] **Step 4: Drive the detail-page entry from the mode**

Replace the existing edit-only `canCollaborate` with mode-derived values. Show:

- `协同编辑` when mode is `edit`;
- `观看协同` when mode is `view`;
- no collaboration entry when mode is null.

Pass `:collaborative="Boolean(mode)"` and `:read-only="mode === 'view'"` to `ImageEditModal`. Preserve ordinary edit access for public/private owners.

Keep the actual backend error message in a new detail error state instead of collapsing every request failure into “图片不存在或已被删除”.

- [ ] **Step 5: Make modal and editor read-only**

Add `readOnly: Boolean` props. In `ImageEditor`, render no toolbar when read-only and guard `emitOperation`:

```javascript
function emitOperation(operation) {
  if (props.readOnly) return
  emit('operation', operation)
}
```

In `ImageEditModal`, change title and connection copy for read-only mode, pass the prop to `ImageEditor`, hide the note and save button, and make the remaining footer button say `关闭`. Guard `handleSave` with an early return when read-only.

- [ ] **Step 6: Verify frontend quality**

```powershell
npm test
npm run lint
npm run build
npm run check:bundle
npm audit --audit-level=high --registry=https://registry.npmjs.org
```

Expected: all commands pass and audit reports zero vulnerabilities.

---

### Task 4: Full Regression, Documentation, and Delivery

**Files:**
- Modify: `docs/round-15-team-space-guide.md`
- Modify only when verification exposes a defect: files named above.

**Interfaces:**
- Consumes: completed backend and frontend read-only behavior.
- Produces: beginner instructions and verified pushed commit.

- [ ] **Step 1: Document read-only collaboration**

Add a section explaining “观看协同”, the join/edit permission split, expected viewer controls, and how to test with editor A and viewer B.

- [ ] **Step 2: Run backend full suite**

```powershell
.\mvnw.cmd "-Dspring.profiles.active=test" test
```

Expected: zero failures and errors; Redis integration tests may skip in the default suite.

- [ ] **Step 3: Run frontend full suite**

```powershell
npm test
npm run lint
npm run build
npm run check:bundle
npm audit --audit-level=high --registry=https://registry.npmjs.org
```

Expected: all checks pass, bundle remains below the configured per-chunk limit, and audit is clean.

- [ ] **Step 4: Check repository hygiene and commit**

```powershell
git diff --check
git status --short
git add src/main/java/com/li/lipicturecloud/controller/PictureController.java src/main/resources/biz/spaceUserAuthConfig.json src/main/java/com/li/lipicturecloud/collaboration/websocket/CollaborationHandshakeInterceptor.java src/test/java/com/li/lipicturecloud/controller/PictureControllerTeamAccessTest.java src/test/java/com/li/lipicturecloud/collaboration/websocket/CollaborationHandshakeInterceptorTest.java src/test/java/com/li/lipicturecloud/manager/auth/SpaceAuthorizationManagerTest.java li-picture-cloud-frontend/src/utils/spaceAccess.js li-picture-cloud-frontend/tests/spaceAccess.test.mjs li-picture-cloud-frontend/src/views/PictureDetailView.vue li-picture-cloud-frontend/src/components/ImageEditModal.vue li-picture-cloud-frontend/src/components/ImageEditor.vue docs/round-15-team-space-guide.md
git commit -m "feat: support read-only team collaboration"
git push origin main
```

Confirm `src/main/resources/application.yaml` remains unstaged.

