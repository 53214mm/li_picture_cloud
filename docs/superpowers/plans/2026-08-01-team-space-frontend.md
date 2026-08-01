# Team Space Frontend Business Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a complete, permission-driven team-space flow from creation and discovery through member administration and collaborative picture editing.

**Architecture:** Keep the existing space routes and picture components, add small frontend modules for space metadata, grouping, API access, cards, and member management, and let pages compose them. Move membership invariants into `SpaceUserService` so every caller receives the same duplicate-member and creator-protection checks; controllers remain HTTP adapters.

**Tech Stack:** Java 21, Spring Boot 3, MyBatis-Plus, JUnit 5, Mockito, Vue 3 Composition API, Vue Router, Axios, Node 22 test runner, ESLint 9, Vite 5.

## Global Constraints

- This round completes team-space behavior only; the site-wide layout, typography, color, component-style, and responsive redesign belongs to Round 17.
- A user may create at most one private space and one team space, but may join multiple teams created by other users.
- Add members by numeric user ID; invitation links, notifications, and invitation acceptance are outside this round.
- The server-provided permission set is the source of truth for visible and enabled actions.
- The team creator remains an administrator and their membership cannot be deleted or demoted.
- Existing WebSocket and Redis collaboration protocols must remain unchanged.
- Do not stage or commit the developer's local `src/main/resources/application.yaml` changes.
- Every user-facing failure must display the backend message when available and fall back to a clear Chinese message.

---

## File Structure

### Backend

- Modify `src/main/java/com/li/lipicturecloud/service/SpaceUserService.java`: publish guarded membership mutation methods.
- Modify `src/main/java/com/li/lipicturecloud/service/impl/SpaceUserServiceImpl.java`: enforce duplicate-member, team-only, creator-role, and creator-removal rules.
- Modify `src/main/java/com/li/lipicturecloud/controller/SpaceUserController.java`: delegate edit and delete operations to the guarded service methods.
- Create `src/test/java/com/li/lipicturecloud/service/impl/SpaceUserServiceImplTest.java`: isolate membership invariant tests with mocks.
- Modify `src/test/java/com/li/lipicturecloud/controller/SpaceUserControllerTest.java`: verify controllers call the guarded service boundary.

### Frontend

- Modify `li-picture-cloud-frontend/src/constants/space.js`: define space types, member roles, labels, and presentation helpers.
- Create `li-picture-cloud-frontend/src/utils/spaceAccess.js`: pure grouping, de-duplication, and permission helpers.
- Create `li-picture-cloud-frontend/tests/spaceAccess.test.mjs`: test all pure team-space rules.
- Modify `li-picture-cloud-frontend/src/api/spaceUser.js`: expose all membership and space-permission endpoints.
- Create `li-picture-cloud-frontend/src/components/space/SpaceCard.vue`: render a reusable space summary and role badge.
- Create `li-picture-cloud-frontend/src/components/space/SpaceMemberPanel.vue`: list, add, edit, and remove team members.
- Modify `li-picture-cloud-frontend/src/views/SpaceCreateView.vue`: select and submit private/team type.
- Modify `li-picture-cloud-frontend/src/views/MySpaceView.vue`: show owned and joined spaces in three sections.
- Modify `li-picture-cloud-frontend/src/views/SpaceManageView.vue`: add type and role visibility while preserving administration behavior.
- Modify `li-picture-cloud-frontend/src/views/SpaceDetailView.vue`: load permissions and mount member management.
- Modify `li-picture-cloud-frontend/src/views/PictureDetailView.vue`: make the collaborative-edit label and entry team-specific while reusing the existing client.
- Create `docs/round-15-team-space-guide.md`: beginner-facing use, verification, and troubleshooting guide.

---

### Task 1: Protect Team Membership Invariants on the Server

**Files:**
- Create: `src/test/java/com/li/lipicturecloud/service/impl/SpaceUserServiceImplTest.java`
- Modify: `src/main/java/com/li/lipicturecloud/service/SpaceUserService.java`
- Modify: `src/main/java/com/li/lipicturecloud/service/impl/SpaceUserServiceImpl.java`
- Modify: `src/main/java/com/li/lipicturecloud/controller/SpaceUserController.java`

**Interfaces:**
- Consumes: `SpaceUserAddRequest`, `SpaceUserEditRequest`, `Space`, `SpaceUser`, `SpaceTypeEnum.TEAM`, and `SpaceRoleEnum.ADMIN`.
- Produces: `boolean editSpaceUser(SpaceUserEditRequest request)` and `boolean deleteSpaceUser(long id)`; `addSpaceUser` additionally guarantees team-only unique membership.

- [ ] **Step 1: Write failing service tests**

Create tests using Mockito mocks injected with `ReflectionTestUtils`. Include these exact cases:

```java
@Test
void rejectsDuplicateMember() {
    when(userService.getById(22L)).thenReturn(user(22L));
    when(spaceService.getById(7L)).thenReturn(teamSpace(7L, 11L));
    when(spaceUserMapper.selectCount(any())).thenReturn(1L);

    SpaceUserAddRequest request = new SpaceUserAddRequest();
    request.setSpaceId(7L);
    request.setUserId(22L);
    request.setSpaceRole("editor");

    BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.addSpaceUser(request));
    assertEquals("该用户已在团队中", exception.getMessage());
}

@Test
void rejectsMemberAddedToPrivateSpace() {
    when(userService.getById(22L)).thenReturn(user(22L));
    when(spaceService.getById(7L)).thenReturn(privateSpace(7L, 11L));

    BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.addSpaceUser(addRequest(7L, 22L, "viewer")));
    assertEquals("只有团队空间可以添加成员", exception.getMessage());
}

@Test
void rejectsDeletingCreatorMembership() {
    when(spaceUserMapper.selectById(3L)).thenReturn(membership(3L, 7L, 11L, "admin"));
    when(spaceService.getById(7L)).thenReturn(teamSpace(7L, 11L));

    BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.deleteSpaceUser(3L));
    assertEquals("不能移除团队创建者", exception.getMessage());
}

@Test
void rejectsDemotingCreatorMembership() {
    when(spaceUserMapper.selectById(3L)).thenReturn(membership(3L, 7L, 11L, "admin"));
    when(spaceService.getById(7L)).thenReturn(teamSpace(7L, 11L));

    BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.editSpaceUser(editRequest(3L, "editor")));
    assertEquals("团队创建者必须保留管理员角色", exception.getMessage());
}
```

Also test the successful add, edit, and delete paths return the mapper/service result.

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\mvnw.cmd -Dtest=SpaceUserServiceImplTest test
```

Expected: compilation fails because `editSpaceUser` and `deleteSpaceUser` do not exist, or invariant assertions fail against the current implementation.

- [ ] **Step 3: Add guarded service interfaces and implementation**

Add to `SpaceUserService`:

```java
boolean editSpaceUser(SpaceUserEditRequest spaceUserEditRequest);

boolean deleteSpaceUser(long id);
```

Implement the rules in `SpaceUserServiceImpl`:

```java
private Space requireTeamSpace(Long spaceId) {
    Space space = spaceService.getById(spaceId);
    ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
    ThrowUtils.throwIf(!Objects.equals(space.getSpaceType(), SpaceTypeEnum.TEAM.getValue()),
            ErrorCode.PARAMS_ERROR, "只有团队空间可以管理成员");
    return space;
}

private void ensureNotCreator(Space space, SpaceUser member, String message) {
    ThrowUtils.throwIf(Objects.equals(space.getUserId(), member.getUserId()),
            ErrorCode.OPERATION_ERROR, message);
}
```

Before saving a new member, call `requireTeamSpace`, then query by `spaceId` and `userId`; throw `OPERATION_ERROR` with `该用户已在团队中` when found. Default a blank new role to `viewer`, validate it, and save once.

For edit, load the existing membership, load its team, reject any creator role other than `admin`, validate a partial `SpaceUser` containing the ID and role, then call `updateById`.

For delete, load the membership and team, call `ensureNotCreator(..., "不能移除团队创建者")`, then call `removeById`.

- [ ] **Step 4: Make the controller delegate to the protected boundary**

Replace direct `removeById` and `updateById` calls with:

```java
boolean result = spaceUserService.deleteSpaceUser(deleteRequest.getId());
```

and:

```java
boolean result = spaceUserService.editSpaceUser(spaceUserEditRequest);
```

Keep the existing `@SpacePermission` annotations so authorization runs before business mutation.

- [ ] **Step 5: Run focused and related backend tests**

Run:

```powershell
.\mvnw.cmd -Dtest=SpaceUserServiceImplTest,SpaceUserControllerTest,SpacePermissionInterceptorTest,SpaceAuthorizationManagerTest test
```

Expected: all selected tests pass with zero failures and zero errors.

- [ ] **Step 6: Commit the invariant boundary**

```powershell
git add src/main/java/com/li/lipicturecloud/service/SpaceUserService.java src/main/java/com/li/lipicturecloud/service/impl/SpaceUserServiceImpl.java src/main/java/com/li/lipicturecloud/controller/SpaceUserController.java src/test/java/com/li/lipicturecloud/service/impl/SpaceUserServiceImplTest.java src/test/java/com/li/lipicturecloud/controller/SpaceUserControllerTest.java
git commit -m "fix: protect team membership invariants"
git push origin main
```

---

### Task 2: Define Frontend Space and Permission Rules

**Files:**
- Modify: `li-picture-cloud-frontend/src/constants/space.js`
- Create: `li-picture-cloud-frontend/src/utils/spaceAccess.js`
- Create: `li-picture-cloud-frontend/tests/spaceAccess.test.mjs`

**Interfaces:**
- Consumes: backend fields `spaceType`, `spaceRole`, `space.id`, and permission strings.
- Produces: `SPACE_TYPE`, `SPACE_ROLE`, `spaceTypeText`, `spaceRoleText`, `normalizePermissions`, `hasPermission`, and `groupMySpaces`.

- [ ] **Step 1: Write failing pure-function tests**

Use Node's built-in test runner:

```javascript
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  groupMySpaces,
  hasPermission,
  normalizePermissions
} from '../src/utils/spaceAccess.js'

test('groups owned private, owned team, and joined team without duplicates', () => {
  const owned = [
    { id: 1, userId: 9, spaceType: 0 },
    { id: 2, userId: 9, spaceType: 1 }
  ]
  const memberships = [
    { spaceRole: 'admin', space: { id: 2, userId: 9, spaceType: 1 } },
    { spaceRole: 'editor', space: { id: 3, userId: 8, spaceType: 1 } },
    { spaceRole: 'viewer', space: { id: 3, userId: 8, spaceType: 1 } }
  ]

  const result = groupMySpaces(owned, memberships, 9)
  assert.deepEqual(result.privateSpaces.map(item => item.id), [1])
  assert.deepEqual(result.ownedTeamSpaces.map(item => item.id), [2])
  assert.deepEqual(result.joinedTeamSpaces.map(item => item.id), [3])
  assert.equal(result.joinedTeamSpaces[0].currentRole, 'editor')
})

test('permissions deny by default and accept arrays or sets', () => {
  assert.equal(hasPermission(undefined, 'picture:upload'), false)
  assert.equal(hasPermission(['picture:view'], 'picture:upload'), false)
  assert.equal(hasPermission(new Set(['picture:upload']), 'picture:upload'), true)
  assert.deepEqual(normalizePermissions(['picture:view', 'picture:view']), ['picture:view'])
})
```

Add assertions for `SPACE_TYPE.PRIVATE === 0`, `SPACE_TYPE.TEAM === 1`, and the three role labels.

- [ ] **Step 2: Run the frontend test and verify RED**

Run from `li-picture-cloud-frontend`:

```powershell
npm test -- --test-name-pattern="groups owned|permissions deny|space metadata"
```

Expected: failure with module-not-found or missing-export errors.

- [ ] **Step 3: Implement constants and pure helpers**

Add exact constants:

```javascript
export const SPACE_TYPE = Object.freeze({ PRIVATE: 0, TEAM: 1 })
export const SPACE_ROLE = Object.freeze({ VIEWER: 'viewer', EDITOR: 'editor', ADMIN: 'admin' })

export const SPACE_ROLE_MAP = Object.freeze({
  viewer: { text: '查看者' },
  editor: { text: '编辑者' },
  admin: { text: '管理员' }
})
```

Implement `groupMySpaces(ownedSpaces = [], memberships = [], currentUserId)` by filtering owned records by `spaceType`, attaching `currentRole: 'admin'` to owned team records, and de-duplicating joined membership spaces by stringified space ID. Exclude any joined record whose `space.userId` equals `currentUserId` or whose ID is already owned.

Implement permissions defensively:

```javascript
export function normalizePermissions(permissions) {
  return [...new Set(permissions instanceof Set ? permissions : (Array.isArray(permissions) ? permissions : []))]
}

export function hasPermission(permissions, permission) {
  return normalizePermissions(permissions).includes(permission)
}
```

- [ ] **Step 4: Run unit tests and lint**

```powershell
npm test
npm run lint
```

Expected: all tests pass and ESLint reports zero warnings.

- [ ] **Step 5: Commit the frontend domain helpers**

```powershell
git add li-picture-cloud-frontend/src/constants/space.js li-picture-cloud-frontend/src/utils/spaceAccess.js li-picture-cloud-frontend/tests/spaceAccess.test.mjs
git commit -m "feat: model team spaces in frontend"
git push origin main
```

---

### Task 3: Expose Membership APIs and Create Team Spaces

**Files:**
- Modify: `li-picture-cloud-frontend/src/api/spaceUser.js`
- Modify: `li-picture-cloud-frontend/src/views/SpaceCreateView.vue`
- Modify: `li-picture-cloud-frontend/tests/spaceAccess.test.mjs`

**Interfaces:**
- Consumes: Axios request adapter and constants from Task 2.
- Produces: `addSpaceUser`, `deleteSpaceUser`, `editSpaceUser`, `listSpaceUsers`, `listMyTeamSpaces`, `getMySpacePermissions`, and an explicit `spaceType` creation payload.

- [ ] **Step 1: Add a failing source-contract test for the creation payload builder**

Extract and test a pure payload helper in `spaceAccess.js`:

```javascript
test('builds an explicit space creation payload', () => {
  assert.deepEqual(buildSpaceCreatePayload({
    spaceName: '设计组',
    spaceLevel: 0,
    spaceType: 1
  }), { spaceName: '设计组', spaceLevel: 0, spaceType: 1 })
})
```

The helper trims the name and omits it only when blank; it never omits `spaceType`.

- [ ] **Step 2: Run the focused test and verify RED**

```powershell
npm test -- --test-name-pattern="explicit space creation"
```

Expected: failure because `buildSpaceCreatePayload` is not exported.

- [ ] **Step 3: Add the API wrappers**

Implement in `api/spaceUser.js`:

```javascript
export const addSpaceUser = (data) => request.post('/spaceUser/add', data)
export const deleteSpaceUser = (id) => request.post('/spaceUser/delete', { id })
export const editSpaceUser = (data) => request.post('/spaceUser/edit', data)
export const listSpaceUsers = (spaceId) => request.post('/spaceUser/list', { spaceId })
export const listMyTeamSpaces = () => request.post('/spaceUser/list/my')
export const getMySpacePermissions = (spaceId) =>
  request.post('/spaceUser/permissions', null, { params: { spaceId } })
```

Keep `getMyPicturePermissions` unchanged.

- [ ] **Step 4: Add the space-type selector and explicit submission**

Initialize:

```javascript
const form = reactive({
  spaceName: '',
  spaceLevel: SPACE_LEVEL.COMMON,
  spaceType: SPACE_TYPE.PRIVATE
})
```

Render two keyboard-accessible buttons before level selection. Each button includes its name and short explanation. Set `aria-pressed` from the selected state. Change the heading to `创建空间`, and submit `buildSpaceCreatePayload(form)`.

After a successful create, route to `/space/${newId}`. On error, keep the selected type and form values intact so the user can correct or navigate away.

- [ ] **Step 5: Run frontend verification**

```powershell
npm test
npm run lint
npm run build
```

Expected: all tests pass, lint has zero warnings, and Vite completes a production build.

- [ ] **Step 6: Commit creation and API support**

```powershell
git add li-picture-cloud-frontend/src/api/spaceUser.js li-picture-cloud-frontend/src/views/SpaceCreateView.vue li-picture-cloud-frontend/src/utils/spaceAccess.js li-picture-cloud-frontend/tests/spaceAccess.test.mjs
git commit -m "feat: create private and team spaces"
git push origin main
```

---

### Task 4: Present Owned and Joined Spaces Clearly

**Files:**
- Create: `li-picture-cloud-frontend/src/components/space/SpaceCard.vue`
- Modify: `li-picture-cloud-frontend/src/views/MySpaceView.vue`
- Modify: `li-picture-cloud-frontend/src/views/SpaceManageView.vue`

**Interfaces:**
- Consumes: `groupMySpaces`, `spaceTypeText`, `spaceRoleText`, `listSpaceVOByPage`, and `listMyTeamSpaces`.
- Produces: three independent space sections and a reusable card emitting `open` with the selected space ID.

- [ ] **Step 1: Build a focused card component**

`SpaceCard.vue` accepts:

```javascript
const props = defineProps({
  space: { type: Object, required: true },
  role: { type: String, default: '' }
})
const emit = defineEmits(['open'])
```

It renders name, type badge, optional role badge, level, picture count, and formatted capacity. The root is a `<button type="button">` or keyboard-accessible card and emits `open` with `space.id`.

- [ ] **Step 2: Replace the single-space assumption in MySpaceView**

Load both sources with `Promise.allSettled`:

```javascript
const [ownedResult, joinedResult] = await Promise.allSettled([
  listSpaceVOByPage({ current: 1, pageSize: 20, userId: userStore.currentUser.id }),
  listMyTeamSpaces()
])
```

Store separate error strings. Pass successful values to `groupMySpaces`, then render:

- `我的私有空间`
- `我创建的团队空间`
- `我加入的团队空间`

Each section has its own empty state and retry action. Preserve the AI assistant only under the private-space section so team membership does not duplicate an unrelated assistant panel.

- [ ] **Step 3: Add type visibility to SpaceManageView**

Keep existing platform/owner management semantics. Add type badges using `spaceTypeText`, and ensure navigation to a team space uses the same `/space/:id` route. Do not duplicate the joined-team list here; `/space/my` is the user workspace, while `/spaces` remains the management/index view.

- [ ] **Step 4: Run frontend test, lint, and build**

```powershell
npm test
npm run lint
npm run build
```

Expected: all commands exit with code 0; the build contains both space views.

- [ ] **Step 5: Commit the space workspace**

```powershell
git add li-picture-cloud-frontend/src/components/space/SpaceCard.vue li-picture-cloud-frontend/src/views/MySpaceView.vue li-picture-cloud-frontend/src/views/SpaceManageView.vue
git commit -m "feat: show owned and joined team spaces"
git push origin main
```

---

### Task 5: Add Permission-Driven Member Administration

**Files:**
- Create: `li-picture-cloud-frontend/src/components/space/SpaceMemberPanel.vue`
- Modify: `li-picture-cloud-frontend/src/views/SpaceDetailView.vue`
- Modify: `li-picture-cloud-frontend/src/views/PictureDetailView.vue`

**Interfaces:**
- Consumes: Task 3 membership APIs, `SPACE_ROLE`, `hasPermission`, `space.userId`, and the current user ID.
- Produces: `SpaceMemberPanel` with props `spaceId`, `creatorId`, `currentUserId`, and `canManage`; permission-derived picture actions.

- [ ] **Step 1: Implement SpaceMemberPanel state and contract**

Use these props and event:

```javascript
const props = defineProps({
  spaceId: { type: [Number, String], required: true },
  creatorId: { type: [Number, String], required: true },
  currentUserId: { type: [Number, String], required: true },
  canManage: { type: Boolean, required: true }
})
const emit = defineEmits(['close'])
```

On mount, call `listSpaceUsers(props.spaceId)` only when `canManage` is true. The add form contains numeric `userId` and a select with `viewer`, `editor`, and `admin`. Trim and validate the ID before calling `addSpaceUser`.

For each row:

- mark `String(member.userId) === String(creatorId)` as `创建者`;
- disable role changes and removal for the creator;
- disable removal for `currentUserId` with explanatory text;
- call `editSpaceUser({ id: member.id, spaceRole })` after role confirmation;
- call `deleteSpaceUser(member.id)` after `window.confirm`;
- refresh the list only after success;
- retain existing members and show `error.message || '操作失败'` after failure.

- [ ] **Step 2: Replace owner guesses with server permissions in SpaceDetailView**

Add state:

```javascript
const permissions = ref([])
const permissionLoading = ref(true)
const permissionError = ref('')
const canView = computed(() => hasPermission(permissions.value, 'picture:view'))
const canUpload = computed(() => hasPermission(permissions.value, 'picture:upload'))
const canEdit = computed(() => hasPermission(permissions.value, 'picture:edit'))
const canDelete = computed(() => hasPermission(permissions.value, 'picture:delete'))
const canManageMembers = computed(() => hasPermission(permissions.value, 'spaceUser:manage'))
```

After loading the space, call `getMySpacePermissions(space.id)`. Keep all action buttons hidden or disabled while `permissionLoading` is true. Replace existing `isOwner` checks for upload, batch actions, picture actions, and empty-state copy with the corresponding permission computed values.

If `canView` is false after permissions load, show `你不是该团队成员或暂无访问权限` and do not request/render the space picture list. Private-space owners continue to receive permissions from the backend owner override.

For team spaces, show a type badge and current role. Obtain the current role from `listMyTeamSpaces()` by matching `spaceId`; treat the owner as `admin` when the membership list is temporarily unavailable.

Mount `SpaceMemberPanel` only when the panel is open and `canManageMembers` is true.

- [ ] **Step 3: Keep picture collaboration permission-driven**

In `PictureDetailView.vue`, retain `collaboration:edit` as the exact collaborative-edit permission. Show the collaborative label only when the picture belongs to a team space and `canCollaborate` is true. Continue passing the same picture ID, space ID, initial state, and permission set to the existing `ImageEditModal`; do not change WebSocket message formats.

- [ ] **Step 4: Exercise role states in a local browser review**

With mocked or real responses, verify:

- viewer: pictures visible, mutation/member buttons absent;
- editor: upload/edit/delete/collaboration visible, member button absent;
- admin: all editor actions and member management visible;
- permission request pending or failed: no privileged button flashes.

Record any environment-only blocker in `docs/round-15-team-space-guide.md` rather than weakening permission checks.

- [ ] **Step 5: Run the frontend quality gate**

```powershell
npm test
npm run lint
npm run build
npm run check:bundle
npm audit --audit-level=high
```

Expected: tests, lint, build, and bundle check pass; audit reports zero high or critical vulnerabilities.

- [ ] **Step 6: Commit member management and permission-driven UI**

```powershell
git add li-picture-cloud-frontend/src/components/space/SpaceMemberPanel.vue li-picture-cloud-frontend/src/views/SpaceDetailView.vue li-picture-cloud-frontend/src/views/PictureDetailView.vue
git commit -m "feat: manage team members by permission"
git push origin main
```

---

### Task 6: Add Controller Delegation Coverage and Full Regression Tests

**Files:**
- Modify: `src/test/java/com/li/lipicturecloud/controller/SpaceUserControllerTest.java`
- Modify: `src/test/java/com/li/lipicturecloud/service/impl/SpaceUserServiceImplTest.java`

**Interfaces:**
- Consumes: guarded service methods from Task 1 and existing authorization interceptor tests.
- Produces: regression evidence that HTTP mutations cannot bypass membership invariants.

- [ ] **Step 1: Add controller delegation tests**

Inject mocks for `SpaceUserService` and `UserService` in `setUp`. Add tests that call controller methods directly or use MockMvc with the permission aspect separately covered:

```java
@Test
void deleteDelegatesToGuardedService() {
    DeleteRequest request = new DeleteRequest();
    request.setId(3L);
    when(spaceUserService.deleteSpaceUser(3L)).thenReturn(true);

    BaseResponse<Boolean> response = controller.deleteSpaceUser(request, httpRequest);

    assertTrue(response.getData());
    verify(spaceUserService).deleteSpaceUser(3L);
    verify(spaceUserService, never()).removeById(anyLong());
}
```

Add the equivalent edit delegation test and retain permission endpoint tests.

- [ ] **Step 2: Run backend tests and package**

```powershell
.\mvnw.cmd -Dspring.profiles.active=test test
.\mvnw.cmd -DskipTests package
```

Expected: the complete test suite has zero failures/errors and the package command creates the application JAR.

- [ ] **Step 3: Run Redis collaboration regression tests**

Start the configured local Redis instance or use the existing CI-compatible command, then run:

```powershell
.\mvnw.cmd -Dtest=RedisCollaborationStateStoreTest,RedisCollaborationEventBusTest test
```

Expected: three Redis-backed tests execute rather than skip and all pass. If local authentication differs, pass the developer's Redis password only as an environment variable; never write it into tracked files.

- [ ] **Step 4: Commit any test-only corrections**

```powershell
git add src/test/java/com/li/lipicturecloud/controller/SpaceUserControllerTest.java src/test/java/com/li/lipicturecloud/service/impl/SpaceUserServiceImplTest.java
git commit -m "test: cover team membership endpoints"
git push origin main
```

Skip this commit when Task 1 already contains the final tests and this task produces no file changes.

---

### Task 7: Write the Beginner Guide and Perform Final Verification

**Files:**
- Create: `docs/round-15-team-space-guide.md`
- Modify only if verification exposes a defect: files already named in Tasks 1-6.

**Interfaces:**
- Consumes: the completed UI, API contracts, backend rules, and existing Redis collaboration guide.
- Produces: reproducible setup and two-account acceptance instructions.

- [ ] **Step 1: Write the beginner guide**

Explain in plain Chinese:

1. What private and team spaces are.
2. Viewer/editor/admin permissions in a table.
3. How to find a user's numeric ID.
4. How account A creates a team and adds account B as editor.
5. How both accounts open the same team picture and verify left rotation, right rotation, zoom in, zoom out, and live operation notices.
6. How changing B to viewer removes editing controls after refresh.
7. Expected messages for duplicate member, missing user, creator protection, MySQL authentication, Redis authentication, and WebSocket connection failure.
8. Commands for backend tests and all frontend quality checks.
9. A note that full visual redesign is intentionally deferred to Round 17.

- [ ] **Step 2: Run the complete verification matrix from a clean process state**

Backend:

```powershell
.\mvnw.cmd -Dspring.profiles.active=test test
.\mvnw.cmd -DskipTests package
```

Frontend:

```powershell
Set-Location li-picture-cloud-frontend
npm ci
npm test
npm run lint
npm run build
npm run check:bundle
npm audit --audit-level=high
Set-Location ..
```

Expected: every command exits 0, except that `npm audit` may exit nonzero only if it reports no high/critical issue but lower severities are configured to affect the installed npm version; record exact output instead of claiming success.

- [ ] **Step 3: Check repository hygiene**

```powershell
git diff --check
git status --short
git diff -- src/main/resources/application.yaml
```

Expected: no whitespace errors; only the developer's known local `application.yaml` change may remain unstaged. Confirm no password or machine-specific path appears in staged content with:

```powershell
git diff --cached | Select-String -Pattern '123456|REDIS_PASSWORD=|MYSQL_PASSWORD=|C:\\Users\\'
```

Expected: no matches.

- [ ] **Step 4: Commit documentation and any verified correction**

```powershell
git add docs/round-15-team-space-guide.md
git commit -m "docs: explain team space workflow"
git push origin main
```

- [ ] **Step 5: Record final evidence**

Report:

- commit hashes pushed in this round;
- backend test count and Redis test count;
- frontend test, lint, build, bundle, and audit results;
- the exact remaining unstaged local configuration file;
- any unresolved environment-only issue and its guide section;
- confirmation that Round 17 owns the global frontend redesign.

