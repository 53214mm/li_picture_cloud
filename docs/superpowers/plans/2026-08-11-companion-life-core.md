# Companion Life Core Vertical Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a working first vertical slice in which one authenticated subject awakens one companion, explicitly feeds one currently authorized picture through a deterministic demo nutrition Adapter, and receives durable life experience, trait, skill, and explainable growth feedback on a companion page.

**Architecture:** Keep the companion life core framework-free and deterministic. The HTTP/application layer owns the trusted subject, permission recheck, idempotent feeding workflow, transaction boundaries, and safe errors; replaceable nutrition and MyBatis/Liquibase Adapters stay behind small Interfaces. A completed feeding run, optimistic companion snapshot update, skill snapshot, and append-only growth record commit atomically, while denied and failed runs remain auditable without becoming growth history.

**Tech Stack:** Java 21, Spring Boot 3.5.14, MyBatis-Plus 3.5.9, Liquibase, MySQL 8 / H2 MySQL mode, JUnit 5, AssertJ, Mockito, Vue 3.4, Pinia 2.1, Vue Router 4.3, Axios 1.7, Node 22, Vite 7, Playwright 1.62.1, GitHub Actions.

## Global Constraints

- This plan implements only implementation slice 1, **伙伴生命核心纵向切片**. Real visual understanding and memory, proactive proposals, capability/MCP migration, model configuration, DeepSeek, GPT Image 2, BYOK, quotas, payments, stories, stickers, image fusion, desktop pets, and extreme-state plots are outside this plan.
- Use the established domain words **主体、伙伴、喂养、成长记录、生命经验、等级、生命阶段、性格轴、技能熟练度、图片、空间、权限、授权资源**. Do not rename feeding as upload, training, or file consumption.
- Each subject has at most one companion. Enforce this with a database unique constraint and race-safe create-or-load behavior; do not rely on a pre-insert count.
- The client never submits a user ID, claimed space ID, nutrition result, XP, trait value, skill value, balance version, or companion revision. The server derives the subject from the authenticated session and resolves the picture as an authorized resource.
- Every feeding attempt rechecks `picture:view` through `SpaceAuthorizationAccessService.checkForUser(String permission, Long pictureId, Long userId)` before the nutrition Adapter sees the picture. Missing and unauthorized pictures must return the same safe message: `图片不可用或无权访问`.
- Feeding treats a picture only as an authorized source reference. It never edits, deletes, relabels, moves, or otherwise mutates the original picture, its space, or its permissions.
- The deterministic demo Adapter never reads image bytes and never claims content understanding. Every current and historical view states `未读取图片内容，也未调用视觉模型`.
- Life experience is nonnegative and monotonic. Level and life stage are derived by the versioned `life-core-v1` balance object; permission, cost, and proactive frequency are never derived from level.
- Trait axes are `好奇↔谨慎、热情↔克制、淘气↔沉稳、共情↔理性、创造↔秩序`. Stored values remain in `[-100, 100]`; one full feed changes any axis by at most `1.00`.
- Skill experience is independent of life experience. The initial skill catalog is `IMAGE_OBSERVATION`, `STORY_CREATION`, `EMOJI_CREATION`, `IMAGE_FUSION`, and `GALLERY_SEARCH`.
- Each request carries a lowercase 16-64 character idempotency key matching `[a-z0-9_-]{16,64}`, scoped to `(companionId, idempotencyKey)`. Reusing a key with a different picture is a parameter conflict. Reusing a completed key returns the original snapshot and growth record without another mutation. Lowercase canonicalization avoids MySQL case-insensitive-collation divergence from H2.
- A new key for an already-fed picture creates a `PICTURE_REVISITED` growth fact: at most `1` life experience, no trait change, no skill change, and at most `3` repeat experience for that companion-picture pair over its lifetime.
- `life-core-v1` hard caps are: `60` life experience per full feed, `300` life experience per Asia/Shanghai calendar day, `1.00` absolute trait shift per feed, `25` skill experience per skill per feed, and an `80.00` soft absolute trait limit. The legal domain range stays `[-100, 100]`; the soft limit keeps newly awakened companions from reaching extreme-state events in this slice without snapping restored legal values.
- Companion snapshot, skill rows, completed feeding run, and growth record commit in one database transaction. Growth records are append-only. A failed Adapter call changes neither companion state nor growth history and leaves a retryable failed feeding run.
- Every feeding run has a UUID correlation ID. Persist only IDs, numeric deltas, safe reasons, safe error codes, and timestamps; do not persist image content, image descriptions, credentials, prompts, or third-party exception bodies.
- `app.companion.feeding-enabled=false` is a global stop switch. `application-prod.yaml` keeps the backend demo feature disabled unless `COMPANION_ENABLED=true` is explicitly supplied, and production frontend builds omit the route/navigation unless `VITE_COMPANION_ENABLED=true`; a release must deliberately enable both sides.
- All new tables are introduced by Liquibase with an explicit rollback block. Under sharding profiles Liquibase connects directly to the physical MySQL datasource, while ShardingSphere routes companion tables through an explicit `!SINGLE` rule.
- Backend `Long` values are serialized as JSON strings by the existing `JacksonConfig`; the frontend must keep all user, space, picture, companion, revision, and growth IDs as strings.
- Companion-domain branch coverage must be at least `85%`. A real Chromium test must cover login/session bootstrap, awaken, authorized private-picture feeding, ambiguous-response retry with the same idempotency key, persisted growth, and reload. Source-string assertions do not replace this browser flow.
- This demo slice is local/test software and must not be presented as production visual AI. Existing production CORS, secret-store, real Provider, and broader release gates remain prerequisites for a public Provider-backed release.

---

## Scope Decisions Locked by This Plan

| Decision | Exact contract |
| --- | --- |
| Empty state | `GET /companion/me` returns HTTP success with `companion: null`; it never auto-awakens. |
| Awakening | `POST /companion/awaken` is idempotent and returns the same companion on repeated/concurrent calls. |
| Feeding permission | Existing `SpaceUserPermissionConstant.PICTURE_VIEW` is the required permission for public, private-space, or team-space pictures. The first page picker lists the subject's oldest private space, while the backend accepts any picture currently authorized by this rule. |
| Demo analysis | `DEMO_DETERMINISTIC`; profile selection is `Math.floorMod(pictureId, 3)`. No bytes, URL, metadata, or model are read. |
| Idempotency retention | Feeding runs remain for the lifetime of the companion. `FAILED` runs can retry with the same key; `REJECTED` and `COMPLETED` runs retain their original outcome. A `PROCESSING` run can be reclaimed after five minutes. |
| Repeat picture | Every new key is a new interaction, but only the first feed receives full nutrition. Revisit growth is capped at 1 XP per interaction and 3 XP lifetime per companion-picture. |
| Daily boundary | Asia/Shanghai local midnight, calculated with an injected `Clock`; the persisted event time remains UTC `Instant`. |
| Extreme traits | A newly awakened `life-core-v1` companion cannot reach an extreme through feeding because the balance applies a soft `±80.00` limit. A restored legal value outside that soft range is never snapped inward: each feed may move it inward by at most `1.00` and may not move it farther outward. The domain legal clamp remains `±100.00`, preserving the later event seam. |
| History privacy | Store and show only the source picture ID. Do not copy thumbnail, URL, name, tags, description, or inferred features into immutable growth history. |
| Sharding | `picture` remains sharded. `user`, `space`, `space_user`, and the four companion tables are explicit single tables on `primary`. |

## Stable HTTP Contract

All endpoints use the existing `/api` servlet context and `{ code, message, data }` response envelope.

```text
GET  /companion/me
POST /companion/awaken
POST /companion/feed
```

The only feed request body is:

```json
{
  "pictureId": "102",
  "idempotencyKey": "6f26d166-0a82-4d9f-8a61-6c21cf2e59d0"
}
```

`GET /companion/me` and `POST /companion/awaken` return:

```json
{
  "companion": {
    "id": "1",
    "lifeExperience": "42",
    "level": 1,
    "lifeStage": "LIGHT",
    "levelStartExperience": "0",
    "nextLevelExperience": "100",
    "traits": {
      "curiosity": 0.60,
      "enthusiasm": 0.40,
      "playfulness": 0.00,
      "empathy": 0.20,
      "creativity": 0.30
    },
    "skills": [
      { "code": "IMAGE_OBSERVATION", "experience": "18", "level": 1, "nextLevelExperience": "100" },
      { "code": "STORY_CREATION", "experience": "12", "level": 1, "nextLevelExperience": "100" },
      { "code": "EMOJI_CREATION", "experience": "0", "level": 1, "nextLevelExperience": "100" },
      { "code": "IMAGE_FUSION", "experience": "0", "level": 1, "nextLevelExperience": "100" },
      { "code": "GALLERY_SEARCH", "experience": "0", "level": 1, "nextLevelExperience": "100" }
    ],
    "balanceVersion": "life-core-v1",
    "revision": "1"
  },
  "nutrition": {
    "mode": "DEMO_DETERMINISTIC",
    "contentUnderstood": false,
    "notice": "仅根据图片 ID 选择固定营养档案，未读取图片内容，也未调用视觉模型。"
  },
  "recentGrowth": []
}
```

`POST /companion/feed` returns the original result on idempotent replay:

```json
{
  "outcome": "GROWN",
  "correlationId": "fef53056-2d9f-467d-9b1d-1afe9a6638fe",
  "companion": {
    "id": "1",
    "lifeExperience": "42",
    "level": 1,
    "lifeStage": "LIGHT",
    "levelStartExperience": "0",
    "nextLevelExperience": "100",
    "traits": {
      "curiosity": 0.60,
      "enthusiasm": 0.40,
      "playfulness": 0.00,
      "empathy": 0.20,
      "creativity": 0.30
    },
    "skills": [
      { "code": "IMAGE_OBSERVATION", "experience": "18", "level": 1, "nextLevelExperience": "100" },
      { "code": "STORY_CREATION", "experience": "12", "level": 1, "nextLevelExperience": "100" },
      { "code": "EMOJI_CREATION", "experience": "0", "level": 1, "nextLevelExperience": "100" },
      { "code": "IMAGE_FUSION", "experience": "0", "level": 1, "nextLevelExperience": "100" },
      { "code": "GALLERY_SEARCH", "experience": "0", "level": 1, "nextLevelExperience": "100" }
    ],
    "balanceVersion": "life-core-v1",
    "revision": "1"
  },
  "growth": {
    "id": "1",
    "sourcePictureId": "102",
    "eventType": "PICTURE_FED",
    "lifeExperienceDelta": "42",
    "traitDelta": {
      "curiosity": 0.60,
      "enthusiasm": 0.40,
      "playfulness": 0.00,
      "empathy": 0.20,
      "creativity": 0.30
    },
    "skillExperienceDelta": {
      "IMAGE_OBSERVATION": "18",
      "STORY_CREATION": "12"
    },
    "reason": "演示营养让伙伴练习了观察与叙事。",
    "balanceVersion": "life-core-v1",
    "nutritionMode": "DEMO_DETERMINISTIC",
    "contentUnderstood": false,
    "createdTime": "2026-08-11T08:00:00Z"
  }
}
```

## File Map

### Migration and configuration

- Modify `pom.xml`: add Liquibase and enforce companion-domain branch coverage.
- Modify `src/main/resources/application.yaml`: point to the master changelog and define companion feature/feeding switches.
- Modify `src/main/resources/application-prod.yaml`: disable the demo feature by default in production.
- Modify `src/main/resources/application-sharding-static.yaml`: give Liquibase a direct physical MySQL connection.
- Modify `src/main/resources/application-sharding-dynamic.yaml`: give Liquibase a direct physical MySQL connection.
- Modify `src/main/resources/sharding/static.yaml`: register existing and companion single tables.
- Modify `src/main/resources/sharding/dynamic.yaml`: register existing and companion single tables.
- Modify `src/test/resources/application-test.yaml`: enable Liquibase and the companion demo switches for tests.
- Create `src/main/resources/db/changelog/db.changelog-master.xml`: include versioned companion changes.
- Create `src/main/resources/db/changelog/changes/2026-08-11-companion-life-core.xml`: create and roll back the four companion tables.
- Create `scripts/migrate-companion-physical.ps1`: run the changelog against physical MySQL before ShardingSphere starts.
- Create `scripts/mvnw-java21.ps1`: make every Windows Maven invocation self-contained when `JAVA_HOME` is absent.
- Create `src/test/java/com/li/lipicturecloud/migration/CompanionSchemaMigrationTest.java`: prove the real H2 migration applies and declares rollback.
- Modify `src/test/java/com/li/lipicturecloud/sharding/ShardingModeConfigurationTest.java`: protect direct migration and `!SINGLE` routing contracts.
- Create `src/test/java/com/li/lipicturecloud/sharding/CompanionSingleTableRoutingIntegrationTest.java`: load both real rule YAMLs through ShardingSphere and exercise the migrated tables.

### Framework-free domain core

- Create `src/main/java/com/li/lipicturecloud/domain/companion/Companion.java`: immutable companion aggregate and `feed` behavior.
- Create `src/main/java/com/li/lipicturecloud/domain/companion/CompanionTraits.java`: five-axis value object and legal-range validation.
- Create `src/main/java/com/li/lipicturecloud/domain/companion/TraitDelta.java`: five-axis applied change value.
- Create `src/main/java/com/li/lipicturecloud/domain/companion/CompanionStage.java`: `LIGHT`, `SEEDLING`, `COMPANION`.
- Create `src/main/java/com/li/lipicturecloud/domain/companion/CompanionSkill.java`: the five initial skill codes.
- Create `src/main/java/com/li/lipicturecloud/domain/companion/CompanionBalance.java`: immutable `life-core-v1` curves, limits, and Asia/Shanghai day boundary.
- Create `src/main/java/com/li/lipicturecloud/domain/companion/NutritionMode.java`: demo-mode disclosure enum.
- Create `src/main/java/com/li/lipicturecloud/domain/companion/PictureNutrition.java`: untrusted Adapter observation candidate.
- Create `src/main/java/com/li/lipicturecloud/domain/companion/FeedingContext.java`: prior-picture and daily-cap facts supplied by the application layer.
- Create `src/main/java/com/li/lipicturecloud/domain/companion/FeedingGrowth.java`: deterministic applied result and after-state.
- Create `src/main/java/com/li/lipicturecloud/domain/companion/GrowthEventType.java`: `PICTURE_FED` and `PICTURE_REVISITED`.
- Create `src/main/java/com/li/lipicturecloud/domain/companion/GrowthRecord.java`: append-only, source-light growth fact with after-snapshot.
- Create `src/main/java/com/li/lipicturecloud/domain/companion/FeedingRun.java`: idempotency/audit state.
- Create `src/main/java/com/li/lipicturecloud/domain/companion/FeedingRunStatus.java`: `PROCESSING`, `COMPLETED`, `FAILED`, `REJECTED`.
- Create `src/main/java/com/li/lipicturecloud/domain/companion/CompanionRepository.java`: small aggregate persistence Interface.
- Create `src/main/java/com/li/lipicturecloud/domain/companion/GrowthRecordRepository.java`: append/history/cap facts Interface.
- Create `src/main/java/com/li/lipicturecloud/domain/companion/FeedingRunRepository.java`: reservation and state-transition Interface.
- Create `src/test/java/com/li/lipicturecloud/domain/companion/CompanionTest.java`: focused balance and feeding examples.
- Create `src/test/java/com/li/lipicturecloud/domain/companion/FeedingRunTest.java`: legal/illegal audit-state transitions and value validation.
- Create `src/test/java/com/li/lipicturecloud/domain/companion/CompanionBalancePropertyTest.java`: deterministic randomized invariant coverage.

### Persistence Adapters

- Create `src/main/java/com/li/lipicturecloud/model/entity/CompanionEntity.java`.
- Create `src/main/java/com/li/lipicturecloud/model/entity/CompanionSkillEntity.java`.
- Create `src/main/java/com/li/lipicturecloud/model/entity/CompanionFeedRunEntity.java`.
- Create `src/main/java/com/li/lipicturecloud/model/entity/CompanionGrowthRecordEntity.java`.
- Create `src/main/java/com/li/lipicturecloud/mapper/CompanionMapper.java`.
- Create `src/main/java/com/li/lipicturecloud/mapper/CompanionSkillMapper.java`.
- Create `src/main/java/com/li/lipicturecloud/mapper/CompanionFeedRunMapper.java`.
- Create `src/main/java/com/li/lipicturecloud/mapper/CompanionGrowthRecordMapper.java`.
- Create `src/main/java/com/li/lipicturecloud/infrastructure/persistence/companion/CompanionJsonCodec.java`: persistence-only JSON payload mapping.
- Create `src/main/java/com/li/lipicturecloud/infrastructure/persistence/companion/MybatisCompanionRepository.java`.
- Create `src/main/java/com/li/lipicturecloud/infrastructure/persistence/companion/MybatisGrowthRecordRepository.java`.
- Create `src/main/java/com/li/lipicturecloud/infrastructure/persistence/companion/MybatisFeedingRunRepository.java`.
- Create `src/test/java/com/li/lipicturecloud/infrastructure/persistence/companion/CompanionPersistenceIntegrationTest.java`.

### Application and HTTP

- Create `src/main/java/com/li/lipicturecloud/config/CompanionConfiguration.java`: `Clock` and `life-core-v1` beans.
- Create `src/main/java/com/li/lipicturecloud/config/CompanionFeatureProperties.java`: feature, stop-switch, and stale-run settings.
- Create `src/main/java/com/li/lipicturecloud/application/companion/PictureNutritionAnalyzer.java`: replaceable nutrition Interface.
- Create `src/main/java/com/li/lipicturecloud/application/companion/AuthorizedPictureRef.java`: trusted subject + authorized picture reference.
- Create `src/main/java/com/li/lipicturecloud/infrastructure/companion/DemoPictureNutritionAdapter.java`: deterministic fake Adapter.
- Create `src/main/java/com/li/lipicturecloud/application/companion/CompanionLife.java`: `home`, `awaken`, and `feed` application Interface.
- Create `src/main/java/com/li/lipicturecloud/application/companion/FeedPictureCommand.java`.
- Create `src/main/java/com/li/lipicturecloud/application/companion/FeedReservation.java`.
- Create `src/main/java/com/li/lipicturecloud/application/companion/CompanionFeedingCoordinator.java`: short, isolated reservation/failure/completion transactions.
- Create `src/main/java/com/li/lipicturecloud/application/companion/CompanionLifeService.java`: authorization and workflow orchestration.
- Create `src/main/java/com/li/lipicturecloud/application/companion/CompanionViewAssembler.java`.
- Create `src/main/java/com/li/lipicturecloud/application/companion/view/CompanionHomeView.java`.
- Create `src/main/java/com/li/lipicturecloud/application/companion/view/CompanionView.java`.
- Create `src/main/java/com/li/lipicturecloud/application/companion/view/CompanionTraitsView.java`.
- Create `src/main/java/com/li/lipicturecloud/application/companion/view/CompanionSkillView.java`.
- Create `src/main/java/com/li/lipicturecloud/application/companion/view/NutritionStatusView.java`.
- Create `src/main/java/com/li/lipicturecloud/application/companion/view/GrowthRecordView.java`.
- Create `src/main/java/com/li/lipicturecloud/application/companion/view/FeedPictureResult.java`.
- Create `src/test/java/com/li/lipicturecloud/application/companion/CompanionFeedingCoordinatorTest.java`.
- Create `src/test/java/com/li/lipicturecloud/application/companion/CompanionFeedingIntegrationTest.java`: H2 concurrency/transaction proof.
- Create `src/test/java/com/li/lipicturecloud/application/companion/CompanionLifeServiceTest.java`.
- Create `src/test/java/com/li/lipicturecloud/infrastructure/companion/DemoPictureNutritionAdapterTest.java`: deterministic fake-profile and disclosure proof.
- Create `src/main/java/com/li/lipicturecloud/model/dto/companion/CompanionFeedRequest.java`.
- Create `src/main/java/com/li/lipicturecloud/controller/CompanionController.java`.
- Create `src/test/java/com/li/lipicturecloud/controller/CompanionControllerTest.java`.

### Frontend and browser acceptance

- Create `li-picture-cloud-frontend/src/api/companion.js`.
- Modify `li-picture-cloud-frontend/src/api/request.js`: preserve HTTP/envelope error metadata for safe same-key retry decisions.
- Modify `li-picture-cloud-frontend/src/api/picture.js`: expose the existing uncached, permission-checked picture page endpoint.
- Create `li-picture-cloud-frontend/src/config/features.js`: keep the companion UI off by default in production builds unless explicitly enabled.
- Create `li-picture-cloud-frontend/src/constants/companion.js`.
- Create `li-picture-cloud-frontend/src/utils/authBootstrap.js`: testable single-flight authentication loading and terminal-error classification.
- Create `li-picture-cloud-frontend/src/utils/companion.js`.
- Create `li-picture-cloud-frontend/tests/companion.test.mjs`.
- Modify `li-picture-cloud-frontend/src/stores/user.js`: add single-flight authentication readiness.
- Modify `li-picture-cloud-frontend/src/App.vue`: call the readiness action rather than launching duplicate fetches.
- Modify `li-picture-cloud-frontend/src/router/index.js`: add guarded `/companion` route.
- Modify `li-picture-cloud-frontend/src/constants/navigation.js`: add `我的伙伴` for authenticated users.
- Modify `li-picture-cloud-frontend/src/components/NavBar.vue`: pass the production-safe companion UI flag into the navigation model.
- Modify `li-picture-cloud-frontend/tests/navigation.test.mjs`.
- Modify `li-picture-cloud-frontend/tests/responsiveViews.test.mjs`.
- Create `li-picture-cloud-frontend/src/components/companion/CompanionStats.vue`.
- Create `li-picture-cloud-frontend/src/components/companion/CompanionPicturePicker.vue`.
- Create `li-picture-cloud-frontend/src/components/companion/CompanionGrowthTimeline.vue`.
- Create `li-picture-cloud-frontend/src/views/CompanionView.vue`.
- Create `src/test/resources/application-e2e.yaml`.
- Create `src/test/resources/e2e-schema.sql`.
- Create `src/test/resources/e2e-data.sql`.
- Create `li-picture-cloud-frontend/scripts/start-e2e-backend.mjs`.
- Create `li-picture-cloud-frontend/playwright.config.js`.
- Create `li-picture-cloud-frontend/e2e/companion.spec.js`.
- Modify `li-picture-cloud-frontend/vite.config.js`: allow the isolated E2E proxy target without changing the normal local default.
- Modify `li-picture-cloud-frontend/package.json` and `li-picture-cloud-frontend/package-lock.json`.
- Modify `li-picture-cloud-frontend/Dockerfile`: accept the production companion build flag.
- Modify `li-picture-cloud-frontend/eslint.config.js` and `.gitignore`.
- Modify `.github/workflows/ci.yml`.
- Modify `compose.yaml` and `.env.example`: pass backend and frontend companion flags through the existing deployment path.
- Modify `src/test/java/com/li/lipicturecloud/deployment/DeploymentArtifactsTest.java`: protect those build/runtime contracts.
- Create `docs/round-19-companion-life-core-guide.md`.
- Modify `README.md`: link the guide and identify the deterministic demo boundary.

---
### Task 1: Introduce Reversible Companion Schema and Single-Table Routing

**Files:**
- Modify: `docs/superpowers/plans/2026-08-11-companion-life-core.md` (track this plan with the first implementation commit)
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yaml`
- Modify: `src/main/resources/application-prod.yaml`
- Modify: `src/main/resources/application-sharding-static.yaml`
- Modify: `src/main/resources/application-sharding-dynamic.yaml`
- Modify: `src/main/resources/sharding/static.yaml`
- Modify: `src/main/resources/sharding/dynamic.yaml`
- Modify: `src/test/resources/application-test.yaml`
- Create: `src/main/resources/db/changelog/db.changelog-master.xml`
- Create: `src/main/resources/db/changelog/changes/2026-08-11-companion-life-core.xml`
- Create: `scripts/migrate-companion-physical.ps1`
- Create: `scripts/mvnw-java21.ps1`
- Create: `src/test/java/com/li/lipicturecloud/migration/CompanionSchemaMigrationTest.java`
- Modify: `src/test/java/com/li/lipicturecloud/sharding/ShardingModeConfigurationTest.java`
- Create: `src/test/java/com/li/lipicturecloud/sharding/CompanionSingleTableRoutingIntegrationTest.java`

**Interfaces:**
- Consumes: the existing direct MySQL/H2 `DataSource`, the two ShardingSphere `primary` storage units, and Spring Boot Liquibase configuration.
- Produces: tables `companion`, `companion_skill`, `companion_feed_run`, and `companion_growth_record`; unique keys `uk_companion_user`, `uk_companion_skill`, `uk_companion_feed_key`, and `uk_companion_growth_run`; resumable/reversible changeSets `20260811-01` through `20260811-07`; executable physical-MySQL pre-migration; explicit `!SINGLE` routing.

- [ ] **Step 0: Create and verify the self-contained Windows Java/Maven wrapper**

Create `scripts/mvnw-java21.ps1`; every later Windows Maven command calls this file because environment mutations inside one spawned PowerShell do not persist to the next:

```powershell
$taskMavenArguments = @($args)

if (-not $env:JAVA_HOME) {
  $taskJavaExecutable = (Get-Command java -ErrorAction Stop).Source
  $taskJdkHome = Split-Path -Parent (Split-Path -Parent $taskJavaExecutable)
  if (-not (Test-Path (Join-Path $taskJdkHome 'bin\javac.exe'))) {
    throw 'JAVA_HOME is unset and java on PATH is not a full JDK 21'
  }
  $env:JAVA_HOME = $taskJdkHome
}
if (-not (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
  throw 'JAVA_HOME does not point to a full JDK'
}

$taskRepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $taskRepositoryRoot
try {
  & .\mvnw.cmd @taskMavenArguments
  $taskMavenExitCode = $LASTEXITCODE
} finally {
  Pop-Location
}
if ($taskMavenExitCode -ne 0) {
  throw "Maven failed with exit code $taskMavenExitCode"
}
```

Run:

```powershell
.\scripts\mvnw-java21.ps1 -version
```

Expected: Maven reports Java `21`. CI obtains `JAVA_HOME` from `actions/setup-java`; do not add a machine-specific JDK path to the repository.

- [ ] **Step 1: Write the failing migration test**

Create `CompanionSchemaMigrationTest` with an isolated, real Liquibase/H2 database. Keep
the Boot test profile on its normal H2 URL, but do not use Boot's
`test-rollback-on-update`: Liquibase opens a second connection for that option, and H2's
`DATABASE_TO_LOWER=TRUE` test URL can make the second connection miss the first
`DATABASECHANGELOG` table. The explicit test below proves the stronger contract,
update → full rollback → update, against an ordinary MySQL-mode H2 database.

```java
package com.li.lipicturecloud.migration;

import org.junit.jupiter.api.Test;
import com.zaxxer.hikari.HikariDataSource;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompanionSchemaMigrationTest {

    @Test
    void updateRollbackAndUpdateAgainLeaveEveryCompanionTableAvailable() throws Exception {
        try (HikariDataSource dataSource = new HikariDataSource()) {
            dataSource.setJdbcUrl("jdbc:h2:mem:companion_migration;MODE=MySQL;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            update(dataSource);
            assertCompanionTables(dataSource, 1);
            rollback(dataSource);
            assertCompanionTables(dataSource, 0);
            update(dataSource);
            assertCompanionTables(dataSource, 1);
        }
    }

    private static void update(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor()) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase(
                    "db/changelog/db.changelog-master.xml", resources, database)) {
                liquibase.update(new Contexts());
            }
        }
    }

    private static void rollback(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor()) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase(
                    "db/changelog/db.changelog-master.xml", resources, database)) {
                liquibase.rollback(7, new Contexts(), new LabelExpression());
            }
        }
    }

    private static void assertCompanionTables(DataSource dataSource, int expectedCount) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        for (String table : List.of(
                "companion", "companion_skill", "companion_feed_run", "companion_growth_record")) {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                    WHERE LOWER(TABLE_SCHEMA) = 'public' AND LOWER(TABLE_NAME) = ?
                    """, Integer.class, table);
            assertThat(count).as(table).isEqualTo(expectedCount);
        }
    }
}
```

- [ ] **Step 2: Extend the sharding contract test and verify RED**

Add this test method to `ShardingModeConfigurationTest`:

```java
@Test
void companionMigrationsUsePhysicalDatasourceAndTablesUseSingleRule() throws IOException {
    for (String profile : List.of(
            "application-sharding-static.yaml", "application-sharding-dynamic.yaml")) {
        assertThat(resource(profile))
                .contains("liquibase:")
                .contains("driver-class-name: com.mysql.cj.jdbc.Driver")
                .contains("jdbc:mysql://")
                .doesNotContain("liquibase:\n    url: jdbc:shardingsphere:");
    }
    for (String rules : List.of("sharding/static.yaml", "sharding/dynamic.yaml")) {
        assertThat(resource(rules))
                .contains("!SINGLE")
                .contains("primary.user")
                .contains("primary.space")
                .contains("primary.space_user")
                .contains("primary.companion")
                .contains("primary.companion_skill")
                .contains("primary.companion_feed_run")
                .contains("primary.companion_growth_record")
                .contains("defaultDataSource: primary");
    }
}
```

Add `import java.util.List;`, then run:

```powershell
.\scripts\mvnw-java21.ps1 "-Dtest=CompanionSchemaMigrationTest,ShardingModeConfigurationTest" test
```

Expected: `CompanionSchemaMigrationTest` reports missing tables/changelog and the new sharding assertions fail.

Also add this source contract to `CompanionSchemaMigrationTest`:

```java
@Test
void physicalMigrationIsExplicitAndDoesNotPutThePasswordOnTheCommandLine() throws Exception {
    String pom = Files.readString(Path.of("pom.xml"));
    String script = Files.readString(Path.of("scripts/migrate-companion-physical.ps1"));
    String wrapper = Files.readString(Path.of("scripts/mvnw-java21.ps1"));
    assertThat(pom)
            .contains("liquibase-maven-plugin", "${env.MYSQL_PASSWORD}");
    assertThat(script)
            .contains("liquibase:update")
            .doesNotContain("-Dliquibase.password");
    assertThat(wrapper)
            .contains("bin\\javac.exe", "mvnw.cmd")
            .doesNotContain("G:\\JDK", "C:\\Program Files");
}
```

- [ ] **Step 3: Enable Liquibase without changing legacy bootstrap scripts**

Add the managed dependency to `pom.xml`:

```xml
<dependency>
    <groupId>org.liquibase</groupId>
    <artifactId>liquibase-core</artifactId>
</dependency>
```

Add this unbound plugin under `build.plugins`; it runs only when an operator explicitly invokes `liquibase:update`. Spring Boot 3.5.14 supplies `liquibase.version=4.31.1` and `mysql.version=9.7.0` through its parent:

```xml
<plugin>
    <groupId>org.liquibase</groupId>
    <artifactId>liquibase-maven-plugin</artifactId>
    <version>${liquibase.version}</version>
    <configuration>
        <changeLogFile>src/main/resources/db/changelog/db.changelog-master.xml</changeLogFile>
        <driver>com.mysql.cj.jdbc.Driver</driver>
        <url>jdbc:mysql://${env.MYSQL_HOST}:${env.MYSQL_PORT}/${env.MYSQL_DATABASE}?useUnicode=true&amp;characterEncoding=UTF-8&amp;serverTimezone=Asia/Shanghai</url>
        <username>${env.MYSQL_USERNAME}</username>
        <password>${env.MYSQL_PASSWORD}</password>
    </configuration>
    <dependencies>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <version>${mysql.version}</version>
        </dependency>
    </dependencies>
</plugin>
```

Do not bind this goal to an application lifecycle phase and do not put a password in source or a Maven command line.

Keep `sql/user.sql`, `sql/picture.sql`, and `sql/space.sql` and their Docker Compose mounts unchanged. Add to the base `spring` block in `application.yaml`:

```yaml
  liquibase:
    enabled: true
    change-log: classpath:/db/changelog/db.changelog-master.xml
```

Add to the existing `app` block:

```yaml
  companion:
    enabled: ${COMPANION_ENABLED:true}
    feeding-enabled: ${COMPANION_FEEDING_ENABLED:true}
    processing-timeout: ${COMPANION_PROCESSING_TIMEOUT:5m}
```

Add the production override under `app` in `application-prod.yaml`:

```yaml
  companion:
    enabled: ${COMPANION_ENABLED:false}
    feeding-enabled: ${COMPANION_FEEDING_ENABLED:false}
```

Add to `application-test.yaml`:

```yaml
spring:
  liquibase:
    enabled: true
    change-log: classpath:/db/changelog/db.changelog-master.xml

app:
  companion:
    enabled: true
    feeding-enabled: true
    processing-timeout: 5m
```

Merge these keys into the existing `spring` and `app` mappings; do not create duplicate YAML top-level keys.

- [ ] **Step 4: Create the exact versioned schema and rollback**

Create the master changelog:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
    <include file="changes/2026-08-11-companion-life-core.xml"
             relativeToChangelogFile="true"/>
</databaseChangeLog>
```

Create the child changelog as seven ordered changeSets, one per MySQL auto-committing DDL statement. Liquibase records each successful statement, so a failed physical pre-migration resumes at the first unapplied changeSet instead of colliding with tables created earlier in the run. Keep table creation parent-first; Liquibase rolls changeSets back in reverse order, which removes indexes and children before parents. Use this exact portable body and do not claim transactional DDL:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
    <changeSet id="20260811-01" author="li-picture-cloud">
        <sql splitStatements="false" stripComments="true"><![CDATA[
CREATE TABLE companion (
    id BIGINT PRIMARY KEY,
    userId BIGINT NOT NULL,
    lifeExperience BIGINT NOT NULL DEFAULT 0,
    level INT NOT NULL DEFAULT 1,
    lifeStage VARCHAR(32) NOT NULL DEFAULT 'LIGHT',
    curiosity DECIMAL(6,2) NOT NULL DEFAULT 0,
    enthusiasm DECIMAL(6,2) NOT NULL DEFAULT 0,
    playfulness DECIMAL(6,2) NOT NULL DEFAULT 0,
    empathy DECIMAL(6,2) NOT NULL DEFAULT 0,
    creativity DECIMAL(6,2) NOT NULL DEFAULT 0,
    balanceVersion VARCHAR(64) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    createTime TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_companion_user UNIQUE (userId),
    CONSTRAINT ck_companion_experience CHECK (lifeExperience >= 0),
    CONSTRAINT ck_companion_curiosity CHECK (curiosity BETWEEN -100 AND 100),
    CONSTRAINT ck_companion_enthusiasm CHECK (enthusiasm BETWEEN -100 AND 100),
    CONSTRAINT ck_companion_playfulness CHECK (playfulness BETWEEN -100 AND 100),
    CONSTRAINT ck_companion_empathy CHECK (empathy BETWEEN -100 AND 100),
    CONSTRAINT ck_companion_creativity CHECK (creativity BETWEEN -100 AND 100)
);
        ]]></sql>
        <rollback><dropTable tableName="companion"/></rollback>
    </changeSet>

    <changeSet id="20260811-02" author="li-picture-cloud">
        <sql splitStatements="false" stripComments="true"><![CDATA[
CREATE TABLE companion_skill (
    id BIGINT PRIMARY KEY,
    companionId BIGINT NOT NULL,
    skillCode VARCHAR(64) NOT NULL,
    skillExperience BIGINT NOT NULL DEFAULT 0,
    createTime TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_companion_skill UNIQUE (companionId, skillCode),
    CONSTRAINT ck_companion_skill_experience CHECK (skillExperience >= 0)
);
        ]]></sql>
        <rollback><dropTable tableName="companion_skill"/></rollback>
    </changeSet>

    <changeSet id="20260811-03" author="li-picture-cloud">
        <sql splitStatements="false" stripComments="true"><![CDATA[
CREATE TABLE companion_feed_run (
    id BIGINT PRIMARY KEY,
    companionId BIGINT NOT NULL,
    subjectId BIGINT NOT NULL,
    pictureId BIGINT NOT NULL,
    idempotencyKey VARCHAR(64) NOT NULL,
    requestFingerprint CHAR(64) NOT NULL,
    correlationId CHAR(36) NOT NULL,
    status VARCHAR(24) NOT NULL,
    nutritionMode VARCHAR(48) NOT NULL,
    contentUnderstood BOOLEAN NOT NULL DEFAULT FALSE,
    resultGrowthRecordId BIGINT NULL,
    safeErrorCode VARCHAR(64) NULL,
    safeErrorMessage VARCHAR(255) NULL,
    safeErrorTime TIMESTAMP NULL,
    attemptCount INT NOT NULL DEFAULT 1,
    revision BIGINT NOT NULL DEFAULT 0,
    createTime TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_companion_feed_key UNIQUE (companionId, idempotencyKey)
);
        ]]></sql>
        <rollback><dropTable tableName="companion_feed_run"/></rollback>
    </changeSet>

    <changeSet id="20260811-04" author="li-picture-cloud">
        <sql splitStatements="false" stripComments="true"><![CDATA[
CREATE INDEX idx_companion_feed_picture_status
    ON companion_feed_run (companionId, pictureId, status);
        ]]></sql>
        <rollback>
            <dropIndex indexName="idx_companion_feed_picture_status"
                       tableName="companion_feed_run"/>
        </rollback>
    </changeSet>

    <changeSet id="20260811-05" author="li-picture-cloud">
        <sql splitStatements="false" stripComments="true"><![CDATA[
CREATE TABLE companion_growth_record (
    id BIGINT PRIMARY KEY,
    feedingRunId BIGINT NOT NULL,
    companionId BIGINT NOT NULL,
    pictureId BIGINT NOT NULL,
    eventType VARCHAR(32) NOT NULL,
    lifeExperienceDelta BIGINT NOT NULL,
    traitDeltaJson TEXT NOT NULL,
    skillDeltaJson TEXT NOT NULL,
    snapshotJson TEXT NOT NULL,
    reason VARCHAR(512) NOT NULL,
    nutritionMode VARCHAR(48) NOT NULL,
    contentUnderstood BOOLEAN NOT NULL DEFAULT FALSE,
    balanceVersion VARCHAR(64) NOT NULL,
    idempotencyKey VARCHAR(64) NOT NULL,
    correlationId CHAR(36) NOT NULL,
    createTime TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_companion_growth_run UNIQUE (feedingRunId),
    CONSTRAINT ck_companion_growth_experience CHECK (lifeExperienceDelta >= 0)
);
        ]]></sql>
        <rollback><dropTable tableName="companion_growth_record"/></rollback>
    </changeSet>

    <changeSet id="20260811-06" author="li-picture-cloud">
        <sql splitStatements="false" stripComments="true"><![CDATA[
CREATE INDEX idx_companion_growth_history
    ON companion_growth_record (companionId, createTime, id);
        ]]></sql>
        <rollback>
            <dropIndex indexName="idx_companion_growth_history"
                       tableName="companion_growth_record"/>
        </rollback>
    </changeSet>

    <changeSet id="20260811-07" author="li-picture-cloud">
        <sql splitStatements="false" stripComments="true"><![CDATA[
CREATE INDEX idx_companion_growth_picture
    ON companion_growth_record (companionId, pictureId, eventType);
        ]]></sql>
        <rollback>
            <dropIndex indexName="idx_companion_growth_picture"
                       tableName="companion_growth_record"/>
        </rollback>
    </changeSet>
</databaseChangeLog>
```

Do not add foreign keys or logical-delete columns. Cross-context user/picture IDs are authorized in the application layer, and both feeding runs and growth facts must remain auditable.

- [ ] **Step 5: Give Liquibase a physical datasource in sharding profiles**

Merge this block under `spring` in both sharding profile files:

```yaml
  liquibase:
    enabled: true
    change-log: classpath:/db/changelog/db.changelog-master.xml
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/${MYSQL_DATABASE:li_picture_cloud_data}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    user: ${MYSQL_USERNAME:root}
    password: ${MYSQL_PASSWORD}
```

This URL must stay separate from `spring.datasource.url=jdbc:shardingsphere:classpath:sharding/static.yaml` and `jdbc:shardingsphere:classpath:sharding/dynamic.yaml`. It is a checksum/no-op safety path after pre-migration; it cannot establish cold-start ordering because Boot constructs the primary ShardingSphere `DataSource` before running its Liquibase initializer.

Create `scripts/migrate-companion-physical.ps1`:

```powershell
$taskRequiredVariables = @(
  'MYSQL_HOST', 'MYSQL_PORT', 'MYSQL_DATABASE', 'MYSQL_USERNAME', 'MYSQL_PASSWORD'
)
foreach ($taskVariable in $taskRequiredVariables) {
  if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($taskVariable))) {
    throw "Required environment variable is missing: $taskVariable"
  }
}

$taskRepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $taskRepositoryRoot
try {
  & .\scripts\mvnw-java21.ps1 -q liquibase:update
} finally {
  Pop-Location
}
```

For every sharded deployment that introduces a table/rule change, export those five physical-MySQL variables and run `powershell -File scripts/migrate-companion-physical.ps1` before starting either sharding profile. Linux operators set the same environment variables and run `./mvnw -q liquibase:update`. Only after that command exits `0` may ShardingSphere initialize and load the declared single-table metadata.

- [ ] **Step 6: Register explicit single tables in both ShardingSphere rule files**

Append this second rule after the existing `!SHARDING` rule in both `sharding/static.yaml` and `sharding/dynamic.yaml`:

```yaml
  - !SINGLE
    tables:
      - primary.user
      - primary.space
      - primary.space_user
      - primary.companion
      - primary.companion_skill
      - primary.companion_feed_run
      - primary.companion_growth_record
    defaultDataSource: primary
```

Do not list logical `picture`, physical picture shards, or Liquibase metadata tables. This follows the official [ShardingSphere 5.5.2 single-table YAML rule](https://shardingsphere.apache.org/document/5.5.2/en/user-manual/shardingsphere-jdbc/yaml-config/rules/single/) and fixes the existing `user`/`space`/`space_user` loading gap at the same seam.

- [ ] **Step 7: Exercise both real ShardingSphere YAMLs against a migrated physical database**

Create `CompanionSingleTableRoutingIntegrationTest` as a parameterized test over `sharding/static.yaml` and `sharding/dynamic.yaml`. For each resource:

1. Create a uniquely named H2 `HikariDataSource` using `MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1`.
2. Through the physical datasource create minimal `user`, `space`, and `space_user` tables with an `id` plus mutable `marker` column, and `picture_0` through `picture_3` tables containing `id`, `userId`, and `spaceId`.
3. Run the real master changelog with `SpringLiquibase` against that physical datasource.
4. Read the rule resource and replace only its embedded `dataSources` section with `{}` so the factory uses `Map.of("primary", physical)`. Preserve the checked-in explicit `!SINGLE` table list unchanged; the H2 fixture uses matching identifiers and proves that the production rule itself loads.
5. Through the returned ShardingSphere datasource insert, select, update, and delete rows in legacy `user`, `space`, and `space_user` as well as `companion`, `companion_skill`, `companion_feed_run`, and `companion_growth_record`; delete companion rows in child-first order.
6. Close both routing and physical data sources in `finally`.

Use fixed valid values: companion/user `8100`, separate legacy route IDs `8201..8203`, picture `9100`, lowercase key `routing-feed-key-01`, a 64-character lowercase fingerprint, a UUID correlation, `PROCESSING`, `DEMO_DETERMINISTIC`, `{}` JSON strings, and `life-core-v1`. Assert every update count is `1` and the final selects through the routed connection see the updated values. This explicitly protects the existing user/space/space_user routing changed by the new `!SINGLE` rule, not only the four new tables. Do not rewrite the YAML into a test-only approximation and do not replace this with source-string assertions.

- [ ] **Step 8: Run migration and sharding verification**

```powershell
.\scripts\mvnw-java21.ps1 "-Dtest=CompanionSchemaMigrationTest,ShardingModeConfigurationTest,CompanionSingleTableRoutingIntegrationTest" test
```

Expected: Liquibase logs all seven changeSets through update → reverse rollback test → update on H2; all three tests pass, all four application tables exist at test execution time, and both checked-in ShardingSphere rule files route real CRUD to `primary`.

- [ ] **Step 9: Commit the schema foundation**

```powershell
git add pom.xml scripts/mvnw-java21.ps1 scripts/migrate-companion-physical.ps1 src/main/resources/application.yaml src/main/resources/application-prod.yaml src/main/resources/application-sharding-static.yaml src/main/resources/application-sharding-dynamic.yaml src/main/resources/sharding/static.yaml src/main/resources/sharding/dynamic.yaml src/test/resources/application-test.yaml src/main/resources/db/changelog/db.changelog-master.xml src/main/resources/db/changelog/changes/2026-08-11-companion-life-core.xml src/test/java/com/li/lipicturecloud/migration/CompanionSchemaMigrationTest.java src/test/java/com/li/lipicturecloud/sharding/ShardingModeConfigurationTest.java src/test/java/com/li/lipicturecloud/sharding/CompanionSingleTableRoutingIntegrationTest.java docs/superpowers/plans/2026-08-11-companion-life-core.md
git commit -m "build: add companion schema migrations"
```

---

### Task 2: Build the Deterministic Companion Domain Core

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/li/lipicturecloud/domain/companion/Companion.java`
- Create: `src/main/java/com/li/lipicturecloud/domain/companion/CompanionTraits.java`
- Create: `src/main/java/com/li/lipicturecloud/domain/companion/TraitDelta.java`
- Create: `src/main/java/com/li/lipicturecloud/domain/companion/CompanionStage.java`
- Create: `src/main/java/com/li/lipicturecloud/domain/companion/CompanionSkill.java`
- Create: `src/main/java/com/li/lipicturecloud/domain/companion/CompanionBalance.java`
- Create: `src/main/java/com/li/lipicturecloud/domain/companion/NutritionMode.java`
- Create: `src/main/java/com/li/lipicturecloud/domain/companion/PictureNutrition.java`
- Create: `src/main/java/com/li/lipicturecloud/domain/companion/FeedingContext.java`
- Create: `src/main/java/com/li/lipicturecloud/domain/companion/FeedingGrowth.java`
- Create: `src/main/java/com/li/lipicturecloud/domain/companion/GrowthEventType.java`
- Create: `src/main/java/com/li/lipicturecloud/domain/companion/GrowthRecord.java`
- Create: `src/main/java/com/li/lipicturecloud/domain/companion/FeedingRun.java`
- Create: `src/main/java/com/li/lipicturecloud/domain/companion/FeedingRunStatus.java`
- Create: `src/main/java/com/li/lipicturecloud/domain/companion/CompanionRepository.java`
- Create: `src/main/java/com/li/lipicturecloud/domain/companion/GrowthRecordRepository.java`
- Create: `src/main/java/com/li/lipicturecloud/domain/companion/FeedingRunRepository.java`
- Create: `src/test/java/com/li/lipicturecloud/domain/companion/CompanionTest.java`
- Create: `src/test/java/com/li/lipicturecloud/domain/companion/FeedingRunTest.java`
- Create: `src/test/java/com/li/lipicturecloud/domain/companion/CompanionBalancePropertyTest.java`

**Interfaces:**
- Consumes: a `PictureNutrition` candidate plus trusted `FeedingContext` facts; no Spring, MyBatis, servlet, entity, JSON, model, or permission type.
- Produces: `Companion.awaken(long, CompanionBalance)`, `Companion.restore(Long, long, long, int, CompanionStage, CompanionTraits, Map<CompanionSkill, Long>, String, long, CompanionBalance)`, `Companion.feed(PictureNutrition, FeedingContext, CompanionBalance)`, `CompanionBalance.v1()`, and the three repository Interfaces listed below.

The exact repository surface is:

```java
public interface CompanionRepository {
    Optional<Companion> findByOwnerId(long ownerId);
    Optional<Companion> findByOwnerIdForUpdate(long ownerId);
    Companion createIfAbsent(long ownerId, CompanionBalance balance);
    boolean save(Companion companionAfter, long expectedRevision);
}

public interface GrowthRecordRepository {
    GrowthRecord append(GrowthRecord record);
    Optional<GrowthRecord> findByFeedingRunId(long feedingRunId);
    List<GrowthRecord> findRecent(long companionId, int limit);
    boolean hasFullFeed(long companionId, long pictureId);
    long sumLifeExperienceSince(long companionId, Instant since);
    long sumRevisitExperience(long companionId, long pictureId);
}

public interface FeedingRunRepository {
    Optional<FeedingRun> findByKey(long companionId, String idempotencyKey);
    FeedingRun insert(FeedingRun run);
    boolean restart(long runId, long expectedRevision, Instant now);
    boolean complete(long runId, long expectedRevision, long growthRecordId, Instant now);
    boolean fail(long runId, long expectedRevision, String safeCode, String safeMessage, Instant now);
    boolean reject(long runId, long expectedRevision, String safeCode, String safeMessage, Instant now);
}
```

- [ ] **Step 1: Write focused failing examples**

Create `CompanionTest` with these cases:

```java
class CompanionTest {
    private final CompanionBalance balance = CompanionBalance.v1();

    @Test
    void awakensAsAZeroExperienceLight() {
        Companion companion = Companion.awaken(7L, balance);
        assertThat(companion.ownerId()).isEqualTo(7L);
        assertThat(companion.lifeExperience()).isZero();
        assertThat(companion.level()).isEqualTo(1);
        assertThat(companion.lifeStage()).isEqualTo(CompanionStage.LIGHT);
        assertThat(companion.traits()).isEqualTo(CompanionTraits.neutral());
        assertThat(companion.revision()).isZero();
    }

    @Test
    void appliesFullNutritionThroughBalanceCaps() {
        Companion companion = Companion.awaken(7L, balance).persistedAs(11L);
        PictureNutrition nutrition = new PictureNutrition(
                200L,
                new TraitDelta(bd("5"), bd("0.4"), bd("0"), bd("0.2"), bd("0.3")),
                Map.of(CompanionSkill.IMAGE_OBSERVATION, 80L),
                "演示营养让伙伴练习了观察与叙事。");

        FeedingGrowth result = companion.feed(
                nutrition, new FeedingContext(false, 0L, 0L), balance);

        assertThat(result.eventType()).isEqualTo(GrowthEventType.PICTURE_FED);
        assertThat(result.lifeExperienceDelta()).isEqualTo(60L);
        assertThat(result.traitDelta().curiosity()).isEqualByComparingTo("1.00");
        assertThat(result.skillExperienceDelta())
                .containsEntry(CompanionSkill.IMAGE_OBSERVATION, 25L);
        assertThat(result.companionAfter().revision()).isEqualTo(1L);
    }

    @Test
    void revisitingNeverRepeatsTraitOrSkillGrowth() {
        Companion companion = Companion.awaken(7L, balance).persistedAs(11L);
        FeedingGrowth result = companion.feed(
                PictureNutrition.demo(42L, TraitDelta.zero(), Map.of(), "演示营养"),
                new FeedingContext(true, 20L, 1L), balance);

        assertThat(result.eventType()).isEqualTo(GrowthEventType.PICTURE_REVISITED);
        assertThat(result.lifeExperienceDelta()).isEqualTo(1L);
        assertThat(result.traitDelta()).isEqualTo(TraitDelta.zero());
        assertThat(result.skillExperienceDelta()).isEmpty();
    }

    @Test
    void dailyAndLifetimeRepeatCapsCanReduceGrowthToZero() {
        Companion companion = Companion.awaken(7L, balance).persistedAs(11L);
        FeedingGrowth dailyCapped = companion.feed(
                PictureNutrition.demo(42L, TraitDelta.zero(), Map.of(), "演示营养"),
                new FeedingContext(false, 300L, 0L), balance);
        FeedingGrowth repeatCapped = companion.feed(
                PictureNutrition.demo(42L, TraitDelta.zero(), Map.of(), "演示营养"),
                new FeedingContext(true, 0L, 3L), balance);
        assertThat(dailyCapped.lifeExperienceDelta()).isZero();
        assertThat(repeatCapped.lifeExperienceDelta()).isZero();
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

```powershell
.\scripts\mvnw-java21.ps1 -Dtest=CompanionTest test
```

Expected: compilation fails because the domain types do not exist.

- [ ] **Step 3: Define enums and immutable value records**

Use these exact enums:

```java
public enum CompanionStage { LIGHT, SEEDLING, COMPANION }

public enum CompanionSkill {
    IMAGE_OBSERVATION,
    STORY_CREATION,
    EMOJI_CREATION,
    IMAGE_FUSION,
    GALLERY_SEARCH
}

public enum NutritionMode { DEMO_DETERMINISTIC }

public enum GrowthEventType { PICTURE_FED, PICTURE_REVISITED }

public enum FeedingRunStatus { PROCESSING, COMPLETED, FAILED, REJECTED }
```

Implement `CompanionTraits` and `TraitDelta` as records with the five `BigDecimal` fields `curiosity`, `enthusiasm`, `playfulness`, `empathy`, and `creativity`. Normalize every field to scale `2` using `RoundingMode.HALF_UP`; reject nulls; make `CompanionTraits` reject values outside `[-100.00, 100.00]`; provide `neutral()` and `zero()` factories.

Implement the observation and context contracts exactly:

```java
public record PictureNutrition(
        long requestedLifeExperience,
        TraitDelta requestedTraitDelta,
        Map<CompanionSkill, Long> requestedSkillExperience,
        String reason) {

    public PictureNutrition {
        if (requestedLifeExperience < 0) throw new IllegalArgumentException("experience must be nonnegative");
        Objects.requireNonNull(requestedTraitDelta);
        Objects.requireNonNull(reason);
        requestedSkillExperience = Map.copyOf(requestedSkillExperience);
        if (requestedSkillExperience.values().stream().anyMatch(value -> value == null || value < 0)) {
            throw new IllegalArgumentException("skill experience must be nonnegative");
        }
    }

    public static PictureNutrition demo(long experience, TraitDelta traits,
                                        Map<CompanionSkill, Long> skills, String reason) {
        return new PictureNutrition(experience, traits, skills, reason);
    }
}

public record FeedingContext(
        boolean picturePreviouslyFed,
        long lifeExperienceEarnedToday,
        long revisitExperienceEarnedForPicture) {
    public FeedingContext {
        if (lifeExperienceEarnedToday < 0 || revisitExperienceEarnedForPicture < 0) {
            throw new IllegalArgumentException("feeding totals must be nonnegative");
        }
    }
}
```

- [ ] **Step 4: Implement the versioned balance object**

`CompanionBalance.v1()` is the only place containing these constants. Implement its calculations with integer arithmetic and deterministic clamps:

```java
public final class CompanionBalance {
    private static final CompanionBalance V1 = new CompanionBalance(
            "life-core-v1", ZoneId.of("Asia/Shanghai"),
            60L, 300L, 1L, 3L, 25L,
            new BigDecimal("1.00"), new BigDecimal("80.00"));

    public static CompanionBalance v1() { return V1; }

    public long totalExperienceForLevel(int level) {
        if (level < 1) throw new IllegalArgumentException("level must be positive");
        return 50L * level * (level - 1L);
    }

    public int levelFor(long experience) {
        if (experience < 0) throw new IllegalArgumentException("experience must be nonnegative");
        int level = 1;
        while (experience >= totalExperienceForLevel(level + 1)) level++;
        return level;
    }

    public CompanionStage stageFor(int level) {
        if (level <= 2) return CompanionStage.LIGHT;
        if (level <= 6) return CompanionStage.SEEDLING;
        return CompanionStage.COMPANION;
    }

    public int skillLevelFor(long experience) {
        return levelFor(experience);
    }

    public Instant startOfDay(Instant now) {
        return now.atZone(dayZone).toLocalDate().atStartOfDay(dayZone).toInstant();
    }
}
```

Add methods `version()`, `nextLevelExperience(long)`, `nextSkillLevelExperience(long)`, `fullFeedExperience(requested, earnedToday)`, `revisitExperience(earnedToday, earnedForPicture)`, `skillExperience(requested)`, and `applyTrait(current, requested)`. Their exact formulas are:

```text
full = min(max(requested, 0), 60, max(300 - earnedToday, 0))
revisit = min(1, max(3 - earnedForPicture, 0), max(300 - earnedToday, 0))
skill = min(max(requested, 0), 25)
trait request = clamp(requested, -1.00, 1.00)
if current > 80.00: applied delta = min(trait request, 0.00)
if current < -80.00: applied delta = max(trait request, 0.00)
otherwise: trait result = clamp(current + trait request, -80.00, 80.00)
otherwise: applied trait delta = trait result - current
```

Thus a restored `100.00` plus an inward `-5.00` request becomes `99.00`, not `80.00`; an outward positive request applies `0.00`. Mirror those cases at `-100.00`. Add all four examples to `CompanionTest` so the soft limit never violates the hard per-feed movement bound.

- [ ] **Step 5: Implement the immutable aggregate and growth result**

Use this exact aggregate state and feed signature:

```java
public record Companion(
        Long id,
        long ownerId,
        long lifeExperience,
        int level,
        CompanionStage lifeStage,
        CompanionTraits traits,
        Map<CompanionSkill, Long> skillExperience,
        String balanceVersion,
        long revision) {

    public Companion {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (ownerId <= 0 || lifeExperience < 0 || level < 1 || revision < 0) {
            throw new IllegalArgumentException("invalid companion state");
        }
        Objects.requireNonNull(lifeStage);
        Objects.requireNonNull(traits);
        Objects.requireNonNull(balanceVersion);
        CompanionBalance supportedBalance = CompanionBalance.v1();
        if (!supportedBalance.version().equals(balanceVersion)
                || level != supportedBalance.levelFor(lifeExperience)
                || lifeStage != supportedBalance.stageFor(level)) {
            throw new IllegalArgumentException("unsupported or inconsistent balance state");
        }
        skillExperience = Map.copyOf(skillExperience);
        if (!skillExperience.keySet().equals(EnumSet.allOf(CompanionSkill.class))
                || skillExperience.values().stream().anyMatch(value -> value == null || value < 0)) {
            throw new IllegalArgumentException("skill map must be complete and nonnegative");
        }
    }

    public static Companion awaken(long ownerId, CompanionBalance balance) {
        if (ownerId <= 0) throw new IllegalArgumentException("ownerId must be positive");
        return new Companion(null, ownerId, 0L, 1, CompanionStage.LIGHT,
                CompanionTraits.neutral(), zeroSkills(), balance.version(), 0L);
    }

    public Companion persistedAs(long id) {
        if (id <= 0 || this.id != null) throw new IllegalStateException("invalid persisted id transition");
        return new Companion(id, ownerId, lifeExperience, level, lifeStage,
                traits, skillExperience, balanceVersion, revision);
    }

    public FeedingGrowth feed(PictureNutrition nutrition, FeedingContext context,
                              CompanionBalance balance) {
        GrowthEventType event = context.picturePreviouslyFed()
                ? GrowthEventType.PICTURE_REVISITED : GrowthEventType.PICTURE_FED;
        long experienceDelta = context.picturePreviouslyFed()
                ? balance.revisitExperience(context.lifeExperienceEarnedToday(),
                    context.revisitExperienceEarnedForPicture())
                : balance.fullFeedExperience(nutrition.requestedLifeExperience(),
                    context.lifeExperienceEarnedToday());
        TraitDelta traitDelta = context.picturePreviouslyFed()
                ? TraitDelta.zero() : applyTraits(nutrition.requestedTraitDelta(), balance);
        Map<CompanionSkill, Long> skillDelta = context.picturePreviouslyFed()
                ? Map.of() : applySkillCaps(nutrition.requestedSkillExperience(), balance);
        Companion after = grow(experienceDelta, traitDelta, skillDelta, balance);
        String reason = context.picturePreviouslyFed()
                ? "它认出了曾经品尝过的图片，只留下了一点熟悉感。"
                : nutrition.reason();
        return new FeedingGrowth(after, event, experienceDelta, traitDelta,
                skillDelta, reason, balance.version());
    }
}
```

`private Companion grow(long experienceDelta, TraitDelta traitDelta, Map<CompanionSkill, Long> skillDelta, CompanionBalance balance)` must use `Math.addExact`, derive level/stage from the new cumulative experience, add independent skill experience with `Math.addExact`, increment revision with `Math.addExact`, and return a new aggregate. `restore(Long id, long ownerId, long lifeExperience, int level, CompanionStage lifeStage, CompanionTraits traits, Map<CompanionSkill, Long> skillExperience, String balanceVersion, long revision, CompanionBalance balance)` must validate persisted ID, nonnegative experience/skills/revision, level/stage consistency under the supplied balance, legal traits, and a complete defensive skill map.

Define the result record:

```java
public record FeedingGrowth(
        Companion companionAfter,
        GrowthEventType eventType,
        long lifeExperienceDelta,
        TraitDelta traitDelta,
        Map<CompanionSkill, Long> skillExperienceDelta,
        String reason,
        String balanceVersion) {
    public FeedingGrowth {
        skillExperienceDelta = Map.copyOf(skillExperienceDelta);
    }
}
```

- [ ] **Step 6: Define append-only facts and repository Interfaces**

`GrowthRecord` contains `Long id`, `long feedingRunId`, `long companionId`, `long pictureId`, `GrowthEventType eventType`, deltas, the full `Companion companionAfter` snapshot, safe reason, nutrition mode, `boolean contentUnderstood`, balance version, idempotency key, correlation ID, and `Instant createdTime`. Provide `GrowthRecord.from(long feedingRunId, long companionId, long pictureId, FeedingGrowth growth, NutritionMode nutritionMode, boolean contentUnderstood, String idempotencyKey, String correlationId, Instant createdTime)` and `withId(long)`; no mutator or revision method exists. `PictureNutrition` intentionally has no mode field: the candidate Adapter cannot override the mode already bound into the trusted feeding run.

`FeedingRun` contains `Long id`, companion/subject/picture IDs, key, fingerprint, correlation ID, status, nutrition mode, `contentUnderstood`, optional result growth ID, optional safe error code/message/time, attempt count, revision, and create/update instants. Provide factories `processing(long companionId, long subjectId, long pictureId, String idempotencyKey, String requestFingerprint, String correlationId, NutritionMode mode, boolean contentUnderstood, Instant now)`, `persistedAs(long id)`, `restarted(Instant now)`, `completed(long growthRecordId, Instant now)`, `failed(String safeCode, String safeMessage, Instant now)`, and `rejected(String safeCode, String safeMessage, Instant now)`; every state transition increments revision and never changes subject, picture, key, fingerprint, correlation, or mode. The processing factory independently enforces positive IDs, `[a-z0-9_-]{16,64}`, 64 lowercase hex fingerprint, valid UUID correlation, and nonnull mode/time so non-HTTP callers cannot bypass the MySQL/H2-stable key format. `failed`/`rejected` set all three safe-error fields, while `restarted` and `completed` preserve them so a recovered run still discloses its last safe failure without storing third-party details.

Create the three repository Interfaces with the exact signatures in this task's Interfaces block. They remain under `domain/companion` and import only JDK/domain types.

- [ ] **Step 7: Cover feeding-run transitions and invalid value boundaries**

Create `FeedingRunTest`:

```java
class FeedingRunTest {
    private static final Instant NOW = Instant.parse("2026-08-11T08:00:00Z");

    @Test
    void keepsIdentityStableAcrossRetryAndCompletion() {
        FeedingRun processing = FeedingRun.processing(
                11L, 7L, 102L,
                "6f26d166-0a82-4d9f-8a61-6c21cf2e59d0",
                "f874b3c9fcbec3f749fe12d7ea01bcf09b83244cbe3b16745486df590f3ec97d",
                "fef53056-2d9f-467d-9b1d-1afe9a6638fe",
                NutritionMode.DEMO_DETERMINISTIC, false, NOW).persistedAs(21L);
        FeedingRun failed = processing.failed(
                "NUTRITION_FAILED", "本次没有消化成功，图片未被消耗", NOW.plusSeconds(1));
        FeedingRun restarted = failed.restarted(NOW.plusSeconds(2));
        FeedingRun completed = restarted.completed(31L, NOW.plusSeconds(3));

        assertThat(restarted.status()).isEqualTo(FeedingRunStatus.PROCESSING);
        assertThat(restarted.attemptCount()).isEqualTo(2);
        assertThat(restarted.revision()).isEqualTo(2L);
        assertThat(completed.status()).isEqualTo(FeedingRunStatus.COMPLETED);
        assertThat(completed.resultGrowthRecordId()).isEqualTo(31L);
        assertThat(completed.idempotencyKey()).isEqualTo(processing.idempotencyKey());
        assertThat(completed.correlationId()).isEqualTo(processing.correlationId());
        assertThat(completed.safeErrorCode()).isEqualTo("NUTRITION_FAILED");
        assertThat(completed.safeErrorTime()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void rejectedRunCannotBecomeCompletedOrRestarted() {
        FeedingRun rejected = processing().rejected(
                "PICTURE_UNAVAILABLE", "图片不可用或无权访问", NOW.plusSeconds(1));
        assertThatThrownBy(() -> rejected.completed(31L, NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> rejected.restarted(NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsIllegalDomainValues() {
        assertThatThrownBy(() -> new FeedingContext(false, -1L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PictureNutrition.demo(-1L, TraitDelta.zero(), Map.of(), "演示"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CompanionTraits(
                new BigDecimal("100.01"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Companion.awaken(0L, CompanionBalance.v1()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FeedingRun.processing(
                11L, 7L, 102L, "UPPERCASE-KEY-0001",
                "f874b3c9fcbec3f749fe12d7ea01bcf09b83244cbe3b16745486df590f3ec97d",
                "fef53056-2d9f-467d-9b1d-1afe9a6638fe",
                NutritionMode.DEMO_DETERMINISTIC, false, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private FeedingRun processing() {
        return FeedingRun.processing(
                11L, 7L, 102L,
                "6f26d166-0a82-4d9f-8a61-6c21cf2e59d0",
                "f874b3c9fcbec3f749fe12d7ea01bcf09b83244cbe3b16745486df590f3ec97d",
                "fef53056-2d9f-467d-9b1d-1afe9a6638fe",
                NutritionMode.DEMO_DETERMINISTIC, false, NOW).persistedAs(21L);
    }
}
```

Make each `FeedingRun` transition validate its legal source status exactly as these tests require. Add boundary cases to `CompanionTest` for life thresholds `99 → level 1`, `100 → level 2`, `299 → level 2`, `300 → level 3`, trait requests on both ± limits, and full-feed XP at both the per-feed and daily caps.

- [ ] **Step 8: Add deterministic randomized invariant coverage**

Create `CompanionBalancePropertyTest`:

```java
@Test
void arbitraryFeedSequenceKeepsCoreInvariants() {
    CompanionBalance balance = CompanionBalance.v1();
    Companion companion = Companion.awaken(9L, balance).persistedAs(12L);
    Random random = new Random(20260811L);
    long previousExperience = 0L;

    for (int index = 0; index < 5_000; index++) {
        TraitDelta requested = new TraitDelta(
                bd(random.nextInt(-500, 501), 2),
                bd(random.nextInt(-500, 501), 2),
                bd(random.nextInt(-500, 501), 2),
                bd(random.nextInt(-500, 501), 2),
                bd(random.nextInt(-500, 501), 2));
        PictureNutrition nutrition = PictureNutrition.demo(
                random.nextLong(0, 500), requested,
                Map.of(CompanionSkill.STORY_CREATION, random.nextLong(0, 100)),
                "确定性属性测试");
        boolean repeat = random.nextBoolean();
        FeedingGrowth growth = companion.feed(nutrition,
                new FeedingContext(repeat, random.nextLong(0, 350), random.nextLong(0, 5)),
                balance);
        companion = growth.companionAfter();

        assertThat(companion.lifeExperience()).isGreaterThanOrEqualTo(previousExperience);
        assertThat(companion.traits().values()).allSatisfy(value ->
                assertThat(value).isBetween(new BigDecimal("-100.00"), new BigDecimal("100.00")));
        assertThat(growth.traitDelta().values()).allSatisfy(value ->
                assertThat(value.abs()).isLessThanOrEqualTo(new BigDecimal("1.00")));
        assertThat(companion.level()).isEqualTo(balance.levelFor(companion.lifeExperience()));
        assertThat(companion.lifeStage()).isEqualTo(balance.stageFor(companion.level()));
        previousExperience = companion.lifeExperience();
    }
}

private static BigDecimal bd(int unscaled, int scale) {
    return BigDecimal.valueOf(unscaled, scale);
}
```

Expose `values()` from both five-axis records as an immutable list in canonical axis order.

- [ ] **Step 9: Run the domain and architecture tests**

```powershell
.\scripts\mvnw-java21.ps1 "-Dtest=CompanionTest,FeedingRunTest,CompanionBalancePropertyTest,DomainDependencyTest" test
```

Expected: all focused examples, 5,000 deterministic transitions, and the framework-dependency rule pass.

- [ ] **Step 10: Enforce the 85% branch floor for the domain package**

Add a `check` execution to the existing JaCoCo plugin:

```xml
<execution>
    <id>check-companion-domain</id>
    <phase>verify</phase>
    <goals><goal>check</goal></goals>
    <configuration>
        <rules>
            <rule>
                <element>PACKAGE</element>
                <includes>
                    <include>com.li.lipicturecloud.domain.companion</include>
                </includes>
                <limits>
                    <limit>
                        <counter>BRANCH</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.85</minimum>
                    </limit>
                </limits>
            </rule>
        </rules>
    </configuration>
</execution>
```

Run:

```powershell
.\scripts\mvnw-java21.ps1 "-Dspring.profiles.active=test" verify
```

Expected: build succeeds and the JaCoCo check reports at least `0.85` branch coverage for `com.li.lipicturecloud.domain.companion`. The Maven rule's `PACKAGE` include uses the dotted Java package name; a slash-separated include silently matches no package with the current plugin. Independently confirm `target/site/jacoco/jacoco.xml` contains `<package name="com/li/lipicturecloud/domain/companion">`, because the XML report uses internal slash-separated names. If the rule reports an uncovered domain branch, add a concrete boundary example to one of the domain tests before proceeding; do not lower the threshold or exclude a domain class.

- [ ] **Step 11: Commit the domain core**

```powershell
git add pom.xml src/main/java/com/li/lipicturecloud/domain/companion src/test/java/com/li/lipicturecloud/domain/companion
git commit -m "feat: model deterministic companion growth"
```

---

### Task 3: Persist Companion State, Feeding Runs, and Append-Only Growth

**Files:**
- Create: `src/main/java/com/li/lipicturecloud/model/entity/CompanionEntity.java`
- Create: `src/main/java/com/li/lipicturecloud/model/entity/CompanionSkillEntity.java`
- Create: `src/main/java/com/li/lipicturecloud/model/entity/CompanionFeedRunEntity.java`
- Create: `src/main/java/com/li/lipicturecloud/model/entity/CompanionGrowthRecordEntity.java`
- Create: `src/main/java/com/li/lipicturecloud/mapper/CompanionMapper.java`
- Create: `src/main/java/com/li/lipicturecloud/mapper/CompanionSkillMapper.java`
- Create: `src/main/java/com/li/lipicturecloud/mapper/CompanionFeedRunMapper.java`
- Create: `src/main/java/com/li/lipicturecloud/mapper/CompanionGrowthRecordMapper.java`
- Create: `src/main/java/com/li/lipicturecloud/infrastructure/persistence/companion/CompanionJsonCodec.java`
- Create: `src/main/java/com/li/lipicturecloud/infrastructure/persistence/companion/MybatisCompanionRepository.java`
- Create: `src/main/java/com/li/lipicturecloud/infrastructure/persistence/companion/MybatisGrowthRecordRepository.java`
- Create: `src/main/java/com/li/lipicturecloud/infrastructure/persistence/companion/MybatisFeedingRunRepository.java`
- Create: `src/test/java/com/li/lipicturecloud/infrastructure/persistence/companion/CompanionPersistenceIntegrationTest.java`

**Interfaces:**
- Consumes: the three domain repository Interfaces from Task 2, MyBatis `BaseMapper`, the four Task 1 tables, and the application-configured Jackson `ObjectMapper`.
- Produces: race-safe `createIfAbsent`, row-locking `findByOwnerIdForUpdate`, revision compare-and-set `save`, complete skill upserts, ordered history/cap queries, and compare-and-set feeding-run transitions.

All entity IDs use `@TableId(type = IdType.ASSIGN_ID)`. No entity uses `@TableLogic`, `@Version`, or a domain class as a field.

- [ ] **Step 1: Write a failing H2 integration test for uniqueness and optimistic save**

Start `CompanionPersistenceIntegrationTest` with:

```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CompanionPersistenceIntegrationTest {

    @Autowired CompanionRepository companionRepository;
    @Autowired GrowthRecordRepository growthRecordRepository;
    @Autowired FeedingRunRepository feedingRunRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private final CompanionBalance balance = CompanionBalance.v1();

    @Test
    void createIsIdempotentAndRevisionSaveRejectsStaleWriter() {
        Companion first = companionRepository.createIfAbsent(501L, balance);
        Companion second = companionRepository.createIfAbsent(501L, balance);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM companion WHERE userId = 501", Long.class)).isEqualTo(1L);

        FeedingGrowth growth = first.feed(
                PictureNutrition.demo(42L,
                        new TraitDelta(bd("0.6"), bd("0.4"), bd("0"), bd("0.2"), bd("0.3")),
                        Map.of(CompanionSkill.IMAGE_OBSERVATION, 18L), "演示营养"),
                new FeedingContext(false, 0L, 0L), balance);

        assertThat(companionRepository.save(growth.companionAfter(), first.revision())).isTrue();
        assertThat(companionRepository.save(growth.companionAfter(), first.revision())).isFalse();
        Companion reloaded = companionRepository.findByOwnerId(501L).orElseThrow();
        assertThat(reloaded.lifeExperience()).isEqualTo(42L);
        assertThat(reloaded.skillExperience())
                .containsEntry(CompanionSkill.IMAGE_OBSERVATION, 18L);
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
```

Add a true two-thread case using a user ID not used by other tests:

```java
@Test
@Transactional(propagation = Propagation.NOT_SUPPORTED)
void concurrentAwakenCreatesOneCompanion() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
        Callable<Companion> create = () -> {
            start.await(5, TimeUnit.SECONDS);
            return companionRepository.createIfAbsent(599L, balance);
        };
        Future<Companion> left = pool.submit(create);
        Future<Companion> right = pool.submit(create);
        start.countDown();
        assertThat(left.get(10, TimeUnit.SECONDS).id())
                .isEqualTo(right.get(10, TimeUnit.SECONDS).id());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM companion WHERE userId = 599", Long.class)).isEqualTo(1L);
    } finally {
        pool.shutdownNow();
        jdbcTemplate.update("DELETE FROM companion_skill WHERE companionId IN "
                + "(SELECT id FROM companion WHERE userId = 599)");
        jdbcTemplate.update("DELETE FROM companion WHERE userId = 599");
    }
}
```

Add `import org.springframework.transaction.annotation.Propagation;`. This method disables the class-level test transaction, so both worker commits and the `finally` cleanup use real database commits. The explicit cleanup targets only subject `599`; leaving the class-level transaction active here would roll back the cleanup while leaking the worker-thread rows.

- [ ] **Step 2: Add failing feeding-run and growth round-trip cases**

Add:

```java
@Test
void feedingRunTransitionsAndGrowthSnapshotRoundTrip() {
    Companion companion = companionRepository.createIfAbsent(502L, balance);
    Instant now = Instant.parse("2026-08-11T08:00:00Z");
    FeedingRun run = feedingRunRepository.insert(FeedingRun.processing(
            companion.id(), 502L, 102L,
            "6f26d166-0a82-4d9f-8a61-6c21cf2e59d0",
            sha256("pictureId=102"), "fef53056-2d9f-467d-9b1d-1afe9a6638fe",
            NutritionMode.DEMO_DETERMINISTIC, false, now));

    FeedingGrowth growth = companion.feed(
            PictureNutrition.demo(42L, TraitDelta.zero(),
                    Map.of(CompanionSkill.STORY_CREATION, 12L), "演示营养"),
            new FeedingContext(false, 0L, 0L), balance);
    GrowthRecord record = growthRecordRepository.append(GrowthRecord.from(
            run.id(), companion.id(), 102L, growth,
            run.nutritionMode(), run.contentUnderstood(),
            run.idempotencyKey(), run.correlationId(), now));

    assertThat(feedingRunRepository.complete(
            run.id(), run.revision(), record.id(), now.plusSeconds(1))).isTrue();
    GrowthRecord reloaded = growthRecordRepository.findByFeedingRunId(run.id()).orElseThrow();
    assertThat(reloaded.companionAfter()).isEqualTo(growth.companionAfter());
    assertThat(reloaded.skillExperienceDelta())
            .containsEntry(CompanionSkill.STORY_CREATION, 12L);
    assertThat(feedingRunRepository.findByKey(companion.id(), run.idempotencyKey()).orElseThrow().status())
            .isEqualTo(FeedingRunStatus.COMPLETED);
}
```

Implement the test-local `sha256(String value)` by hashing `value.getBytes(StandardCharsets.UTF_8)` with `MessageDigest.getInstance("SHA-256")` and returning `HexFormat.of().formatHex(digest)`.

- [ ] **Step 3: Run the integration test and verify RED**

```powershell
.\scripts\mvnw-java21.ps1 -Dtest=CompanionPersistenceIntegrationTest test
```

Expected: the test class compiles against the Task 2 Interfaces, then the Spring test context fails because no `CompanionRepository`, `GrowthRecordRepository`, or `FeedingRunRepository` implementation beans exist yet.

- [ ] **Step 4: Create four persistence-only entities**

Follow the existing Lombok/MyBatis style. `CompanionEntity` begins:

```java
@Data
@TableName("companion")
public class CompanionEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long lifeExperience;
    private Integer level;
    private String lifeStage;
    private BigDecimal curiosity;
    private BigDecimal enthusiasm;
    private BigDecimal playfulness;
    private BigDecimal empathy;
    private BigDecimal creativity;
    private String balanceVersion;
    private Long revision;
    private Date createTime;
    private Date updateTime;
}
```

Create the remaining entities with exact table-column parity:

```text
CompanionSkillEntity:
id, companionId, skillCode, skillExperience, createTime, updateTime

CompanionFeedRunEntity:
id, companionId, subjectId, pictureId, idempotencyKey, requestFingerprint,
correlationId, status, nutritionMode, contentUnderstood, resultGrowthRecordId,
safeErrorCode, safeErrorMessage, safeErrorTime, attemptCount, revision, createTime, updateTime

CompanionGrowthRecordEntity:
id, feedingRunId, companionId, pictureId, eventType, lifeExperienceDelta,
traitDeltaJson, skillDeltaJson, snapshotJson, reason, nutritionMode,
contentUnderstood, balanceVersion, idempotencyKey, correlationId, createTime
```

Use wrapper types for nullable columns and `Date` for all timestamps. Set `@TableId(type = IdType.ASSIGN_ID)` on every ID.

- [ ] **Step 5: Create mappers with the exact lock and aggregate query surface**

`CompanionMapper`, `CompanionSkillMapper`, `CompanionFeedRunMapper`, and `CompanionGrowthRecordMapper` extend `BaseMapper<CompanionEntity>`, `BaseMapper<CompanionSkillEntity>`, `BaseMapper<CompanionFeedRunEntity>`, and `BaseMapper<CompanionGrowthRecordEntity>` respectively. Add this lock query to `CompanionMapper`:

```java
@Select("SELECT * FROM companion WHERE userId = #{userId} LIMIT 1 FOR UPDATE")
CompanionEntity selectByUserIdForUpdate(@Param("userId") long userId);
```

Add these queries to `CompanionGrowthRecordMapper`:

```java
@Select("""
        SELECT COUNT(*) FROM companion_growth_record
        WHERE companionId = #{companionId} AND pictureId = #{pictureId}
          AND eventType = 'PICTURE_FED'
        """)
long countFullFeeds(@Param("companionId") long companionId,
                    @Param("pictureId") long pictureId);

@Select("""
        SELECT COALESCE(SUM(lifeExperienceDelta), 0)
        FROM companion_growth_record
        WHERE companionId = #{companionId} AND createTime >= #{since}
        """)
long sumLifeExperienceSince(@Param("companionId") long companionId,
                            @Param("since") Date since);

@Select("""
        SELECT COALESCE(SUM(lifeExperienceDelta), 0)
        FROM companion_growth_record
        WHERE companionId = #{companionId} AND pictureId = #{pictureId}
          AND eventType = 'PICTURE_REVISITED'
        """)
long sumRevisitExperience(@Param("companionId") long companionId,
                          @Param("pictureId") long pictureId);
```

No mapper exposes an update or delete method specifically for `companion_growth_record`.

- [ ] **Step 6: Implement persistence JSON mapping without leaking Jackson into the domain**

`CompanionJsonCodec` is a Spring component under `infrastructure/persistence/companion`. Use a private payload record rather than serializing the aggregate class:

```java
record CompanionSnapshotPayload(
        Long id, long ownerId, long lifeExperience, int level, String lifeStage,
        BigDecimal curiosity, BigDecimal enthusiasm, BigDecimal playfulness,
        BigDecimal empathy, BigDecimal creativity,
        Map<String, Long> skills, String balanceVersion, long revision) {}
```

Expose:

```java
String writeTraitDelta(TraitDelta value);
TraitDelta readTraitDelta(String json);
String writeSkillDelta(Map<CompanionSkill, Long> value);
Map<CompanionSkill, Long> readSkillDelta(String json);
String writeSnapshot(Companion companion);
Companion readSnapshot(String json, CompanionBalance balance);
```

Map enum keys through `CompanionSkill.name()`, reconstruct all absent skill codes as zero, and wrap `JsonProcessingException` in `IllegalStateException("伙伴持久化数据无法解析", cause)`. Never log the raw JSON on failure.

Annotate `MybatisCompanionRepository`, `MybatisGrowthRecordRepository`, and `MybatisFeedingRunRepository` with Spring `@Repository` and use constructor injection only for the mappers and `CompanionJsonCodec` they actually need. The annotation is part of the executable contract: it registers each domain repository Interface implementation and keeps persistence exception translation active. Task 3 rehydrates through the locked `CompanionBalance.v1()` factory as specified below; do not inject the `CompanionBalance` bean before Task 4 creates it, and do not rely on component scanning to instantiate an unannotated concrete class.

- [ ] **Step 7: Implement race-safe companion creation and row rehydration**

`MybatisCompanionRepository` uses constructor injection. Its create contract is:

```java
@Override
public Companion createIfAbsent(long ownerId, CompanionBalance balance) {
    Optional<Companion> existing = findByOwnerId(ownerId);
    if (existing.isPresent()) return existing.get();

    CompanionEntity row = toRow(Companion.awaken(ownerId, balance));
    try {
        companionMapper.insert(row);
    } catch (DuplicateKeyException raceWonElsewhere) {
        return findByOwnerId(ownerId).orElseThrow(() ->
                new IllegalStateException("伙伴唯一键冲突后无法读取已存在伙伴", raceWonElsewhere));
    }
    return fromRows(row, List.of(), balance);
}
```

`findByOwnerId` uses `LambdaQueryWrapper.eq(CompanionEntity::getUserId, ownerId)`. `findByOwnerIdForUpdate` calls the custom mapper. Both load `companion_skill` rows and call `Companion.restore(row.getId(), row.getUserId(), row.getLifeExperience(), row.getLevel(), CompanionStage.valueOf(row.getLifeStage()), traits(row), skills(rows), row.getBalanceVersion(), row.getRevision(), CompanionBalance.v1())`; reject any stored balance version other than `life-core-v1` in this slice with `IllegalStateException("不支持的伙伴平衡版本: " + version)`.

- [ ] **Step 8: Implement compare-and-set aggregate persistence**

`save` performs the snapshot update first:

```java
@Override
@Transactional(rollbackFor = Exception.class)
public boolean save(Companion after, long expectedRevision) {
    UpdateWrapper<CompanionEntity> update = new UpdateWrapper<>();
    update.eq("id", after.id())
            .eq("revision", expectedRevision)
            .set("lifeExperience", after.lifeExperience())
            .set("level", after.level())
            .set("lifeStage", after.lifeStage().name())
            .set("curiosity", after.traits().curiosity())
            .set("enthusiasm", after.traits().enthusiasm())
            .set("playfulness", after.traits().playfulness())
            .set("empathy", after.traits().empathy())
            .set("creativity", after.traits().creativity())
            .set("balanceVersion", after.balanceVersion())
            .set("revision", after.revision())
            .set("updateTime", new Date());
    if (companionMapper.update(null, update) != 1) return false;
    upsertSkills(after);
    return true;
}
```

For each canonical `CompanionSkill`, query by `(companionId, skillCode)`. Insert a row when absent and experience is positive; otherwise update only when the persisted experience differs. A unique-key collision on insert must retry as an update inside the same transaction. Do not delete zero rows.

- [ ] **Step 9: Implement append-only growth and cap queries**

`MybatisGrowthRecordRepository.append` assigns JSON payloads, calls `insert`, reloads the inserted row with `selectById(entity.getId())`, and decodes that database-normalized row. Returning the reloaded timestamp makes the first response byte-for-byte reconstructible on idempotent replay. `findByFeedingRunId` and `findRecent` decode every payload and return immutable domain records. Clamp `findRecent` to `1..50` and order with:

```java
new LambdaQueryWrapper<CompanionGrowthRecordEntity>()
        .eq(CompanionGrowthRecordEntity::getCompanionId, companionId)
        .orderByDesc(CompanionGrowthRecordEntity::getCreateTime)
        .orderByDesc(CompanionGrowthRecordEntity::getId)
        .last("LIMIT " + safeLimit)
```

Delegate `hasFullFeed`, `sumLifeExperienceSince`, and `sumRevisitExperience` to the three mapper queries from Step 5. Use `Date.from(since)` for the daily boundary.

- [ ] **Step 10: Implement compare-and-set feeding-run transitions**

`MybatisFeedingRunRepository.findByKey` queries the unique pair. `insert` maps a `PROCESSING` run and returns it with the assigned ID. Implement all four transitions with a shared update method that includes both current revision and the legal source status:

```java
private boolean transition(long id, long expectedRevision, FeedingRunStatus source,
                           FeedingRun target) {
    UpdateWrapper<CompanionFeedRunEntity> update = new UpdateWrapper<>();
    update.eq("id", id)
            .eq("revision", expectedRevision)
            .eq("status", source.name())
            .set("status", target.status().name())
            .set("resultGrowthRecordId", target.resultGrowthRecordId())
            .set("safeErrorCode", target.safeErrorCode())
            .set("safeErrorMessage", target.safeErrorMessage())
            .set("safeErrorTime", target.safeErrorTime() == null
                    ? null : Date.from(target.safeErrorTime()))
            .set("attemptCount", target.attemptCount())
            .set("revision", target.revision())
            .set("updateTime", Date.from(target.updatedAt()));
    return feedRunMapper.update(null, update) == 1;
}
```

`restart` reloads the current row, builds `current.restarted(now)`, and permits `FAILED → PROCESSING` or stale `PROCESSING → PROCESSING`; after a successful compare-and-set the coordinator reloads the new revision before returning `STARTED`. `complete`, `fail`, and `reject` reload the current row, build the corresponding domain target, and permit only `PROCESSING` as the source. This keeps the public repository signatures from Task 2 and ensures callers always continue with the persisted revision.

- [ ] **Step 11: Run persistence and migration tests**

```powershell
.\scripts\mvnw-java21.ps1 "-Dtest=CompanionPersistenceIntegrationTest,CompanionSchemaMigrationTest,DomainDependencyTest" test
```

Expected: all tests pass. SQL output shows the second stale snapshot update affects `0` rows, and no update/delete statement targets `companion_growth_record`.

- [ ] **Step 12: Commit the persistence Adapters**

```powershell
git add src/main/java/com/li/lipicturecloud/model/entity/CompanionEntity.java src/main/java/com/li/lipicturecloud/model/entity/CompanionSkillEntity.java src/main/java/com/li/lipicturecloud/model/entity/CompanionFeedRunEntity.java src/main/java/com/li/lipicturecloud/model/entity/CompanionGrowthRecordEntity.java src/main/java/com/li/lipicturecloud/mapper/CompanionMapper.java src/main/java/com/li/lipicturecloud/mapper/CompanionSkillMapper.java src/main/java/com/li/lipicturecloud/mapper/CompanionFeedRunMapper.java src/main/java/com/li/lipicturecloud/mapper/CompanionGrowthRecordMapper.java src/main/java/com/li/lipicturecloud/infrastructure/persistence/companion src/test/java/com/li/lipicturecloud/infrastructure/persistence/companion
git commit -m "feat: persist companion feeding history"
```

---

### Task 4: Orchestrate Authorized, Idempotent Feeding Through a Demo Adapter

**Files:**
- Create: `src/main/java/com/li/lipicturecloud/config/CompanionConfiguration.java`
- Create: `src/main/java/com/li/lipicturecloud/config/CompanionFeatureProperties.java`
- Create: `src/main/java/com/li/lipicturecloud/application/companion/PictureNutritionAnalyzer.java`
- Create: `src/main/java/com/li/lipicturecloud/application/companion/AuthorizedPictureRef.java`
- Create: `src/main/java/com/li/lipicturecloud/infrastructure/companion/DemoPictureNutritionAdapter.java`
- Create: `src/main/java/com/li/lipicturecloud/application/companion/CompanionLife.java`
- Create: `src/main/java/com/li/lipicturecloud/application/companion/FeedPictureCommand.java`
- Create: `src/main/java/com/li/lipicturecloud/application/companion/FeedReservation.java`
- Create: `src/main/java/com/li/lipicturecloud/application/companion/CompanionFeedingCoordinator.java`
- Create: `src/main/java/com/li/lipicturecloud/application/companion/CompanionLifeService.java`
- Create: `src/main/java/com/li/lipicturecloud/application/companion/CompanionViewAssembler.java`
- Create: `src/main/java/com/li/lipicturecloud/application/companion/view/CompanionHomeView.java`
- Create: `src/main/java/com/li/lipicturecloud/application/companion/view/CompanionView.java`
- Create: `src/main/java/com/li/lipicturecloud/application/companion/view/CompanionTraitsView.java`
- Create: `src/main/java/com/li/lipicturecloud/application/companion/view/CompanionSkillView.java`
- Create: `src/main/java/com/li/lipicturecloud/application/companion/view/NutritionStatusView.java`
- Create: `src/main/java/com/li/lipicturecloud/application/companion/view/GrowthRecordView.java`
- Create: `src/main/java/com/li/lipicturecloud/application/companion/view/FeedPictureResult.java`
- Create: `src/test/java/com/li/lipicturecloud/application/companion/CompanionFeedingCoordinatorTest.java`
- Create: `src/test/java/com/li/lipicturecloud/application/companion/CompanionFeedingIntegrationTest.java`
- Create: `src/test/java/com/li/lipicturecloud/application/companion/CompanionLifeServiceTest.java`
- Create: `src/test/java/com/li/lipicturecloud/infrastructure/companion/DemoPictureNutritionAdapterTest.java`

**Interfaces:**
- Consumes: authenticated `AuthorizationSubject`, `SpaceAuthorizationAccessService.checkForUser(PICTURE_VIEW, pictureId, subject.userId())`, domain repositories, injected `Clock`, and the demo analyzer.
- Produces:

```java
public interface CompanionLife {
    CompanionHomeView home(AuthorizationSubject subject);
    CompanionHomeView awaken(AuthorizationSubject subject);
    FeedPictureResult feed(FeedPictureCommand command);
}

public record FeedPictureCommand(
        AuthorizationSubject subject, long pictureId, String idempotencyKey) {}

public interface PictureNutritionAnalyzer {
    NutritionMode mode();
    boolean contentUnderstood();
    PictureNutrition analyze(AuthorizedPictureRef picture);
}

public record AuthorizedPictureRef(AuthorizationSubject subject, long pictureId) {
    public AuthorizedPictureRef {
        Objects.requireNonNull(subject);
        if (pictureId <= 0) throw new IllegalArgumentException("pictureId must be positive");
    }
}
```

The application result records are:

```java
public record CompanionHomeView(
        CompanionView companion,
        NutritionStatusView nutrition,
        List<GrowthRecordView> recentGrowth) {}

public record CompanionView(
        Long id,
        long lifeExperience,
        int level,
        String lifeStage,
        long levelStartExperience,
        long nextLevelExperience,
        CompanionTraitsView traits,
        List<CompanionSkillView> skills,
        String balanceVersion,
        long revision) {}

public record CompanionTraitsView(
        BigDecimal curiosity, BigDecimal enthusiasm, BigDecimal playfulness,
        BigDecimal empathy, BigDecimal creativity) {}

public record CompanionSkillView(
        String code, long experience, int level, long nextLevelExperience) {}

public record NutritionStatusView(
        String mode, boolean contentUnderstood, String notice) {}

public record GrowthRecordView(
        Long id, Long sourcePictureId, String eventType,
        long lifeExperienceDelta, CompanionTraitsView traitDelta,
        Map<String, Long> skillExperienceDelta, String reason,
        String balanceVersion, String nutritionMode,
        boolean contentUnderstood, Instant createdTime) {}

public record FeedPictureResult(
        String outcome, String correlationId,
        CompanionView companion, GrowthRecordView growth) {}
```

- [ ] **Step 1: Write failing orchestration tests for safe authorization and replay**

Create `CompanionLifeServiceTest` with Mockito constructor mocks and a fixed clock. Cover these exact behaviors:

```java
@Test
void normalizesMissingAndDeniedPicturesBeforeAnalyzerUse() {
    AuthorizationSubject subject = AuthorizationSubject.user(7L);
    Companion companion = Companion.awaken(7L, balance).persistedAs(11L);
    FeedingRun run = processingRun(companion, subject, 102L);
    when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
    when(coordinator.reserve(any(), eq(subject), eq(102L), anyString(), anyString(), anyString(),
            any(), eq(false)))
            .thenReturn(FeedReservation.started(run));
    doThrow(new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在"))
            .when(authorization).checkForUser(PICTURE_VIEW, 102L, 7L);

    BusinessException error = assertThrows(BusinessException.class,
            () -> service.feed(new FeedPictureCommand(subject, 102L, VALID_KEY)));

    assertThat(error.getMessage()).isEqualTo("图片不可用或无权访问");
    verify(coordinator).reject(run, "PICTURE_UNAVAILABLE", "图片不可用或无权访问");
    verify(analyzer, never()).analyze(any());
}

@Test
void completedIdempotencyRunRechecksPermissionAndReturnsOriginalResult() {
    AuthorizationSubject subject = AuthorizationSubject.user(7L);
    Companion companion = Companion.awaken(7L, balance).persistedAs(11L);
    FeedPictureResult original = feedResult(companion);
    when(companionRepository.findByOwnerId(7L)).thenReturn(Optional.of(companion));
    when(coordinator.reserve(any(), eq(subject), eq(102L), eq(VALID_KEY),
            anyString(), anyString(), eq(NutritionMode.DEMO_DETERMINISTIC), eq(false)))
            .thenReturn(FeedReservation.replay(completedRun(companion), original));

    FeedPictureResult returned = service.feed(new FeedPictureCommand(subject, 102L, VALID_KEY));

    assertThat(returned).isEqualTo(original);
    verify(authorization).checkForUser(PICTURE_VIEW, 102L, 7L);
    verify(analyzer, never()).analyze(any());
}
```

Repeat the first test with `ErrorCode.NO_AUTH_ERROR` and assert the same external code/message. Test `feedingEnabled=false` rejects before reservation with `伙伴喂养已暂停`. Test analyzer failure calls `coordinator.fail(run, "NUTRITION_FAILED", "本次没有消化成功，图片未被消耗")` and never calls `complete`.

Use these exact in-class fixtures in both application tests; `CompanionViewAssembler` is a real instance backed by `CompanionBalance.v1()` and the mocked analyzer:

```java
private static final Instant NOW = Instant.parse("2026-08-11T08:00:00Z");
private static final String VALID_KEY = "6f26d166-0a82-4d9f-8a61-6c21cf2e59d0";
private static final String CORRELATION = "fef53056-2d9f-467d-9b1d-1afe9a6638fe";

private Companion persistedCompanion() {
    return Companion.awaken(7L, CompanionBalance.v1()).persistedAs(11L);
}

private FeedingRun processingRun(Companion companion, AuthorizationSubject subject, long pictureId) {
    return FeedingRun.processing(companion.id(), subject.userId(), pictureId, VALID_KEY,
            fingerprint(pictureId), CORRELATION,
            NutritionMode.DEMO_DETERMINISTIC, false, NOW).persistedAs(21L);
}

private FeedingRun completedRun(Companion companion) {
    return processingRun(companion, AuthorizationSubject.user(7L), 102L)
            .completed(31L, NOW);
}

private FeedPictureResult feedResult(Companion companion) {
    return new FeedPictureResult("GROWN", CORRELATION,
            assembler.companion(companion), null);
}

private static String fingerprint(long pictureId) {
    try {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(("pictureId=" + pictureId).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException error) {
        throw new IllegalStateException(error);
    }
}
```

- [ ] **Step 2: Write failing reservation and completion tests**

Create `CompanionFeedingCoordinatorTest` with these core cases:

```java
@Test
void sameKeyWithDifferentPictureIsAConflict() {
    Companion companion = persistedCompanion();
    FeedingRun existing = processingRun(companion, AuthorizationSubject.user(7L), 101L);
    when(runRepository.findByKey(companion.id(), VALID_KEY)).thenReturn(Optional.of(existing));

    BusinessException error = assertThrows(BusinessException.class, () -> coordinator.reserve(
            companion, AuthorizationSubject.user(7L), 102L, VALID_KEY,
            fingerprint(102L), "fef53056-2d9f-467d-9b1d-1afe9a6638fe",
            NutritionMode.DEMO_DETERMINISTIC, false));

    assertThat(error.getCode()).isEqualTo(ErrorCode.PARAMS_ERROR.getCode());
    assertThat(error.getMessage()).isEqualTo("幂等键已用于另一张图片");
}

@Test
void completionUsesDailyAndRepeatFactsThenCommitsOneGrowthRecord() {
    Companion companion = persistedCompanion();
    FeedingRun run = processingRun(companion, AuthorizationSubject.user(7L), 102L);
    when(companionRepository.findByOwnerIdForUpdate(7L)).thenReturn(Optional.of(companion));
    when(growthRepository.hasFullFeed(companion.id(), 102L)).thenReturn(true);
    when(growthRepository.sumLifeExperienceSince(eq(companion.id()), any())).thenReturn(20L);
    when(growthRepository.sumRevisitExperience(companion.id(), 102L)).thenReturn(1L);
    when(companionRepository.save(any(), eq(companion.revision()))).thenReturn(true);
    when(growthRepository.append(any())).thenAnswer(invocation ->
            invocation.<GrowthRecord>getArgument(0).withId(31L));
    when(runRepository.complete(run.id(), run.revision(), 31L, NOW)).thenReturn(true);

    FeedPictureResult result = coordinator.complete(run,
            PictureNutrition.demo(42L, TraitDelta.zero(),
                    Map.of(CompanionSkill.STORY_CREATION, 12L), "演示营养"));

    assertThat(result.outcome()).isEqualTo("FAMILIARITY");
    assertThat(result.growth().lifeExperienceDelta()).isEqualTo(1L);
    assertThat(result.growth().skillExperienceDelta()).isEmpty();
    verify(growthRepository).append(any(GrowthRecord.class));
    verify(runRepository).complete(run.id(), run.revision(), 31L, NOW);
}
```

Also test a `FAILED` run restarts with the same correlation/key and incremented attempt count; a non-stale `PROCESSING` run returns `IN_PROGRESS`; a stale run restarts; a `COMPLETED` run whose growth row is missing throws `喂养回执不完整`; and a false companion CAS throws `伙伴状态已变化，请重试` before growth append.

- [ ] **Step 3: Run the two tests and verify RED**

```powershell
.\scripts\mvnw-java21.ps1 "-Dtest=CompanionLifeServiceTest,CompanionFeedingCoordinatorTest" test
```

Expected: compilation fails because the application Interfaces, records, and services do not exist.

- [ ] **Step 4: Bind feature switches, balance, and clock**

Implement mutable configuration properties so Boot can bind the existing YAML:

```java
@Getter
@Setter
@ConfigurationProperties(prefix = "app.companion")
public class CompanionFeatureProperties {
    private boolean enabled = true;
    private boolean feedingEnabled = true;
    private Duration processingTimeout = Duration.ofMinutes(5);
}
```

Create configuration:

```java
@Configuration
@EnableConfigurationProperties(CompanionFeatureProperties.class)
public class CompanionConfiguration {
    @Bean
    public CompanionBalance companionBalance() {
        return CompanionBalance.v1();
    }

    @Bean
    public Clock companionClock() {
        return Clock.systemUTC();
    }
}
```

- [ ] **Step 5: Implement the deterministic fake nutrition Adapter**

Create the Interface and authorized reference from this task's Interfaces block. Implement:

```java
@Component
public class DemoPictureNutritionAdapter implements PictureNutritionAnalyzer {
    @Override
    public NutritionMode mode() { return NutritionMode.DEMO_DETERMINISTIC; }

    @Override
    public boolean contentUnderstood() { return false; }

    @Override
    public PictureNutrition analyze(AuthorizedPictureRef picture) {
        return switch (Math.floorMod(picture.pictureId(), 3)) {
            case 0 -> PictureNutrition.demo(42L,
                    new TraitDelta(bd("0.60"), bd("0.40"), bd("0"), bd("0.20"), bd("0.30")),
                    Map.of(CompanionSkill.IMAGE_OBSERVATION, 18L,
                           CompanionSkill.STORY_CREATION, 12L),
                    "演示营养让伙伴练习了观察与叙事。");
            case 1 -> PictureNutrition.demo(36L,
                    new TraitDelta(bd("0.20"), bd("0.20"), bd("0.70"), bd("0.10"), bd("0.40")),
                    Map.of(CompanionSkill.IMAGE_OBSERVATION, 15L,
                           CompanionSkill.EMOJI_CREATION, 14L),
                    "演示营养让伙伴练习了观察与表情表达。");
            default -> PictureNutrition.demo(48L,
                    new TraitDelta(bd("0.50"), bd("0.10"), bd("0.20"), bd("0.40"), bd("0.80")),
                    Map.of(CompanionSkill.IMAGE_OBSERVATION, 16L,
                           CompanionSkill.IMAGE_FUSION, 10L),
                    "演示营养让伙伴练习了观察与组合想象。");
        };
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
```

Create `src/test/java/com/li/lipicturecloud/infrastructure/companion/DemoPictureNutritionAdapterTest.java`; call each profile twice and assert equality, `contentUnderstood=false`, and no dependency on the wall clock.

- [ ] **Step 6: Implement view records and the single assembler**

Create the seven view records exactly as listed in this task's Interfaces block. `CompanionViewAssembler` owns all domain-to-transport mapping:

Annotate `CompanionViewAssembler` with `@Component` and use constructor injection for `CompanionBalance` and `PictureNutritionAnalyzer`; there must be exactly one application-wide assembler bean.

```java
public CompanionView companion(Companion value) {
    List<CompanionSkillView> skills = Arrays.stream(CompanionSkill.values())
            .map(skill -> {
                long experience = value.skillExperience().getOrDefault(skill, 0L);
                return new CompanionSkillView(skill.name(), experience,
                        balance.skillLevelFor(experience),
                        balance.nextSkillLevelExperience(experience));
            })
            .toList();
    return new CompanionView(value.id(), value.lifeExperience(), value.level(),
            value.lifeStage().name(), balance.totalExperienceForLevel(value.level()),
            balance.nextLevelExperience(value.lifeExperience()),
            traits(value.traits()), skills, value.balanceVersion(), value.revision());
}

public FeedPictureResult feedResult(GrowthRecord record) {
    String outcome = record.eventType() == GrowthEventType.PICTURE_FED
            ? "GROWN" : "FAMILIARITY";
    return new FeedPictureResult(outcome, record.correlationId(),
            companion(record.companionAfter()), growth(record));
}
```

`GrowthRecordView growth(GrowthRecord record)` maps enum skill keys to their `name()`, uses the stored after-snapshot, and maps `contentUnderstood` from the immutable growth fact. `NutritionStatusView nutritionStatus()` returns exactly:

```java
new NutritionStatusView(
        analyzer.mode().name(),
        analyzer.contentUnderstood(),
        "仅根据图片 ID 选择固定营养档案，未读取图片内容，也未调用视觉模型。");
```

- [ ] **Step 7: Implement reservation states and short transactions**

Define:

```java
public record FeedReservation(Kind kind, FeedingRun run, FeedPictureResult replay) {
    public enum Kind { STARTED, REPLAY, IN_PROGRESS, REJECTED }
    public static FeedReservation started(FeedingRun run) {
        return new FeedReservation(Kind.STARTED, run, null);
    }
    public static FeedReservation replay(FeedingRun run, FeedPictureResult result) {
        return new FeedReservation(Kind.REPLAY, run, result);
    }
    public static FeedReservation inProgress(FeedingRun run) {
        return new FeedReservation(Kind.IN_PROGRESS, run, null);
    }
    public static FeedReservation rejected(FeedingRun run) {
        return new FeedReservation(Kind.REJECTED, run, null);
    }
}
```

Annotate `CompanionFeedingCoordinator` with `@Service` and use constructor injection. Keep `reserve`, `reject`, `fail`, and `complete` public so Spring invokes them through the transactional proxy when `CompanionLifeService` calls the coordinator bean; do not replace those external calls with coordinator self-invocation, which would bypass `REQUIRES_NEW` and rollback semantics.

`CompanionFeedingCoordinator.reserve(Companion companion, AuthorizationSubject subject, long pictureId, String idempotencyKey, String fingerprint, String correlationId, NutritionMode mode, boolean contentUnderstood)` is `@Transactional(propagation = REQUIRES_NEW)`. It:

1. Loads `(companionId, key)`.
2. Attempts `runRepository.insert(FeedingRun.processing(companion.id(), subject.userId(), pictureId, idempotencyKey, fingerprint, correlationId, mode, contentUnderstood, clock.instant()))` when absent; on `DuplicateKeyException`, reloads once.
3. Rejects a different picture or fingerprint with `PARAMS_ERROR` and `幂等键已用于另一张图片`.
4. Rebuilds a `COMPLETED` result from `findByFeedingRunId`.
5. Returns the stored `REJECTED` run unchanged.
6. Restarts `FAILED`, or `PROCESSING` older than `now - processingTimeout`, with compare-and-set; reloads after a lost CAS.
7. Returns `IN_PROGRESS` for a fresh `PROCESSING` run.

Use SHA-256 over the UTF-8 string `pictureId=<decimal-id>` as the request fingerprint. Use `UUID.randomUUID().toString()` once per newly observed client key as its correlation ID.

`reject(FeedingRun run, String safeCode, String safeMessage)` and `fail(FeedingRun run, String safeCode, String safeMessage)` use `REQUIRES_NEW` and compare-and-set the run. If another transaction already changed it, reload and accept only the already-requested terminal status; otherwise throw `喂养运行状态已变化，请重试`.

- [ ] **Step 8: Implement atomic completion with caps and optimistic revision**

`complete(FeedingRun run, PictureNutrition nutrition)` is `@Transactional(rollbackFor = Exception.class)` and performs this exact order:

```java
Companion locked = companionRepository.findByOwnerIdForUpdate(run.subjectId())
        .filter(value -> Objects.equals(value.id(), run.companionId()))
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "请先唤醒伙伴"));
Instant now = clock.instant();
boolean repeated = growthRepository.hasFullFeed(locked.id(), run.pictureId());
long today = growthRepository.sumLifeExperienceSince(
        locked.id(), balance.startOfDay(now));
long repeatTotal = growthRepository.sumRevisitExperience(locked.id(), run.pictureId());
FeedingGrowth growth = locked.feed(nutrition,
        new FeedingContext(repeated, today, repeatTotal), balance);

if (!companionRepository.save(growth.companionAfter(), locked.revision())) {
    throw new BusinessException(ErrorCode.OPERATION_ERROR, "伙伴状态已变化，请重试");
}
GrowthRecord record = growthRepository.append(GrowthRecord.from(
        run.id(), locked.id(), run.pictureId(), growth,
        run.nutritionMode(), run.contentUnderstood(),
        run.idempotencyKey(), run.correlationId(), now));
if (!runRepository.complete(run.id(), run.revision(), record.id(), now)) {
    throw new BusinessException(ErrorCode.OPERATION_ERROR, "喂养运行状态已变化，请重试");
}
return assembler.feedResult(record);
```

The transaction rollback guarantees a failed snapshot update, skill upsert, growth insert, or run completion leaves no partial growth. Add a coordinator test whose mocked `Clock` returns one instant just before Asia/Shanghai midnight and later instants just after it; verify `clock.instant()` is called once and that the daily query boundary, growth `createdTime`, and run completion all derive from that first instant. This prevents one feed from being counted against one day while being persisted in the next.

- [ ] **Step 9: Implement the public application workflow**

Annotate `CompanionLifeService` with:

```java
@Service
@ConditionalOnProperty(prefix = "app.companion", name = "enabled",
        havingValue = "true", matchIfMissing = true)
```

Build one read template in the `CompanionLifeService` constructor from the injected `PlatformTransactionManager`:

```java
this.homeReadTransaction = new TransactionTemplate(transactionManager);
this.homeReadTransaction.setReadOnly(true);
this.homeReadTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
```

Implement `home` as `homeReadTransaction.execute(status -> readHome(subject))`. `readHome` loads only `subject.userId()`, then the aggregate plus its skills and at most 20 recent growth records inside that single repeatable-read snapshot. Implement `awaken` by calling race-safe `createIfAbsent` first and then `home(subject)`; because `home` uses an explicit template, self-invocation cannot bypass the read transaction. Do not wrap the pre-insert read/unique-key recovery in the same repeatable-read snapshot, or a losing concurrent awakener may be unable to see the winning row.

In `CompanionLifeServiceTest`, construct this service with a real H2 `DataSourceTransactionManager`. Make the mocked companion and growth repositories answer by asserting `TransactionSynchronizationManager.isActualTransactionActive()`, `isCurrentTransactionReadOnly()`, and `getCurrentTransactionIsolationLevel() == Connection.TRANSACTION_REPEATABLE_READ`; then return the companion/history fixtures. This proves every query used to assemble home is inside the explicit snapshot boundary.

Validate feed keys with `^[a-z0-9_-]{16,64}$`; reject uppercase rather than silently normalizing it, and add an application test for that boundary. The workflow order is:

```java
if (!properties.isFeedingEnabled()) {
    throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "伙伴喂养已暂停");
}
if (command.pictureId() <= 0 || command.idempotencyKey() == null
        || !command.idempotencyKey().matches("^[a-z0-9_-]{16,64}$")) {
    throw new BusinessException(ErrorCode.PARAMS_ERROR, "喂养请求标识不合法");
}
Companion companion = companionRepository.findByOwnerId(command.subject().userId())
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "请先唤醒伙伴"));
String fingerprint = fingerprint(command.pictureId());
FeedReservation reservation = coordinator.reserve(
        companion, command.subject(), command.pictureId(), command.idempotencyKey(),
        fingerprint, UUID.randomUUID().toString(), analyzer.mode(),
        analyzer.contentUnderstood());

try {
    authorization.checkForUser(PICTURE_VIEW, command.pictureId(), command.subject().userId());
} catch (BusinessException error) {
    if (error.getCode() == ErrorCode.NOT_FOUND_ERROR.getCode()
            || error.getCode() == ErrorCode.NO_AUTH_ERROR.getCode()) {
        if (reservation.kind() == FeedReservation.Kind.STARTED) {
            coordinator.reject(reservation.run(), "PICTURE_UNAVAILABLE", "图片不可用或无权访问");
        }
        log.warn("companion_feed_denied correlationId={} subjectId={} pictureId={} reason=PICTURE_UNAVAILABLE",
                reservation.run().correlationId(), command.subject().userId(), command.pictureId());
        throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "图片不可用或无权访问");
    }
    if (error.getCode() == ErrorCode.NOT_LOGIN_ERROR.getCode()) {
        failAuthorizationCheckIfStarted(reservation, command, error.getClass());
        throw error;
    }
    failAuthorizationCheckIfStarted(reservation, command, error.getClass());
    throw new BusinessException(ErrorCode.OPERATION_ERROR, "暂时无法校验图片访问权限，请重试");
} catch (RuntimeException error) {
    failAuthorizationCheckIfStarted(reservation, command, error.getClass());
    throw new BusinessException(ErrorCode.OPERATION_ERROR, "暂时无法校验图片访问权限，请重试");
}
```

Implement the helper exactly so it never mutates a completed/rejected run and never logs an exception message:

```java
private void failAuthorizationCheckIfStarted(
        FeedReservation reservation, FeedPictureCommand command,
        Class<?> exceptionType) {
    if (reservation.kind() != FeedReservation.Kind.STARTED) return;
    coordinator.fail(reservation.run(), "AUTHORIZATION_CHECK_FAILED",
            "暂时无法校验图片访问权限，请重试");
    log.warn("companion_feed_authorization_failed correlationId={} subjectId={} pictureId={} exceptionType={}",
            reservation.run().correlationId(), command.subject().userId(),
            command.pictureId(), exceptionType.getName());
}
```

Add an orchestration test for this path so an authorization infrastructure failure cannot strand a fresh run in `PROCESSING` for five minutes.

After authorization: return `reservation.replay()` for `REPLAY`; for `REJECTED`, throw `BusinessException(ErrorCode.NO_AUTH_ERROR, reservation.run().safeErrorMessage())`; for `IN_PROGRESS`, throw `BusinessException(ErrorCode.OPERATION_ERROR, "这次喂养还在消化中，请稍后重试")`; only `STARTED` calls `analyzer.analyze(new AuthorizedPictureRef(command.subject(), command.pictureId()))` and `coordinator.complete(reservation.run(), nutrition)`.

On any analyzer exception, persist `NUTRITION_FAILED` with `本次没有消化成功，图片未被消耗`, log only correlation/subject/picture and the exception class, then throw that safe message. On completion failure, mark a still-processing run `FAILED` with `FEED_COMMIT_FAILED`, then propagate a safe `OPERATION_ERROR`. Do not put `error.getMessage()` from a third party into logs, rows, or responses.

- [ ] **Step 10: Prove two distinct concurrent requests cannot both receive full nutrition**

Create `CompanionFeedingIntegrationTest` with the real Spring transaction proxy and H2 repositories:

```java
@SpringBootTest
@ActiveProfiles("test")
class CompanionFeedingIntegrationTest {
    @Autowired CompanionRepository companionRepository;
    @Autowired FeedingRunRepository runRepository;
    @Autowired GrowthRecordRepository growthRepository;
    @Autowired CompanionFeedingCoordinator coordinator;
    @Autowired CompanionLife companionLife;
    @Autowired PictureNutritionAnalyzer analyzer;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void concurrentDistinctKeysForOnePictureBecomeFullThenRevisit() throws Exception {
        Companion companion = companionRepository.createIfAbsent(698L, CompanionBalance.v1());
        Instant now = Instant.parse("2026-08-11T08:00:00Z");
        FeedingRun leftRun = runRepository.insert(run(companion, "feed-concurrent-left", "11111111-1111-4111-8111-111111111111", now));
        FeedingRun rightRun = runRepository.insert(run(companion, "feed-concurrent-right", "22222222-2222-4222-8222-222222222222", now));
        PictureNutrition nutrition = PictureNutrition.demo(42L,
                new TraitDelta(bd("0.60"), bd("0.40"), bd("0"), bd("0.20"), bd("0.30")),
                Map.of(CompanionSkill.STORY_CREATION, 12L), "演示营养");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<FeedPictureResult> left = pool.submit(() -> {
                start.await();
                return coordinator.complete(leftRun, nutrition);
            });
            Future<FeedPictureResult> right = pool.submit(() -> {
                start.await();
                return coordinator.complete(rightRun, nutrition);
            });
            start.countDown();
            Set<String> outcomes = Set.of(
                    left.get(10, TimeUnit.SECONDS).outcome(),
                    right.get(10, TimeUnit.SECONDS).outcome());
            assertThat(outcomes).containsExactlyInAnyOrder("GROWN", "FAMILIARITY");

            Companion reloaded = companionRepository.findByOwnerId(698L).orElseThrow();
            assertThat(reloaded.lifeExperience()).isEqualTo(43L);
            assertThat(reloaded.skillExperience())
                    .containsEntry(CompanionSkill.STORY_CREATION, 12L);
            assertThat(growthRepository.findRecent(companion.id(), 10)).hasSize(2);
        } finally {
            pool.shutdownNow();
            jdbcTemplate.update("DELETE FROM companion_growth_record WHERE companionId = ?", companion.id());
            jdbcTemplate.update("DELETE FROM companion_feed_run WHERE companionId = ?", companion.id());
            jdbcTemplate.update("DELETE FROM companion_skill WHERE companionId = ?", companion.id());
            jdbcTemplate.update("DELETE FROM companion WHERE id = ?", companion.id());
        }
    }

    private FeedingRun run(Companion companion, String key, String correlation, Instant now) {
        return FeedingRun.processing(companion.id(), 698L, 102L, key,
                sha256("pictureId=102"), correlation,
                NutritionMode.DEMO_DETERMINISTIC, false, now);
    }
}
```

The successful construction of this `@SpringBootTest` with all six autowired slice beans is also the bean-wiring assertion for the three `@Repository` Adapters, proxied coordinator, service, assembler, and demo analyzer. Use the same concrete `bd(String)` and `sha256(String)` helpers already defined in Task 3. The two keys satisfy the 16-character minimum. H2 must show one `PICTURE_FED`, one `PICTURE_REVISITED`, two completed runs, and revision `2`.

Add a second real-transaction case with subject `699` to prove rollback on the final run CAS rather than merely trusting annotations:

```java
@Test
void staleRunRevisionRollsBackSnapshotSkillsAndGrowth() {
    Companion companion = companionRepository.createIfAbsent(699L, CompanionBalance.v1());
    Instant now = Instant.parse("2026-08-11T08:00:00Z");
    FeedingRun stale = runRepository.insert(FeedingRun.processing(
            companion.id(), 699L, 102L, "feed-stale-run-0001",
            sha256("pictureId=102"), "33333333-3333-4333-8333-333333333333",
            NutritionMode.DEMO_DETERMINISTIC, false, now));
    assertThat(runRepository.restart(stale.id(), stale.revision(), now.plusSeconds(1))).isTrue();

    assertThatThrownBy(() -> coordinator.complete(stale,
            PictureNutrition.demo(42L, TraitDelta.zero(),
                    Map.of(CompanionSkill.STORY_CREATION, 12L), "演示营养")))
            .isInstanceOf(BusinessException.class)
            .hasMessage("喂养运行状态已变化，请重试");

    Companion unchanged = companionRepository.findByOwnerId(699L).orElseThrow();
    assertThat(unchanged.lifeExperience()).isZero();
    assertThat(unchanged.revision()).isZero();
    assertThat(unchanged.skillExperience().get(CompanionSkill.STORY_CREATION)).isZero();
    assertThat(growthRepository.findRecent(companion.id(), 10)).isEmpty();
    FeedingRun current = runRepository.findByKey(companion.id(), stale.idempotencyKey()).orElseThrow();
    assertThat(current.status()).isEqualTo(FeedingRunStatus.PROCESSING);
    assertThat(current.revision()).isEqualTo(1L);
}
```

Wrap this test in `try/finally` with the same child-first, subject-specific cleanup as the first concurrency case. The failed `runRepository.complete` occurs after aggregate/skill/growth writes inside `coordinator.complete`; all three must disappear while the independently committed restarted run remains.

Add a third H2 case for the most dangerous idempotency race: two threads call `coordinator.reserve` concurrently with subject `697`, picture `102`, key `feed-same-key-0001`, the same fingerprint/mode/disclosure, but different proposed correlation IDs. Release them with one latch, then assert the returned kinds contain exactly one `STARTED` and one `IN_PROGRESS`, both results reference the same persisted run ID and stored correlation ID, and SQL count for `(companionId, idempotencyKey)` is `1`. Clean up that subject in `finally`. This test must exercise absent-read → competing insert → `DuplicateKeyException` → reload on real H2; do not replace it with mocks or a sequential call.

- [ ] **Step 11: Run application tests and domain coverage**

```powershell
.\scripts\mvnw-java21.ps1 "-Dtest=CompanionLifeServiceTest,CompanionFeedingCoordinatorTest,CompanionFeedingIntegrationTest,DemoPictureNutritionAdapterTest,CompanionTest,FeedingRunTest,CompanionBalancePropertyTest" test
.\scripts\mvnw-java21.ps1 "-Dspring.profiles.active=test" verify
```

Expected: authorization normalization, exact replay, failed-run retry, daily/repeat caps, atomic completion, and demo determinism pass; companion-domain branch coverage remains at least 85%.

- [ ] **Step 12: Commit the application workflow**

```powershell
git add src/main/java/com/li/lipicturecloud/config/CompanionConfiguration.java src/main/java/com/li/lipicturecloud/config/CompanionFeatureProperties.java src/main/java/com/li/lipicturecloud/application/companion src/main/java/com/li/lipicturecloud/infrastructure/companion src/test/java/com/li/lipicturecloud/application/companion src/test/java/com/li/lipicturecloud/infrastructure/companion/DemoPictureNutritionAdapterTest.java
git commit -m "feat: orchestrate authorized companion feeding"
```

---

### Task 5: Expose the Authenticated Companion HTTP Surface

**Files:**
- Create: `src/main/java/com/li/lipicturecloud/model/dto/companion/CompanionFeedRequest.java`
- Create: `src/main/java/com/li/lipicturecloud/controller/CompanionController.java`
- Create: `src/test/java/com/li/lipicturecloud/controller/CompanionControllerTest.java`

**Interfaces:**
- Consumes: `CompanionLife`, `UserService.getLoginUserEntity(HttpServletRequest)`, `UserService.isAdmin(User)`, and `AuthorizationSubject` factories.
- Produces: `GET /companion/me`, `POST /companion/awaken`, and `POST /companion/feed`; no endpoint accepts a subject/user ID.

- [ ] **Step 1: Write failing controller tests**

Create a standalone MockMvc test with `GlobalExceptionHandler`:

```java
class CompanionControllerTest {
    private MockMvc mockMvc;
    private CompanionLife companionLife;
    private UserService userService;
    private AuthorizationSubject subject;

    @BeforeEach
    void setUp() {
        companionLife = mock(CompanionLife.class);
        userService = mock(UserService.class);
        User loginUser = new User();
        loginUser.setId(7L);
        when(userService.getLoginUserEntity(any(HttpServletRequest.class))).thenReturn(loginUser);
        when(userService.isAdmin(loginUser)).thenReturn(false);
        subject = AuthorizationSubject.user(7L);
        CompanionController controller = new CompanionController(companionLife, userService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void currentReturnsAnExplicitEmptyHomeWithoutAutoAwakening() throws Exception {
        when(companionLife.home(subject)).thenReturn(new CompanionHomeView(
                null,
                new NutritionStatusView("DEMO_DETERMINISTIC", false,
                        "仅根据图片 ID 选择固定营养档案，未读取图片内容，也未调用视觉模型。"),
                List.of()));

        mockMvc.perform(get("/companion/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.companion").value(nullValue()))
                .andExpect(jsonPath("$.data.nutrition.contentUnderstood").value(false));
        verify(companionLife).home(subject);
        verify(companionLife, never()).awaken(any());
    }

    @Test
    void feedBuildsSubjectFromSessionAndIgnoresClaimedUserField() throws Exception {
        FeedPictureResult result = feedResult();
        when(companionLife.feed(any())).thenReturn(result);

        mockMvc.perform(post("/companion/feed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pictureId":"102",
                                 "idempotencyKey":"6f26d166-0a82-4d9f-8a61-6c21cf2e59d0",
                                 "userId":"999"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.outcome").value("GROWN"));

        ArgumentCaptor<FeedPictureCommand> command = ArgumentCaptor.forClass(FeedPictureCommand.class);
        verify(companionLife).feed(command.capture());
        assertThat(command.getValue().subject()).isEqualTo(subject);
        assertThat(command.getValue().pictureId()).isEqualTo(102L);
    }
}
```

Add a successful awaken case and a feed request containing `{}` expecting HTTP 400 / code `40000`.

Define the controller-test result helper exactly:

```java
private FeedPictureResult feedResult() {
    return new FeedPictureResult(
            "GROWN",
            "fef53056-2d9f-467d-9b1d-1afe9a6638fe",
            null,
            null);
}
```

- [ ] **Step 2: Run the controller test and verify RED**

```powershell
.\scripts\mvnw-java21.ps1 -Dtest=CompanionControllerTest test
```

Expected: compilation fails because the DTO and controller do not exist.

- [ ] **Step 3: Create the narrow request DTO**

```java
package com.li.lipicturecloud.model.dto.companion;

import lombok.Data;

@Data
public class CompanionFeedRequest {
    private Long pictureId;
    private String idempotencyKey;
}
```

Do not add `userId`, `spaceId`, XP, traits, skills, nutrition mode, balance version, or revision.

- [ ] **Step 4: Implement the controller with constructor injection**

```java
@RestController
@RequestMapping(value = "/companion", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "app.companion", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@Tag(name = "图像伙伴", description = "唤醒伙伴并用授权图片获得演示成长")
public class CompanionController {
    private final CompanionLife companionLife;
    private final UserService userService;

    public CompanionController(CompanionLife companionLife, UserService userService) {
        this.companionLife = companionLife;
        this.userService = userService;
    }

    @GetMapping("/me")
    @AuthCheck
    public BaseResponse<CompanionHomeView> home(HttpServletRequest request) {
        return ResultUtils.success(companionLife.home(subject(request)));
    }

    @PostMapping("/awaken")
    @AuthCheck
    public BaseResponse<CompanionHomeView> awaken(HttpServletRequest request) {
        return ResultUtils.success(companionLife.awaken(subject(request)));
    }

    @PostMapping("/feed")
    @AuthCheck
    public BaseResponse<FeedPictureResult> feed(
            @RequestBody CompanionFeedRequest body, HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.getPictureId() == null,
                ErrorCode.PARAMS_ERROR, "请选择要喂养的图片");
        return ResultUtils.success(companionLife.feed(new FeedPictureCommand(
                subject(request), body.getPictureId(), body.getIdempotencyKey())));
    }

    private AuthorizationSubject subject(HttpServletRequest request) {
        User user = userService.getLoginUserEntity(request);
        return userService.isAdmin(user)
                ? AuthorizationSubject.platformAdmin(user.getId())
                : AuthorizationSubject.user(user.getId());
    }
}
```

The method-level `@AuthCheck` provides the standard authentication guard; rebuilding the subject inside the controller ensures request JSON cannot override it.

- [ ] **Step 5: Run controller, application, and authorization tests**

```powershell
.\scripts\mvnw-java21.ps1 "-Dtest=CompanionControllerTest,CompanionLifeServiceTest,CompanionFeedingCoordinatorTest,SpacePermissionInterceptorTest,SpaceAuthorizationManagerTest" test
```

Expected: all selected tests pass; malicious `userId` JSON never appears in the application command, and the existing authorization decision/interceptor tests remain green.

- [ ] **Step 6: Commit the HTTP surface**

```powershell
git add src/main/java/com/li/lipicturecloud/model/dto/companion/CompanionFeedRequest.java src/main/java/com/li/lipicturecloud/controller/CompanionController.java src/test/java/com/li/lipicturecloud/controller/CompanionControllerTest.java
git commit -m "feat: expose companion life endpoints"
```

---

### Task 6: Build the Companion Page and Reliable Feed Retry UX

**Files:**
- Create: `li-picture-cloud-frontend/src/api/companion.js`
- Modify: `li-picture-cloud-frontend/src/api/request.js`
- Modify: `li-picture-cloud-frontend/src/api/picture.js`
- Create: `li-picture-cloud-frontend/src/config/features.js`
- Create: `li-picture-cloud-frontend/src/constants/companion.js`
- Create: `li-picture-cloud-frontend/src/utils/authBootstrap.js`
- Create: `li-picture-cloud-frontend/src/utils/companion.js`
- Create: `li-picture-cloud-frontend/tests/companion.test.mjs`
- Modify: `li-picture-cloud-frontend/src/stores/user.js`
- Modify: `li-picture-cloud-frontend/src/App.vue`
- Modify: `li-picture-cloud-frontend/src/router/index.js`
- Modify: `li-picture-cloud-frontend/src/constants/navigation.js`
- Modify: `li-picture-cloud-frontend/src/components/NavBar.vue`
- Modify: `li-picture-cloud-frontend/tests/navigation.test.mjs`
- Modify: `li-picture-cloud-frontend/tests/responsiveViews.test.mjs`
- Create: `li-picture-cloud-frontend/src/components/companion/CompanionStats.vue`
- Create: `li-picture-cloud-frontend/src/components/companion/CompanionPicturePicker.vue`
- Create: `li-picture-cloud-frontend/src/components/companion/CompanionGrowthTimeline.vue`
- Create: `li-picture-cloud-frontend/src/views/CompanionView.vue`

**Interfaces:**
- Consumes: Task 5's exact HTTP surface, `listSpaceVOByPage`, the existing permission-checked `/picture/list/page/vo` endpoint, Pinia authentication state, and JSON-string IDs.
- Produces:

```javascript
getCompanionHome()                    // GET  /companion/me
awakenCompanion()                     // POST /companion/awaken
feedCompanion({ pictureId, idempotencyKey }) // POST /companion/feed
listPictureVOByPageUncached(data)     // POST /picture/list/page/vo
```

The page never calculates growth. It only renders server-provided totals and deltas.

- [ ] **Step 1: Write failing pure frontend behavior tests**

Create `tests/companion.test.mjs`:

```javascript
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  applyFeedResult,
  beginFeedAttempt,
  buildCompanionPictureQuery,
  describeTrait,
  selectOldestPrivateSpace,
  shouldRetrySameFeedKey,
  traitPosition
} from '../src/utils/companion.js'
import {
  createAuthSessionGate,
  createSingleFlightLoader,
  isTerminalAuthFailure
} from '../src/utils/authBootstrap.js'

test('keeps one idempotency key through ambiguous retries', () => {
  const first = beginFeedAttempt('102', null, () => 'feed-key-0000001')
  const retry = beginFeedAttempt('102', first, () => 'feed-key-0000002')
  const changed = beginFeedAttempt('103', first, () => 'feed-key-0000003')
  assert.deepEqual(first, { pictureId: '102', idempotencyKey: 'feed-key-0000001' })
  assert.equal(retry, first)
  assert.equal(changed.idempotencyKey, 'feed-key-0000003')
})

test('applies the server result once and de-duplicates history', () => {
  const home = { companion: { revision: '0' }, recentGrowth: [] }
  const result = {
    companion: { revision: '1', lifeExperience: '42' },
    growth: { id: '31', eventType: 'PICTURE_FED', createdTime: '2026-08-11T08:00:00Z' }
  }
  const once = applyFeedResult(home, result)
  const twice = applyFeedResult(once, result)
  assert.equal(twice.companion.revision, '1')
  assert.deepEqual(twice.recentGrowth.map(item => item.id), ['31'])
})

test('an old idempotent replay cannot roll back the visible companion or timeline', () => {
  const home = {
    companion: { revision: '2', lifeExperience: '43' },
    recentGrowth: [
      { id: '32', createdTime: '2026-08-11T08:01:00Z' },
      { id: '31', createdTime: '2026-08-11T08:00:00Z' }
    ]
  }
  const replay = {
    companion: { revision: '1', lifeExperience: '42' },
    growth: { id: '31', createdTime: '2026-08-11T08:00:00Z' }
  }
  const merged = applyFeedResult(home, replay)
  assert.equal(merged.companion.revision, '2')
  assert.equal(merged.companion.lifeExperience, '43')
  assert.deepEqual(merged.recentGrowth.map(item => item.id), ['32', '31'])
})

test('orders growth instants by time even when fractional precision differs', () => {
  const home = {
    companion: { revision: '2' },
    recentGrowth: [
      { id: '31', createdTime: '2026-08-11T08:00:00Z' }
    ]
  }
  const result = {
    companion: { revision: '3' },
    growth: { id: '32', createdTime: '2026-08-11T08:00:00.500Z' }
  }
  assert.deepEqual(
    applyFeedResult(home, result).recentGrowth.map(item => item.id),
    ['32', '31']
  )
})

test('selects the oldest owned private space and builds an authorized picture query', () => {
  const spaces = [
    { id: '12', userId: '7', spaceType: 0, createTime: '2026-01-01T00:00:00.500Z' },
    { id: '15', userId: '7', spaceType: 0, createTime: '2026-01-01T00:00:00Z' },
    { id: '11', userId: '7', spaceType: 0, createTime: '2026-01-01T00:00:00Z' },
    { id: '13', userId: '8', spaceType: 0, createTime: '2025-01-01T00:00:00Z' },
    { id: '14', userId: '7', spaceType: 1, createTime: '2025-01-01T00:00:00Z' }
  ]
  assert.equal(selectOldestPrivateSpace(spaces, '7').id, '11')
  assert.deepEqual(buildCompanionPictureQuery('11'), {
    current: 1, pageSize: 12, spaceId: '11',
    sortField: 'createTime', sortOrder: 'descend'
  })
})

test('describes bipolar traits without presenting a maximize score', () => {
  assert.equal(describeTrait(0, { negative: '谨慎', positive: '好奇' }), '保持中性')
  assert.equal(describeTrait(24, { negative: '谨慎', positive: '好奇' }), '略偏好奇')
  assert.equal(describeTrait(-72, { negative: '谨慎', positive: '好奇' }), '明显偏谨慎')
  assert.equal(traitPosition(-100), 0)
  assert.equal(traitPosition(0), 50)
  assert.equal(traitPosition(100), 100)
})

test('retains a feed key only when retrying can recover the same run', () => {
  assert.equal(shouldRetrySameFeedKey({}), true)
  assert.equal(shouldRetrySameFeedKey({ status: 500 }), true)
  assert.equal(shouldRetrySameFeedKey({ status: 429 }), true)
  assert.equal(shouldRetrySameFeedKey({ status: 403 }), false)
  assert.equal(shouldRetrySameFeedKey({ status: 400 }), false)
})

test('auth loading is single-flight and retries a transient failure', async () => {
  let calls = 0
  const load = createSingleFlightLoader(async () => {
    calls += 1
    if (calls === 1) throw Object.assign(new Error('temporary'), { status: 500 })
    return { id: '7' }
  })
  const first = load()
  const concurrent = load()
  assert.equal(first, concurrent)
  await assert.rejects(first)
  assert.deepEqual(await load(), { id: '7' })
  assert.equal(calls, 2)
  assert.equal(isTerminalAuthFailure({ status: 401 }), true)
  assert.equal(isTerminalAuthFailure({ status: 403 }), true)
  assert.equal(isTerminalAuthFailure({ status: 500 }), false)
  assert.equal(isTerminalAuthFailure({}), false)
})

test('a late bootstrap result cannot clobber an explicit login or logout', async () => {
  const gate = createAuthSessionGate()
  let settleBootstrap
  const bootstrapResult = new Promise(resolve => { settleBootstrap = resolve })
  const captured = gate.capture()
  let currentUser = null

  currentUser = { id: '7' }
  gate.invalidate()
  settleBootstrap(null)
  const staleResult = await bootstrapResult
  if (gate.isCurrent(captured)) currentUser = staleResult
  assert.deepEqual(currentUser, { id: '7' })

  const logoutCapture = gate.capture()
  currentUser = null
  gate.invalidate()
  if (gate.isCurrent(logoutCapture)) currentUser = { id: '7' }
  assert.equal(currentUser, null)
})
```

- [ ] **Step 2: Run the focused Node test and verify RED**

```powershell
Set-Location li-picture-cloud-frontend
node --test --test-name-pattern="idempotency|server result|old idempotent replay|fractional precision|oldest owned|bipolar traits|retains a feed key|auth loading|late bootstrap" tests/companion.test.mjs
```

Expected: module-not-found or missing-export failures.

- [ ] **Step 3: Implement companion constants and pure helpers**

Create the canonical UI metadata:

```javascript
export const LIFE_STAGE = Object.freeze({
  LIGHT: { label: '光点', description: '刚刚被唤醒，正在形成自己的轮廓。' },
  SEEDLING: { label: '幼体', description: '已经积累了一些稳定倾向与技能。' },
  COMPANION: { label: '伙伴', description: '成长为能够长期陪伴与共同创作的形态。' }
})

export const TRAIT_AXES = Object.freeze([
  { key: 'curiosity', negative: '谨慎', positive: '好奇' },
  { key: 'enthusiasm', negative: '克制', positive: '热情' },
  { key: 'playfulness', negative: '沉稳', positive: '淘气' },
  { key: 'empathy', negative: '理性', positive: '共情' },
  { key: 'creativity', negative: '秩序', positive: '创造' }
])

export const SKILL_LABEL = Object.freeze({
  IMAGE_OBSERVATION: '图片观察',
  STORY_CREATION: '故事创作',
  EMOJI_CREATION: '表情制作',
  IMAGE_FUSION: '图片融合',
  GALLERY_SEARCH: '图库搜索'
})
```

Implement helpers exactly:

```javascript
export function beginFeedAttempt(pictureId, currentAttempt, keyFactory = createIdempotencyKey) {
  const normalized = String(pictureId)
  if (currentAttempt?.pictureId === normalized) return currentAttempt
  return { pictureId: normalized, idempotencyKey: keyFactory() }
}

export function createIdempotencyKey() {
  return (globalThis.crypto?.randomUUID?.()
    || `feed-${Date.now()}-${Math.random().toString(36).slice(2, 14)}`).toLowerCase()
}

export function applyFeedResult(home, result) {
  const currentRevision = BigInt(String(home.companion?.revision ?? -1))
  const resultRevision = BigInt(String(result.companion.revision))
  const mergedGrowth = [
    result.growth,
    ...(home.recentGrowth || []).filter(item => String(item.id) !== String(result.growth.id))
  ].sort((left, right) => {
    const byTime = Date.parse(right.createdTime) - Date.parse(left.createdTime)
    if (byTime !== 0) return byTime
    const leftId = BigInt(String(left.id))
    const rightId = BigInt(String(right.id))
    return leftId === rightId ? 0 : leftId > rightId ? -1 : 1
  })
  return {
    ...home,
    companion: resultRevision >= currentRevision ? result.companion : home.companion,
    recentGrowth: mergedGrowth.slice(0, 20)
  }
}

export function selectOldestPrivateSpace(spaces = [], userId) {
  return spaces
    .filter(space => space.spaceType === 0 && String(space.userId) === String(userId))
    .toSorted((left, right) => {
      const byTime = Date.parse(left.createTime) - Date.parse(right.createTime)
      if (byTime !== 0) return byTime
      const leftId = BigInt(String(left.id))
      const rightId = BigInt(String(right.id))
      return leftId === rightId ? 0 : leftId < rightId ? -1 : 1
    })[0] || null
}

export function buildCompanionPictureQuery(spaceId) {
  return { current: 1, pageSize: 12, spaceId: String(spaceId),
    sortField: 'createTime', sortOrder: 'descend' }
}

export function traitPosition(value) {
  return Math.min(100, Math.max(0, (Number(value) + 100) / 2))
}

export function describeTrait(value, axis) {
  const amount = Number(value)
  if (Math.abs(amount) < 10) return '保持中性'
  const direction = amount > 0 ? axis.positive : axis.negative
  return `${Math.abs(amount) >= 60 ? '明显' : '略'}偏${direction}`
}

export function shouldRetrySameFeedKey(error) {
  return ![400, 401, 403, 404].includes(Number(error?.status))
}
```

Also export `formatSignedDelta(value)` using two decimals and an explicit `+` for positive changes.

Create `src/utils/authBootstrap.js`:

```javascript
export function isTerminalAuthFailure(error) {
  return [401, 403].includes(Number(error?.status))
}

export function createSingleFlightLoader(loader) {
  let inFlight = null
  return function load() {
    if (inFlight) return inFlight
    inFlight = Promise.resolve()
      .then(loader)
      .finally(() => { inFlight = null })
    return inFlight
  }
}

export function createAuthSessionGate() {
  let generation = 0
  return {
    capture: () => generation,
    invalidate: () => { generation += 1 },
    isCurrent: captured => captured === generation
  }
}
```

- [ ] **Step 4: Add the API wrappers**

Create `src/api/companion.js`:

```javascript
import request from './request'

export const getCompanionHome = () => request.get('/companion/me')
export const awakenCompanion = () => request.post('/companion/awaken')
export const feedCompanion = data => request.post('/companion/feed', data)
```

In both rejection branches of `src/api/request.js`, reject an `Error` that preserves `status` and the backend envelope `code`:

```javascript
function toApiError(message, status, code) {
  const error = new Error(message || '请求失败')
  error.status = status
  error.code = code
  return error
}
```

For a nonzero success-envelope response use `toApiError(body.message, res.status, body.code)`; for Axios rejection use `toApiError(msg, err.response?.status, err.response?.data?.code)`. Do not expose response bodies or headers on the error object.

Add to `src/api/picture.js`:

```javascript
/** 分页获取当前主体有权查看的空间图片，不使用 Redis 列表缓存。 */
export function listPictureVOByPageUncached(data) {
  return request.post('/picture/list/page/vo', data)
}
```

The companion page uses the uncached route so picture listing does not depend on Redis cache state; the backend endpoint already resolves the space and enforces `picture:view`. The real login flow still uses the repository's Sa-Token Redis DAO, so local E2E requires Redis and CI supplies a service container.

Create `src/config/features.js`:

```javascript
export const COMPANION_UI_ENABLED = import.meta.env.DEV
  || import.meta.env.VITE_COMPANION_ENABLED === 'true'
```

Development and E2E therefore show the feature. A production build hides both its route and navigation entry unless `VITE_COMPANION_ENABLED=true`; enabling a release requires both that build flag and backend `COMPANION_ENABLED=true`.

- [ ] **Step 5: Make authentication bootstrap single-flight and route-aware**

Update the user store:

```javascript
const authReady = ref(false)
const authBootstrapError = ref(null)
const loadCurrentUserOnce = createSingleFlightLoader(getCurrentUser)
const authSessionGate = createAuthSessionGate()

async function ensureCurrentUser() {
  if (authReady.value) return currentUser.value
  const generation = authSessionGate.capture()
  try {
    const user = await loadCurrentUserOnce()
    if (!authSessionGate.isCurrent(generation)) return currentUser.value
    currentUser.value = user
    authReady.value = true
    authBootstrapError.value = null
    return user
  } catch (error) {
    if (!authSessionGate.isCurrent(generation)) return currentUser.value
    if (isTerminalAuthFailure(error)) {
      currentUser.value = null
      authReady.value = true
      authBootstrapError.value = null
      return null
    }
    authReady.value = false
    authBootstrapError.value = error
    throw error
  }
}
```

Import `createAuthSessionGate`, `createSingleFlightLoader`, and `isTerminalAuthFailure` from `@/utils/authBootstrap`.

Make `fetchCurrentUser()` delegate to `ensureCurrentUser()`. Call `authSessionGate.invalidate()`, set `authReady=true`, and clear `authBootstrapError` after successful login and after logout; this prevents an older bootstrap response from overwriting a newer explicit login/logout. Export `authReady`, `authBootstrapError`, and `ensureCurrentUser`. Change `App.vue` to call `userStore.ensureCurrentUser().catch(() => {})`; the store retains the safe retry state, so this fire-and-forget bootstrap does not create an unhandled promise rejection.

Add the route only when `COMPANION_UI_ENABLED` is true, then add metadata and a guard:

```javascript
if (COMPANION_UI_ENABLED) {
  routes.push({
    path: '/companion',
    name: 'companion',
    component: () => import('@/views/CompanionView.vue'),
    meta: { requiresAuth: true }
  })
}

router.beforeEach(async to => {
  if (!to.meta.requiresAuth) return true
  const userStore = useUserStore()
  try {
    await userStore.ensureCurrentUser()
  } catch {
    return true
  }
  if (userStore.isLoggedIn) return true
  return { name: 'login', query: { redirect: to.fullPath } }
})
```

Import `useUserStore` and `COMPANION_UI_ENABLED` in the router. Pinia is installed before the router in `main.js`, so the guard receives the active store. A transient network/5xx failure is allowed through only so `CompanionView` can render a retryable authentication-bootstrap error; an explicit 401/403 still redirects to login. Do not copy the synchronous `if (!isLoggedIn) replace('/login')` pattern into `CompanionView`.

- [ ] **Step 6: Add authenticated navigation and contract assertions**

Extend `buildNavigationGroups` to accept `companionEnabled = true`; insert this workspace item after `我的空间` only when that flag is true:

```javascript
{ label: '我的伙伴', to: '/companion' }
```

Import `COMPANION_UI_ENABLED` in `NavBar.vue` and pass it as `companionEnabled` wherever the component builds the shared desktop/mobile navigation groups.

Extend `navigation.test.mjs`:

```javascript
assert.equal(user.flatMap(group => group.items).some(item => item.to === '/companion'), true)
assert.equal(buildNavigationGroups({ isLoggedIn: false, isAdmin: false })
  .flatMap(group => group.items).some(item => item.to === '/companion'), false)
assert.equal(buildNavigationGroups({
  isLoggedIn: true, isAdmin: false, companionEnabled: false
}).flatMap(group => group.items).some(item => item.to === '/companion'), false)
```

Extend the `space and AI surfaces define phone layouts` file list in `responsiveViews.test.mjs` with:

```javascript
'views/CompanionView.vue',
'components/companion/CompanionStats.vue',
'components/companion/CompanionPicturePicker.vue',
'components/companion/CompanionGrowthTimeline.vue'
```

- [ ] **Step 7: Build the stats component with natural-language traits**

`CompanionStats.vue` accepts one required `companion` object. Render:

- stage label and description from `LIFE_STAGE`;
- `等级 {{ companion.level }}` and `{{ lifeExperience }} / {{ nextLevelExperience }} 生命经验`;
- a progress bar calculated from `levelStartExperience` and `nextLevelExperience`;
- five bipolar trait rows with negative label, neutral center, positive label, a marker from `traitPosition`, and text from `describeTrait`;
- five skills with server-provided level/experience/next threshold.

Give each rendered skill row the stable locator `data-testid="skill-<skill.code>"`; this is an acceptance seam, not a styling selector.

Use this progress calculation rather than recomputing the backend curve:

```javascript
const lifeProgress = computed(() => {
  const start = Number(props.companion.levelStartExperience)
  const next = Number(props.companion.nextLevelExperience)
  const current = Number(props.companion.lifeExperience)
  return next <= start ? 100 : Math.min(100, Math.max(0, (current - start) / (next - start) * 100))
})
```

Use `aria-valuemin="-100"`, `aria-valuemax="100"`, and the server value for `aria-valuenow`, but do not print the absolute trait number as a score to maximize.

- [ ] **Step 8: Build the accessible single-picture picker**

`CompanionPicturePicker.vue` props are `pictures`, `selectedId`, `loading`, and `disabled`; it emits `select`. Each picture is a real button:

```vue
<button
  v-for="picture in pictures"
  :key="picture.id"
  type="button"
  class="picture-choice"
  :class="{ selected: String(picture.id) === String(selectedId) }"
  :aria-pressed="String(picture.id) === String(selectedId)"
  :disabled="disabled"
  @click="$emit('select', String(picture.id))"
>
  <img :src="picture.thumbnailUrl || picture.url" :alt="picture.name || '图片'" />
  <span>{{ picture.name || '未命名图片' }}</span>
</button>
```

Render explicit loading and empty states. The component does not navigate and never accepts multiple selections.

- [ ] **Step 9: Build the source-light growth timeline**

`CompanionGrowthTimeline.vue` accepts `records`. For each record render event label, `+N 生命经验`, nonzero trait/skill deltas, reason, balance version, nutrition disclosure, and formatted time. Render the source as a link `图片 #<last 6 characters>` to `/picture/:id`; do not render a copied name, thumbnail, URL, tags, or description.

Use `formatSignedDelta` for traits, `SKILL_LABEL` for skill codes, and this persistent badge whenever `contentUnderstood === false`:

```vue
<span class="demo-badge">未进行内容理解</span>
```

Give nonzero trait-delta rows `data-testid="growth-trait-<axis-key>"`. The source must be a real router link whose rendered `href` is `/picture/<full-id>` even though its label shows only the last six characters.

An empty list says `伙伴还没有成长记录，选择一张图片开始第一次喂养。`

- [ ] **Step 10: Compose the full page and preserve ambiguous feed attempts**

`CompanionView.vue` orchestrates the components. If `userStore.authBootstrapError` exists on mount, render `暂时无法确认登录状态` and a `重试` button instead of calling companion APIs. The retry button calls `ensureCurrentUser()` again; on success it loads the home, and on an explicit unauthenticated result it redirects to login. Otherwise call `getCompanionHome()`. If `home.companion` exists, load private sources:

```javascript
const spacesPage = await listSpaceVOByPage({
  current: 1,
  pageSize: 20,
  userId: String(userStore.currentUser.id),
  spaceType: 0,
  sortField: 'createTime',
  sortOrder: 'ascend'
})
privateSpace.value = selectOldestPrivateSpace(spacesPage.records || [], userStore.currentUser.id)
if (privateSpace.value) {
  const picturePage = await listPictureVOByPageUncached(
    buildCompanionPictureQuery(privateSpace.value.id))
  pictures.value = picturePage.records || []
}
```

If the initial home request returns HTTP 404 because the backend feature is disabled, render `伙伴功能暂未开放` and no awaken/feed controls. For other initial failures, render the safe error plus a `重新加载` button. Do not mistake an HTTP failure for the valid `companion: null` awakening state.

Awakening calls `awakenCompanion()`, replaces `home`, then loads sources. Feeding uses:

```javascript
async function submitFeed() {
  if (feedBusy.value || !selectedPictureId.value) return
  feedBusy.value = true
  const previousAttempt = pendingAttempt.value
  const attempt = beginFeedAttempt(selectedPictureId.value, previousAttempt)
  const wasRetry = attempt === previousAttempt
  pendingAttempt.value = attempt
  feedError.value = ''
  try {
    const result = await feedCompanion(pendingAttempt.value)
    home.value = applyFeedResult(home.value, result)
    feedNotice.value = wasRetry
      ? '这次喂养已安全完成，没有重复成长。'
      : result.outcome === 'FAMILIARITY'
        ? '伙伴认出了这张图片，只获得了一点熟悉感。'
        : '伙伴完成了这次喂养。'
    pendingAttempt.value = null
  } catch (error) {
    const retrySameKey = shouldRetrySameFeedKey(error)
    if (!retrySameKey) pendingAttempt.value = null
    feedError.value = error.status == null
      ? '响应不确定，请用同一请求重试这次喂养'
      : error.message || '喂养失败'
  } finally {
    feedBusy.value = false
  }
}
```

Changing the selected picture clears the pending attempt and feed error. An aborted/network, rate-limit, in-progress, or server-failure response keeps the same attempt and changes the button label to `重试这次喂养`; compute that label from `pendingAttempt && feedError`, not from the error alone. An explicit 400/401/403/404 clears the terminal attempt so a later user intent gets a new key. Disable submit until a picture is selected, and disable selection plus submit while a request is running. Keep the synchronous `feedBusy` function guard even though the button is disabled; DOM updates alone do not stop two clicks in the same event-loop turn.

The page has four visible sections:

1. Hero/empty state with `唤醒我的伙伴`.
2. Server-driven banner `演示营养（确定性）` plus `home.nutrition.notice`.
3. Stats and private-picture picker; when no private space exists, link to `/space/create`; when it contains no pictures, link to `/upload`.
4. Growth timeline.

Use a two-column desktop grid and set all four new Vue files to `grid-template-columns: 1fr` at `@media (max-width: 767px)`. Give selectable buttons a visible `:focus-visible` outline and 44px minimum touch target.

- [ ] **Step 11: Run frontend tests, lint, build, and bundle budget**

```powershell
npm test
npm run lint
Remove-Item Env:\VITE_COMPANION_ENABLED -ErrorAction SilentlyContinue
npm run build
npm run check:bundle
$taskDefaultCompanionChunks = Get-ChildItem dist/assets -Filter 'CompanionView-*.js'
if ($taskDefaultCompanionChunks) {
  throw 'default production build must not contain the disabled companion route chunk'
}
$env:VITE_COMPANION_ENABLED = 'true'
try {
  npm run build
  npm run check:bundle
  $taskEnabledCompanionChunks = Get-ChildItem dist/assets -Filter 'CompanionView-*.js'
  if (-not $taskEnabledCompanionChunks) {
    throw 'enabled production build must contain the lazy companion route chunk'
  }
} finally {
  Remove-Item Env:\VITE_COMPANION_ENABLED -ErrorAction SilentlyContinue
}
```

Expected: all Node tests pass and ESLint has zero warnings. The default production build has no companion route chunk; the explicit enabled build contains `CompanionView-*.js` as a lazy chunk; both builds pass the existing bundle budget.

- [ ] **Step 12: Commit the companion page**

```powershell
Set-Location ..
git add li-picture-cloud-frontend/src/api/companion.js li-picture-cloud-frontend/src/api/request.js li-picture-cloud-frontend/src/api/picture.js li-picture-cloud-frontend/src/config/features.js li-picture-cloud-frontend/src/constants/companion.js li-picture-cloud-frontend/src/utils/authBootstrap.js li-picture-cloud-frontend/src/utils/companion.js li-picture-cloud-frontend/tests/companion.test.mjs li-picture-cloud-frontend/src/stores/user.js li-picture-cloud-frontend/src/App.vue li-picture-cloud-frontend/src/router/index.js li-picture-cloud-frontend/src/constants/navigation.js li-picture-cloud-frontend/src/components/NavBar.vue li-picture-cloud-frontend/tests/navigation.test.mjs li-picture-cloud-frontend/tests/responsiveViews.test.mjs li-picture-cloud-frontend/src/components/companion li-picture-cloud-frontend/src/views/CompanionView.vue
git commit -m "feat: add companion growth page"
```

---

### Task 7: Prove the Full Browser Flow and Document the Demo Boundary

**Files:**
- Create: `src/test/resources/application-e2e.yaml`
- Create: `src/test/resources/e2e-schema.sql`
- Create: `src/test/resources/e2e-data.sql`
- Create: `li-picture-cloud-frontend/scripts/start-e2e-backend.mjs`
- Create: `li-picture-cloud-frontend/playwright.config.js`
- Create: `li-picture-cloud-frontend/e2e/companion.spec.js`
- Modify: `li-picture-cloud-frontend/vite.config.js`
- Modify: `li-picture-cloud-frontend/package.json`
- Modify: `li-picture-cloud-frontend/package-lock.json`
- Modify: `li-picture-cloud-frontend/Dockerfile`
- Modify: `li-picture-cloud-frontend/eslint.config.js`
- Modify: `.gitignore`
- Modify: `.github/workflows/ci.yml`
- Modify: `compose.yaml`
- Modify: `.env.example`
- Modify: `src/test/java/com/li/lipicturecloud/deployment/DeploymentArtifactsTest.java`
- Create: `docs/round-19-companion-life-core-guide.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: the real Vite application, real Spring Boot HTTP/controller/application/domain/MyBatis stack, H2 migrations, existing session login, one private space, and one private picture fixture.
- Produces: `npm run test:e2e`, a one-worker Chromium test, CI job `companion-e2e`, and an operator/user guide. No HTTP route is mocked.

- [ ] **Step 1: Install Playwright and declare the browser command**

From the frontend directory run:

```powershell
npm install --save-dev @playwright/test@^1.62.1
```

Add to `package.json` scripts:

```json
"test:e2e": "playwright test"
```

This command updates both `package.json` and `package-lock.json`; do not hand-edit the lockfile.

- [ ] **Step 2: Add the cross-platform backend launcher**

Create `scripts/start-e2e-backend.mjs`:

```javascript
import { spawn } from 'node:child_process'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const repositoryRoot = resolve(here, '../..')
const mavenArguments = [
  '-q',
  '-DskipTests',
  '-Dspring-boot.run.profiles=test,e2e',
  '-Dspring-boot.run.useTestClasspath=true',
  'test-compile',
  'spring-boot:run'
]
const command = process.platform === 'win32' ? 'powershell.exe' : './mvnw'
const commandArguments = process.platform === 'win32'
  ? ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
      resolve(repositoryRoot, 'scripts/mvnw-java21.ps1'), ...mavenArguments]
  : mavenArguments
const child = spawn(command, commandArguments, {
  cwd: repositoryRoot,
  stdio: 'inherit',
  shell: false
})

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => child.kill(signal))
}

child.on('exit', code => process.exit(code ?? 1))
```

- [ ] **Step 3: Write the Playwright config and initially failing real-browser test**

Create `playwright.config.js`:

```javascript
import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://127.0.0.1:15173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  },
  webServer: [
    {
      command: 'node scripts/start-e2e-backend.mjs',
      url: 'http://127.0.0.1:18124/api/v3/api-docs',
      timeout: 180_000,
      reuseExistingServer: false
    },
    {
      command: 'npm run dev -- --host 127.0.0.1 --port 15173 --strictPort',
      url: 'http://127.0.0.1:15173',
      timeout: 60_000,
      reuseExistingServer: false,
      env: {
        VITE_API_PROXY_TARGET: 'http://127.0.0.1:18124',
        VITE_COMPANION_ENABLED: 'true'
      }
    }
  ],
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }]
})
```

Modify `vite.config.js` so the existing proxy keeps its normal default but the isolated browser run can override it:

```javascript
target: process.env.VITE_API_PROXY_TARGET || 'http://localhost:8124'
```

The dedicated ports and `reuseExistingServer: false` make an occupied port fail loudly instead of silently writing E2E data through a developer backend. Keep Playwright retries at zero because this stateful H2 flow has no reset endpoint; a process-level retry would reuse committed growth and invalidate first-feed assertions.

Create `e2e/companion.spec.js`:

```javascript
import { test, expect } from '@playwright/test'

test('awakens a companion and recovers one private-picture feed without double growth', async ({ page }) => {
  const login = await page.request.post('/api/user/login', {
    data: { userAccount: 'companion_e2e', userPassword: 'LocalUser123!' }
  })
  expect(login.ok()).toBeTruthy()

  await page.goto('/companion')
  await expect(page).toHaveURL(/\/companion$/)
  await expect(page.getByText('演示营养（确定性）')).toBeVisible()
  await expect(page.getByText('未读取图片内容，也未调用视觉模型')).toBeVisible()
  await page.getByRole('button', { name: '唤醒我的伙伴' }).click()
  await expect(page.getByText('光点')).toBeVisible()

  await page.getByRole('button', { name: /旅行样片/ }).click()
  const keys = []
  let loseFirstResponse = true
  await page.route('**/api/companion/feed', async route => {
    const payload = route.request().postDataJSON()
    keys.push(payload.idempotencyKey)
    if (loseFirstResponse) {
      loseFirstResponse = false
      const backendResponse = await route.fetch()
      expect(backendResponse.ok()).toBeTruthy()
      await route.abort('failed')
      return
    }
    await route.continue()
  })

  await page.getByRole('button', { name: '喂给伙伴' }).evaluate(button => {
    button.click()
    button.click()
  })
  await expect(page.getByRole('button', { name: '重试这次喂养' })).toBeVisible()
  expect(keys).toHaveLength(1)
  await page.getByRole('button', { name: '重试这次喂养' }).click()

  await expect(page.getByText('这次喂养已安全完成，没有重复成长。')).toBeVisible()
  expect(keys).toHaveLength(2)
  expect(keys[1]).toBe(keys[0])
  await expect(page.getByText('42 / 100 生命经验')).toBeVisible()
  await expect(page.getByText('+42 生命经验')).toBeVisible()
  await expect(page.getByTestId('growth-trait-curiosity').first()).toContainText('+0.60')
  await expect(page.getByTestId('skill-IMAGE_OBSERVATION')).toContainText('18 / 100')
  await expect(page.getByTestId('skill-STORY_CREATION')).toContainText('12 / 100')
  await expect(page.getByRole('link', { name: '图片 #102' }).first())
    .toHaveAttribute('href', '/picture/102')
  await expect(page.getByText('未进行内容理解').first()).toBeVisible()

  const homeResponse = await page.request.get('/api/companion/me')
  expect(homeResponse.ok()).toBeTruthy()
  const homeBody = await homeResponse.json()
  expect(homeBody.data.companion.lifeExperience).toBe('42')
  expect(homeBody.data.companion.revision).toBe('1')
  expect(homeBody.data.recentGrowth).toHaveLength(1)

  await page.reload()
  await expect(page.getByText('42 / 100 生命经验')).toBeVisible()
  await expect(page.getByText('+42 生命经验')).toBeVisible()
  await expect(page.getByTestId('skill-IMAGE_OBSERVATION')).toContainText('18 / 100')
  await expect(page.getByRole('link', { name: '图片 #102' }).first())
    .toHaveAttribute('href', '/picture/102')
  await expect(page.getByText('未读取图片内容，也未调用视觉模型')).toBeVisible()

  const sourceResponse = await page.request.get('/api/picture/get/vo?id=102')
  expect(sourceResponse.ok()).toBeTruthy()
  const sourceBody = await sourceResponse.json()
  expect(sourceBody.data).toMatchObject({
    id: '102',
    name: '旅行样片',
    url: '/images/mosaic/travel.jpg',
    originalUrl: '/images/mosaic/travel.jpg',
    spaceId: '10'
  })
})
```

- [ ] **Step 4: Run the browser test and verify RED before adding fixtures**

```powershell
npx playwright install chromium
npm run test:e2e
```

Run a local Redis on `127.0.0.1:6379` before this command because the real Sa-Token login DAO uses it even though Spring Session is disabled. Expected: backend startup or login fails because the E2E profile and legacy-table fixtures do not exist yet. This proves the test is using the real backend rather than route mocks.

- [ ] **Step 5: Add the isolated E2E datasource and legacy schema fixture**

Create `src/test/resources/application-e2e.yaml`:

```yaml
spring:
  config:
    activate:
      on-profile: e2e
  datasource:
    url: jdbc:h2:mem:li_picture_cloud_e2e;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER
    username: sa
    password: ""
    driver-class-name: org.h2.Driver
  sql:
    init:
      mode: always
      schema-locations: classpath:e2e-schema.sql
      data-locations: classpath:e2e-data.sql
  liquibase:
    enabled: true
    change-log: classpath:/db/changelog/db.changelog-master.xml
    test-rollback-on-update: true

app:
  companion:
    enabled: true
    feeding-enabled: true
    processing-timeout: 5m

server:
  port: 18124
  servlet:
    context-path: /api
```

Create `e2e-schema.sql` with only the existing contexts needed by the real login, private-space list, picture list, and authorization chain:

```sql
CREATE TABLE IF NOT EXISTS user (
    id BIGINT PRIMARY KEY,
    userAccount VARCHAR(256) NOT NULL,
    userPassword VARCHAR(512) NOT NULL,
    userName VARCHAR(256),
    userAvatar VARCHAR(1024),
    userProfile VARCHAR(512),
    userRole VARCHAR(256) NOT NULL DEFAULT 'user',
    editTime TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    createTime TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isDelete TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_e2e_user_account UNIQUE (userAccount)
);

CREATE TABLE IF NOT EXISTS space (
    id BIGINT PRIMARY KEY,
    spaceName VARCHAR(128),
    spaceLevel INT DEFAULT 0,
    spaceType INT NOT NULL DEFAULT 0,
    maxSize BIGINT DEFAULT 0,
    maxCount BIGINT DEFAULT 0,
    totalSize BIGINT DEFAULT 0,
    totalCount BIGINT DEFAULT 0,
    userId BIGINT NOT NULL,
    createTime TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    editTime TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isDelete TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS picture (
    id BIGINT PRIMARY KEY,
    url VARCHAR(512) NOT NULL,
    thumbnailUrl VARCHAR(512),
    originalUrl VARCHAR(512),
    name VARCHAR(128) NOT NULL,
    introduction VARCHAR(512),
    category VARCHAR(64),
    tags VARCHAR(512),
    picSize BIGINT,
    picWidth INT,
    picHeight INT,
    picScale DOUBLE,
    picFormat VARCHAR(32),
    userId BIGINT NOT NULL,
    spaceId BIGINT,
    createTime TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    editTime TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isDelete TINYINT NOT NULL DEFAULT 0,
    reviewStatus INT NOT NULL DEFAULT 0,
    reviewMessage VARCHAR(512),
    reviewerId BIGINT,
    reviewTime TIMESTAMP
);

CREATE TABLE IF NOT EXISTS space_user (
    id BIGINT PRIMARY KEY,
    spaceId BIGINT NOT NULL,
    userId BIGINT NOT NULL,
    spaceRole VARCHAR(128) DEFAULT 'viewer',
    createTime TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_e2e_space_user UNIQUE (spaceId, userId)
);
```

- [ ] **Step 6: Seed one authenticated subject, private space, and picture**

Create `e2e-data.sql`:

```sql
INSERT INTO user
    (id, userAccount, userPassword, userName, userRole, isDelete)
VALUES
    (7, 'companion_e2e',
     '$2a$12$a5SNma8tchPGcKIOSIGgI.liyrqFQiARSqUVszUdmlO3qCR9U3Cs6',
     '伙伴端到端用户', 'user', 0);

INSERT INTO space
    (id, spaceName, spaceLevel, spaceType, maxSize, maxCount,
     totalSize, totalCount, userId, isDelete, createTime, editTime, updateTime)
VALUES
    (10, '伙伴私有空间', 0, 0, 104857600, 100,
     2048, 1, 7, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO picture
    (id, url, thumbnailUrl, originalUrl, name, introduction, category, tags,
     picSize, picWidth, picHeight, picScale, picFormat, userId, spaceId,
     isDelete, reviewStatus, createTime, editTime, updateTime)
VALUES
    (102, '/images/mosaic/travel.jpg', '/images/mosaic/travel.jpg',
     '/images/mosaic/travel.jpg', '旅行样片', '仅供端到端测试', '旅行',
     '["旅行"]', 2048, 800, 600, 1.3333, 'jpg', 7, 10,
     0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
```

The password hash is the existing local-only BCrypt fixture for `LocalUser123!`. Keep this file under test resources and never reference it from production profiles.

- [ ] **Step 7: Run the real browser flow and verify GREEN**

From `li-picture-cloud-frontend`:

```powershell
npm run test:e2e
```

Expected: Chromium logs in through the real session endpoint, Liquibase creates the companion tables, the UI awakens one companion, the first feed commits once despite its response being aborted, retry reuses the exact key and returns the stored result, H2 contains one growth row, reload preserves disclosure and totals, and the authorized source-picture read proves its identity, location, name, and URLs were not changed by feeding.

- [ ] **Step 8: Add E2E lint/ignore rules and CI gate**

Before the CI edit, wire the production flags through the existing Docker path. In `li-picture-cloud-frontend/Dockerfile`, add before `RUN npm run build`:

```dockerfile
ARG VITE_COMPANION_ENABLED=false
ENV VITE_COMPANION_ENABLED=$VITE_COMPANION_ENABLED
```

In `compose.yaml`, add to `backend.environment`:

```yaml
      COMPANION_ENABLED: ${COMPANION_ENABLED:-false}
      COMPANION_FEEDING_ENABLED: ${COMPANION_FEEDING_ENABLED:-false}
      COMPANION_PROCESSING_TIMEOUT: ${COMPANION_PROCESSING_TIMEOUT:-5m}
```

Add to `web.build`:

```yaml
      args:
        VITE_COMPANION_ENABLED: ${VITE_COMPANION_ENABLED:-false}
```

Add all four variables to `.env.example` with `false`, `false`, `5m`, and `false` defaults. Extend `DeploymentArtifactsTest` to assert the Dockerfile declares the `ARG`, Compose passes the web build arg and all three backend variables, and `.env.example` documents them. Also assert the defaults are false on both frontend and backend; a production operator must rebuild the web image after changing the build flag.

Extend `eslint.config.js`'s Node file block to include:

```javascript
files: ['vite.config.js', 'playwright.config.js', 'eslint.config.js',
  'scripts/**/*.mjs', 'e2e/**/*.js']
```

Add to `.gitignore`:

```text
# Playwright artifacts
**/playwright-report/
**/test-results/
```

After the existing default frontend build/bundle steps, add an explicit production-enabled variant so CI proves both sides of the UI flag:

```yaml
      - name: Verify companion is absent by default
        run: test -z "$(find dist/assets -maxdepth 1 -name 'CompanionView-*.js' -print -quit)"

      - name: Build enabled companion variant
        env:
          VITE_COMPANION_ENABLED: "true"
        run: npm run build

      - name: Verify enabled companion lazy chunk
        run: test -n "$(find dist/assets -maxdepth 1 -name 'CompanionView-*.js' -print -quit)"

      - name: Check enabled companion bundle budget
        run: npm run check:bundle
```

Add a job after the current backend/frontend jobs:

```yaml
  companion-e2e:
    name: Companion browser E2E
    needs: [backend, frontend]
    runs-on: ubuntu-latest
    services:
      redis:
        image: redis:7.4-alpine
        ports:
          - 6379:6379
        options: >-
          --health-cmd "redis-cli ping"
          --health-interval 5s
          --health-timeout 3s
          --health-retries 10
    defaults:
      run:
        working-directory: li-picture-cloud-frontend
    steps:
      - name: Checkout repository
        uses: actions/checkout@v5

      - name: Set up Java 21
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: "21"
          cache: maven

      - name: Set up Node.js 22
        uses: actions/setup-node@v6
        with:
          node-version: "22"
          cache: npm
          cache-dependency-path: li-picture-cloud-frontend/package-lock.json

      - name: Use npm 10
        run: npm install --global npm@10

      - name: Install frontend dependencies
        run: npm ci

      - name: Install Chromium
        run: npx playwright install --with-deps chromium

      - name: Run companion browser flow
        run: npm run test:e2e

      - name: Publish Playwright report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: companion-playwright-report
          path: li-picture-cloud-frontend/playwright-report
          if-no-files-found: ignore
```

Run `..\scripts\mvnw-java21.ps1 -Dtest=DeploymentArtifactsTest test` from the frontend directory (or the equivalent root command) and expect the Docker/Compose flag contract to pass before continuing.

- [ ] **Step 9: Write the Round 19 guide and README entry**

Create `docs/round-19-companion-life-core-guide.md` with these exact sections and facts:

1. `这次交付了什么`: awaken, demo feed, XP/traits/skills/history, one companion per subject.
2. `演示营养不是视觉 AI`: deterministic picture-ID profiles; no image bytes/content/model; persisted `contentUnderstood=false`.
3. `本地体验`: start MySQL/Redis/backend/frontend, open `/companion`, awaken, select a private picture, feed, retry behavior.
4. `配置开关`: backend `COMPANION_ENABLED`, `COMPANION_FEEDING_ENABLED`, `COMPANION_PROCESSING_TIMEOUT`, and build-time frontend `VITE_COMPANION_ENABLED`; production requires both frontend and backend enable flags, both default disabled, and changing the frontend flag requires rebuilding the web image.
5. `数据与一致性`: four tables, unique keys, revision CAS, append-only growth, daily/repeat caps, correlation and safe audit fields.
6. `分片模式上线顺序`: with the five `MYSQL_*` variables set, run `powershell -File scripts/migrate-companion-physical.ps1` (Windows) or `./mvnw -q liquibase:update` (Linux) against direct physical MySQL first; only after exit `0` start ShardingSphere with the new `!SINGLE` declarations.
7. `验证命令`: every focused/full backend, frontend, and Playwright command from this plan.
8. `明确未包含`: real vision, memories, proactive behavior, model/MCP control center, image generation, BYOK/quota/payment, extreme plots.
9. `生产发布警告`: do not enable this demo publicly as AI image understanding; Provider-backed release still requires the master spec's CORS, credential, privacy, and broader E2E gates.

Add under `README.md`'s document navigation:

```markdown
- [图像伙伴生命核心与演示喂养指南](docs/round-19-companion-life-core-guide.md)
```

Add one sentence to the testing section: `CI 还会启动 H2 后端与真实 Chromium，验证伙伴唤醒、私有图片喂养及幂等重试。`

- [ ] **Step 10: Run the complete release-sized verification**

From the repository root:

```powershell
.\scripts\mvnw-java21.ps1 -B -Dspring.profiles.active=test verify
Set-Location li-picture-cloud-frontend
npm audit --omit=dev --audit-level=high --registry=https://registry.npmjs.org
npm run lint
npm test
Remove-Item Env:\VITE_COMPANION_ENABLED -ErrorAction SilentlyContinue
npm run build
npm run check:bundle
$taskDefaultCompanionChunks = Get-ChildItem dist/assets -Filter 'CompanionView-*.js'
if ($taskDefaultCompanionChunks) { throw 'default build exposed companion' }
$env:VITE_COMPANION_ENABLED = 'true'
try {
  npm run build
  npm run check:bundle
  if (-not (Get-ChildItem dist/assets -Filter 'CompanionView-*.js')) {
    throw 'enabled build omitted companion'
  }
} finally {
  Remove-Item Env:\VITE_COMPANION_ENABLED -ErrorAction SilentlyContinue
}
npm run test:e2e
Set-Location ..
git status --short
```

Expected:

- Maven exits 0; Liquibase rollback-on-update passes; companion-domain branch coverage is at least 85%.
- Production dependency audit has no high/critical finding.
- ESLint and all Node tests pass; default production output omits the companion chunk, the enabled output contains it, and both bundle checks exit 0.
- Chromium proves the complete real-backend flow and one-row idempotency result.
- `git status --short` lists only the files intentionally changed by Tasks 1-7; no local secret/config file is staged.

- [ ] **Step 11: Commit the browser gate and guide**

```powershell
git add src/test/resources/application-e2e.yaml src/test/resources/e2e-schema.sql src/test/resources/e2e-data.sql src/test/java/com/li/lipicturecloud/deployment/DeploymentArtifactsTest.java li-picture-cloud-frontend/scripts/start-e2e-backend.mjs li-picture-cloud-frontend/playwright.config.js li-picture-cloud-frontend/e2e/companion.spec.js li-picture-cloud-frontend/vite.config.js li-picture-cloud-frontend/package.json li-picture-cloud-frontend/package-lock.json li-picture-cloud-frontend/Dockerfile li-picture-cloud-frontend/eslint.config.js compose.yaml .env.example .gitignore .github/workflows/ci.yml docs/round-19-companion-life-core-guide.md README.md
git commit -m "test: verify companion life browser flow"
```

---

## Execution Review Gates

After every task, the reviewer must answer all of these before the next task starts:

- Does the task preserve the trusted subject and authorized-resource boundary?
- Can a model/Adapter/client write domain totals directly? The required answer is no.
- Can the same idempotency key, a different key for the same picture, or a concurrent request produce unintended full growth? The required answer is no.
- Can failure leave companion state without its growth fact, or a growth fact without its completed run? The required answer is no.
- Does any current or historical UI imply visual content understanding? The required answer is no.
- Are all newly introduced constants owned by `CompanionBalance.v1()` or explicit feature configuration?
- Do domain files still pass `DomainDependencyTest`?
- Is the task's focused test green before commit?

## Deferred Seams Preserved for the Next Specs

- `PictureNutritionAnalyzer` can receive a real visual-understanding Adapter in slice 2 without changing companion growth calculations.
- `GrowthRecord` already carries source picture ID, explanation, balance version, and after-snapshot; memory candidates and deletion propagation remain separate records in slice 2.
- The five skill codes include future story, emoji, fusion, and gallery-search capabilities, but this slice only changes their proficiency numbers; it does not invoke those capabilities.
- `CompanionLife` remains `home / awaken / feed`; active proposals, feedback, time advancement, autonomy, model routing, MCP, and image creation require separate reviewed Interfaces and plans.
- Feeding-run status and correlation support a future asynchronous/outcome-unknown design, but this fake Adapter completes synchronously and never calls an external Provider.
