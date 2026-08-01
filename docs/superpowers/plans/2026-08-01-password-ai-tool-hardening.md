# Password and AI Tool Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace fixed-salt MD5 passwords with BCrypt-only authentication and remove general server-operation AI tools while preserving MCP image generation and deterministic saving to a user's private space.

**Architecture:** A small `PasswordHashService` owns BCrypt encoding and verification so authentication code never knows algorithm details. AI image saving moves behind `AiPictureSaveService`; both the local save tool and MCP result handler call it, and it selects only an owned private space. The local tool registry will contain business tools only, while MCP callbacks remain independently injected by `PicCloudApp`.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Security Crypto BCrypt, MyBatis-Plus, Spring AI MCP, JUnit 5, AssertJ, Mockito, Maven.

## Global Constraints

- BCrypt strength is exactly 12.
- Existing 32-character MD5 hashes must be rejected; there is no compatibility login or automatic migration.
- `TerminalOperationTool`, `FileOperationTool`, and `ResourceDownloadTool` must be deleted and absent from the model tool list.
- MCP generation tools must remain available through `RefreshableMcpToolProvider`.
- AI-generated images may only be saved to a space owned by the user with `spaceType = PRIVATE`.
- The local `src/main/resources/application.yaml` modification must never be staged or committed.
- Every behavior change follows RED → GREEN and every implementation commit is pushed to `origin/main` after verification.

---

### Task 1: BCrypt-only password boundary

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/li/lipicturecloud/service/PasswordHashService.java`
- Modify: `src/main/java/com/li/lipicturecloud/service/impl/UserServiceImpl.java`
- Modify: `src/main/java/com/li/lipicturecloud/constant/UserConstant.java`
- Create: `src/test/java/com/li/lipicturecloud/service/PasswordHashServiceTest.java`

**Interfaces:**
- Produces: `String PasswordHashService.encode(String rawPassword)`.
- Produces: `boolean PasswordHashService.matches(String rawPassword, String encodedPassword)`; malformed, blank, and legacy MD5 values return `false` without leaking the format to callers.
- Consumes: `UserServiceImpl` uses the service in registration, login, and administrator-created-user flows.

- [ ] **Step 1: Write the failing password tests**

Create tests asserting that `encode("correct-password")` starts with a BCrypt `$2` prefix, differs from the plaintext and can be matched; a wrong password, blank hash, and `DigestUtil.md5Hex(...)` cannot match.

```java
class PasswordHashServiceTest {
    private final PasswordHashService service = new PasswordHashService();

    @Test
    void encodesAndMatchesWithBcrypt() {
        String encoded = service.encode("correct-password");
        assertThat(encoded).startsWith("$2").isNotEqualTo("correct-password");
        assertThat(service.matches("correct-password", encoded)).isTrue();
        assertThat(service.matches("wrong-password", encoded)).isFalse();
    }

    @Test
    void rejectsLegacyAndMalformedHashes() {
        assertThat(service.matches("correct-password",
                DigestUtil.md5Hex("correct-password" + "liPictureCloud2026"))).isFalse();
        assertThat(service.matches("correct-password", "")).isFalse();
    }
}
```

- [ ] **Step 2: Run RED**

Run:

```powershell
$env:JAVA_HOME='G:\JDK\Java\jdk-21'
.\mvnw.cmd -Dtest=PasswordHashServiceTest test
```

Expected: compilation failure because `PasswordHashService` does not exist.

- [ ] **Step 3: Implement the BCrypt boundary**

Add `spring-security-crypto` to `pom.xml`. Implement a Spring `@Service` with a private `BCryptPasswordEncoder(12)`. `matches` first requires an encoded value matching `^\$2[ayb]\$12\$.*`; return `false` otherwise, then call the encoder and catch `IllegalArgumentException` as `false`.

- [ ] **Step 4: Replace all password MD5 calls**

Inject `PasswordHashService` into `UserServiceImpl`. Replace registration and administrator creation with `passwordHashService.encode(rawPassword)`, and login comparison with `passwordHashService.matches(rawPassword, user.getUserPassword())`. Remove `DigestUtil`, `UserConstant.SALT`, and all MD5 comments. Keep the public error text exactly `账号或密码错误` for both missing accounts and mismatches.

- [ ] **Step 5: Run GREEN and a source guard**

Run:

```powershell
.\mvnw.cmd -Dtest=PasswordHashServiceTest test
rg -n "DigestUtil\.md5Hex|UserConstant\.SALT|String SALT" src/main/java/com/li/lipicturecloud
```

Expected: tests pass and `rg` has no matches.

- [ ] **Step 6: Commit and push**

```powershell
git add -- pom.xml src/main/java/com/li/lipicturecloud/service/PasswordHashService.java src/main/java/com/li/lipicturecloud/service/impl/UserServiceImpl.java src/main/java/com/li/lipicturecloud/constant/UserConstant.java src/test/java/com/li/lipicturecloud/service/PasswordHashServiceTest.java
git commit -m "security: replace MD5 passwords with BCrypt"
git push origin main
```

---

### Task 2: One private-space image-saving service

**Files:**
- Modify: `src/main/java/com/li/lipicturecloud/service/SpaceService.java`
- Modify: `src/main/java/com/li/lipicturecloud/service/impl/SpaceServiceImpl.java`
- Create: `src/main/java/com/li/lipicturecloud/AI/service/AiPictureSaveService.java`
- Create: `src/test/java/com/li/lipicturecloud/AI/service/AiPictureSaveServiceTest.java`

**Interfaces:**
- Produces: `Space SpaceService.getOwnedPrivateSpace(Long userId)` returning the oldest owned private space or `null`.
- Produces: `String AiPictureSaveService.save(String imageUrl, String name, User user)` returning a user-safe success or failure message.
- Consumes: the existing `PictureService.uploadPicture(String, PictureUploadRequest, User)` performs remote validation, COS upload, and persistence.

- [ ] **Step 1: Write failing save-service tests**

Use mocked `SpaceService` and `PictureService` to prove:

1. `null` user returns `无法获取用户信息，请登录后再试。` and never uploads.
2. invalid protocols return `无效的图片地址` and never query a space.
3. no private space returns `未保存：请先创建个人空间。`.
4. a private space builds `PictureUploadRequest` with that exact space ID, calls `uploadPicture`, and returns the picture ID and space name.

The success assertion must capture the request and verify `spaceId`, `fileUrl`, and the provided name.

- [ ] **Step 2: Run RED**

```powershell
.\mvnw.cmd -Dtest=AiPictureSaveServiceTest test
```

Expected: compilation failure because the service does not exist.

- [ ] **Step 3: Implement owned-private-space lookup**

Add this query in `SpaceServiceImpl`:

```java
return lambdaQuery()
        .eq(Space::getUserId, userId)
        .eq(Space::getSpaceType, SpaceTypeEnum.PRIVATE.getValue())
        .orderByAsc(Space::getCreateTime)
        .last("LIMIT 1")
        .one();
```

Reject a null/non-positive user ID by returning `null` before querying.

- [ ] **Step 4: Implement `AiPictureSaveService`**

Validate `http` or `https`, resolve `getOwnedPrivateSpace(user.getId())`, build the upload request, call `PictureService`, and return only safe messages. Log the full exception server-side without returning its details.

- [ ] **Step 5: Run GREEN**

```powershell
.\mvnw.cmd -Dtest=AiPictureSaveServiceTest test
```

Expected: all save-service tests pass.

- [ ] **Step 6: Commit and push**

```powershell
git add -- src/main/java/com/li/lipicturecloud/service/SpaceService.java src/main/java/com/li/lipicturecloud/service/impl/SpaceServiceImpl.java src/main/java/com/li/lipicturecloud/AI/service/AiPictureSaveService.java src/test/java/com/li/lipicturecloud/AI/service/AiPictureSaveServiceTest.java
git commit -m "feat: save AI images to owned private spaces"
git push origin main
```

---

### Task 3: Reuse the save service for local and MCP generation

**Files:**
- Modify: `src/main/java/com/li/lipicturecloud/AI/tools/AIGenerationTool.java`
- Create: `src/main/java/com/li/lipicturecloud/AI/service/McpGeneratedImageHandler.java`
- Modify: `src/main/java/com/li/lipicturecloud/AI/config/RefreshableMcpToolProvider.java`
- Create: `src/test/java/com/li/lipicturecloud/AI/service/McpGeneratedImageHandlerTest.java`
- Create: `src/test/java/com/li/lipicturecloud/AI/tools/AIGenerationToolTest.java`

**Interfaces:**
- Consumes: `AiPictureSaveService.save(imageUrl, name, user)` from Task 2.
- Produces: `String McpGeneratedImageHandler.appendSaveResult(String mcpText, User user)`; text without an HTTP(S) URL is unchanged, while text with an image URL is appended with the save result.

- [ ] **Step 1: Write failing handler tests**

Test that MCP text containing `https://cdn.example.com/result.png` passes exactly that URL, name `AI生成`, and the current user to `AiPictureSaveService`; test text without a URL returns unchanged and never saves.

- [ ] **Step 2: Write the failing local-tool test**

Set `UserContextHolder` to a user, invoke `AIGenerationTool.saveToMySpace`, and verify it delegates to `AiPictureSaveService` with the same URL/name/user. Always clear the context in `finally`.

- [ ] **Step 3: Run RED**

```powershell
.\mvnw.cmd -Dtest=McpGeneratedImageHandlerTest,AIGenerationToolTest test
```

Expected: handler missing and `AIGenerationTool` has no injectable shared saver.

- [ ] **Step 4: Implement shared delegation**

Make `AIGenerationTool` constructor-inject only `AiPictureSaveService`. Implement `McpGeneratedImageHandler` with the existing URL pattern and the same saver. In `RefreshableMcpToolProvider`, constructor-inject the handler, replace both generation and `get_task_status` auto-save branches with `appendSaveResult`, and delete its private duplicate `autoSaveToSpace` plus unused `PictureService`/`SpaceService` dependencies.

- [ ] **Step 5: Run GREEN**

```powershell
.\mvnw.cmd -Dtest=McpGeneratedImageHandlerTest,AIGenerationToolTest test
```

Expected: all handler and delegation tests pass.

- [ ] **Step 6: Commit and push**

```powershell
git add -- src/main/java/com/li/lipicturecloud/AI/tools/AIGenerationTool.java src/main/java/com/li/lipicturecloud/AI/service/McpGeneratedImageHandler.java src/main/java/com/li/lipicturecloud/AI/config/RefreshableMcpToolProvider.java src/test/java/com/li/lipicturecloud/AI/service/McpGeneratedImageHandlerTest.java src/test/java/com/li/lipicturecloud/AI/tools/AIGenerationToolTest.java
git commit -m "refactor: unify AI image auto-save flow"
git push origin main
```

---

### Task 4: Remove general server-operation tools

**Files:**
- Modify: `src/main/java/com/li/lipicturecloud/AI/tools/ToolRegistration.java`
- Delete: `src/main/java/com/li/lipicturecloud/AI/tools/TerminalOperationTool.java`
- Delete: `src/main/java/com/li/lipicturecloud/AI/tools/FileOperationTool.java`
- Delete: `src/main/java/com/li/lipicturecloud/AI/tools/ResourceDownloadTool.java`
- Create: `src/test/java/com/li/lipicturecloud/AI/tools/ToolRegistrationTest.java`

**Interfaces:**
- Produces: `ToolCallback[] allTools()` containing business tools only.
- Preserves: MCP callbacks are not part of this bean; `PicCloudApp` continues adding them from `RefreshableMcpToolProvider`.

- [ ] **Step 1: Write the failing registry test**

Instantiate `ToolRegistration` with real tool objects or inject its fields using `ReflectionTestUtils`. Convert callbacks to tool names and assert the list contains `saveToMySpace` and the image-management/analysis names, while it excludes `executeCommandSafe`, `readFile`, `writeFile`, and `downloadResource`.

- [ ] **Step 2: Run RED**

```powershell
.\mvnw.cmd -Dtest=ToolRegistrationTest test
```

Expected: forbidden tool names are present.

- [ ] **Step 3: Remove dangerous registrations and classes**

Delete object creation and callback entries for the three forbidden tools, then delete their source files. Do not remove `refreshableMcpToolProvider` from `PicCloudApp` or change MCP configuration.

- [ ] **Step 4: Run GREEN and source guards**

```powershell
.\mvnw.cmd -Dtest=ToolRegistrationTest test
rg -n "TerminalOperationTool|FileOperationTool|ResourceDownloadTool|executeCommandSafe" src/main/java
rg -n "refreshableMcpToolProvider|getToolCallbacks" src/main/java/com/li/lipicturecloud/AI/app/PicCloudApp.java
```

Expected: test passes; first `rg` is empty; second confirms MCP injection remains.

- [ ] **Step 5: Commit and push**

```powershell
git add -- src/main/java/com/li/lipicturecloud/AI/tools/ToolRegistration.java src/main/java/com/li/lipicturecloud/AI/tools/TerminalOperationTool.java src/main/java/com/li/lipicturecloud/AI/tools/FileOperationTool.java src/main/java/com/li/lipicturecloud/AI/tools/ResourceDownloadTool.java src/test/java/com/li/lipicturecloud/AI/tools/ToolRegistrationTest.java
git commit -m "security: remove general server AI tools"
git push origin main
```

---

### Task 5: Beginner migration and verification guide

**Files:**
- Create: `docs/round-16-password-ai-security-guide.md`

**Interfaces:**
- Documents the operator-visible consequences of Tasks 1–4.

- [ ] **Step 1: Write the guide**

Explain in beginner language:

- why old MD5 accounts intentionally fail;
- how to back up the database before changing users;
- how to recreate development users and promote the first admin with an `UPDATE user SET userRole='admin' WHERE userAccount=?` prepared statement or database client parameter entry;
- why plaintext passwords and online hash generators are forbidden;
- which AI tools were removed and which MCP/image tools remain;
- how to create a private space, request an MCP image, confirm the result appears in that private space, and verify it did not enter a team space;
- rollback means reverting the application commit, not restoring MD5 hashes.

- [ ] **Step 2: Check documentation and repository scope**

```powershell
rg -n "MD5|BCrypt|MCP|个人空间|团队空间|终端|文件" docs/round-16-password-ai-security-guide.md
git diff --check
git status --short
```

Expected: all topics are present, no whitespace errors, and `application.yaml` remains the only unrelated unstaged file.

- [ ] **Step 3: Commit and push**

```powershell
git add -- docs/round-16-password-ai-security-guide.md
git commit -m "docs: explain password and AI security migration"
git push origin main
```

---

### Task 6: Full delivery verification

**Files:**
- Verify only; change production files only if a failing test reveals a defect, and start a new RED test before that fix.

**Interfaces:**
- Proves the complete deliverable and preserves the user's local configuration.

- [ ] **Step 1: Run the complete backend suite**

```powershell
$env:JAVA_HOME='G:\JDK\Java\jdk-21'
.\mvnw.cmd -B "-Dspring.profiles.active=test" verify
```

Expected: Maven `BUILD SUCCESS`, zero failures and zero errors.

- [ ] **Step 2: Run the complete frontend quality gate**

```powershell
Set-Location li-picture-cloud-frontend
npm test
npm run lint
npm run build
npm run check:bundle
npm audit --omit=dev --audit-level=high --registry=https://registry.npmjs.org
Set-Location ..
```

Expected: all tests pass, lint has zero warnings, Vite builds, bundle budget passes, and audit reports zero high-severity production vulnerabilities.

- [ ] **Step 3: Verify security invariants and Git scope**

```powershell
rg -n "DigestUtil\.md5Hex|UserConstant\.SALT|TerminalOperationTool|FileOperationTool|ResourceDownloadTool" src/main/java
git diff --check
git status --short
git fetch origin main
git rev-parse HEAD
git rev-parse origin/main
```

Expected: security search is empty; no whitespace errors; only the user's `application.yaml` is modified; local and remote hashes match.

- [ ] **Step 4: Report operational action**

Tell the user that old accounts require recreation/reset before login, the backend must be restarted, MCP credentials remain required, and the private-space verification procedure is in the round-16 guide.
