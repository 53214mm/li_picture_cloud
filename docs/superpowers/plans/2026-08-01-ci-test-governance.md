# CI and Test Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make backend and frontend builds reproducible in CI without local credentials or external infrastructure.

**Architecture:** A dedicated Spring `test` profile supplies in-memory infrastructure and disables outbound MCP initialization through conditional bean configuration. A GitHub Actions workflow runs independent backend and frontend build jobs using the repository's pinned toolchains.

**Tech Stack:** Java 21, Spring Boot 3.5, Maven Wrapper, H2, JUnit 5, Node.js 22, npm 10, Vue 3, Vite 5, GitHub Actions

## Global Constraints

- Do not modify, stage, or commit the three untracked gallery-analysis files.
- Tests must not require MySQL, Redis, business credentials, or internet access.
- Production and local-development MCP behavior remains enabled by default.
- Backend validation uses Maven Wrapper; frontend validation uses `npm ci` followed by `npm run build`.

---

### Task 1: Isolate the Spring context test

**Files:**
- Modify: `pom.xml`
- Modify: `src/test/java/com/li/lipicturecloud/LiPictureCloudApplicationTests.java`
- Create: `src/test/resources/application-test.yaml`
- Modify: `src/main/java/com/li/lipicturecloud/AI/config/RefreshableMcpToolProvider.java`
- Modify dependent AI configuration only where required for conditional bean startup

**Interfaces:**
- Consumes: Spring profile and conditional bean configuration.
- Produces: `test` profile that starts the application context without external services.

- [ ] **Step 1: Add a test that asserts the active profile is `test`**

Inject `Environment` into `LiPictureCloudApplicationTests` and assert that `test` is active. Keep the existing context-load assertion.

- [ ] **Step 2: Run the test without local secrets and verify it fails or performs an external connection**

Run: `./mvnw -B -Dspring.profiles.active=test test`

Expected before implementation: startup fails because test infrastructure or conditional AI beans are not yet configured.

- [ ] **Step 3: Add minimal test infrastructure and MCP switch**

Add H2 as a test dependency. Create `application-test.yaml` with an H2 datasource in MySQL mode, `spring.session.store-type: none`, non-secret placeholder AI values, and `app.mcp.enabled: false`. Add `@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)` to the outbound MCP provider and make its consumers conditional on the same property if required by context startup.

- [ ] **Step 4: Run isolated backend tests**

Run: `./mvnw -B -Dspring.profiles.active=test test`

Expected: tests pass and logs contain no MCP server handshake.

- [ ] **Step 5: Commit**

Commit message: `test: isolate Spring context from external services`

### Task 2: Add continuous integration

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: Maven Wrapper, Java 21, frontend lock file, Node.js 22.
- Produces: independent `backend` and `frontend` CI jobs.

- [ ] **Step 1: Create the CI workflow**

Configure triggers for pushes to `main` and pull requests. Give the workflow read-only contents permission. Backend runs `./mvnw -B -Dspring.profiles.active=test package`; frontend runs `npm ci` and `npm run build` from `li-picture-cloud-frontend`.

- [ ] **Step 2: Validate workflow syntax and commands locally**

Parse `.github/workflows/ci.yml` as YAML and run both build command sequences locally.

Expected: valid YAML, backend package passes without external MCP, frontend production build passes.

- [ ] **Step 3: Commit**

Commit message: `ci: verify backend and frontend builds`

### Task 3: Document the verification contract

**Files:**
- Modify: `doc/开发环境配置.md`

**Interfaces:**
- Consumes: commands established by Tasks 1 and 2.
- Produces: documented local pre-push verification contract.

- [ ] **Step 1: Add CI-equivalent verification commands**

Document Java 21, Node 22, backend test-profile package command, frontend clean install/build commands, and the rule that tests require no business secrets.

- [ ] **Step 2: Run final verification**

Run backend package and frontend clean build exactly as documented. Run `git diff --check` and confirm gallery-analysis paths are absent from the staged changes.

Expected: all commands pass with no whitespace errors or gallery-analysis files staged.

- [ ] **Step 3: Commit**

Commit message: `docs: define local verification workflow`
