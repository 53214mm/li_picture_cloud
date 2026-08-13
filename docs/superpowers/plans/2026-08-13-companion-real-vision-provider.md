# 伙伴真实视觉 Provider Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让伙伴在严格授权、预算上限和完整来源审计下真正读取图片像素，首个实现接入百炼 Qwen 视觉模型，并允许显式降级为元数据营养。

**Architecture:** 喂养运行只保存“请求策略”，分析结果携带“实际来源”；外部模型只能产生结构化观察候选，最终经验、性格和技能仍由 `CompanionBalance` 裁剪。图片由后端从 COS 受控读取，不向模型暴露永久对象地址或 COS 密钥；视觉调用前预占每日次数，失败也计次，防止平台 Token 被无限消耗。

**Tech Stack:** Java 21、Spring Boot 3.5、Spring `RestClient`、MyBatis/MyBatis-Plus、Liquibase、H2/MySQL、Vue 3、Node 22、Playwright。

**Spec:** `docs/superpowers/specs/2026-08-13-companion-vision-foundation-design.md`

## Global Constraints

- 第一家 Provider 固定为 `dashscope`，默认模型固定为 `qwen3.6-flash`；模型 ID 必须可由环境变量覆盖。
- 使用百炼 OpenAI 兼容 `chat/completions` HTTP 接口；不升级现有 DashScope SDK，不新增 OpenAI SDK。
- 视觉请求关闭思考并要求 JSON Schema 结构化输出；温度为 `0`，单次 HTTP 调用不自动重试。
- 默认每个用户每天最多发起 `10` 次视觉调用；额度在出站前预占，模型失败或降级不退还。
- 已存在完整喂养记录的图片只结算熟悉度，直接使用 `SKIPPED_FAMILIAR` 元数据来源，不调用视觉模型也不占额度。
- 只允许从配置的 COS host 解析对象键并通过 `COSClient` 下载；禁止请求数据库中的任意外部 URL。
- 单张送模图片最大 `8 MiB`，仅接受 JPEG、PNG、WEBP；超限、格式不支持或读取失败按显式降级策略处理。
- 单次模型响应体最大 `64 KiB`；超过上限视为 `VISION_INVALID_RESPONSE`，不得继续解析或记录原文。
- 图片字节、Data URL、原始模型响应、用户描述原文不得写入数据库、普通日志、指标标签或异常消息。
- 图片外发前必须再次校验 `PICTURE_VIEW` 权限，并验证图片 ID、对象地址与资源版本仍和读取快照一致。
- 外部模型不得直接修改伙伴、经验、等级、性格、技能、权限、额度或源图片。
- Demo 与 E2E 不访问外网；真实 Provider 使用本地 HTTP stub 验证请求和解析，Live smoke test 默认禁用。
- 生产环境必须显式设置请求策略、Provider、模型、每日次数、超时和最大字节数。

## File Map

### 新建

- `src/main/resources/db/changelog/changes/2026-08-13-companion-visual-provider.xml`：请求策略、实际来源字段和视觉日额度表迁移。
- `src/main/java/com/li/lipicturecloud/domain/companion/NutritionPolicy.java`：`DEMO_ONLY`、`METADATA_ONLY`、`VISUAL_WITH_METADATA_FALLBACK`。
- `src/main/java/com/li/lipicturecloud/domain/companion/NutritionProvenance.java`：一次分析的实际模式、Provider、模型、提示词版本、Schema 版本、置信度和降级原因。
- `src/main/java/com/li/lipicturecloud/application/companion/AuthorizedPictureContent.java`：受控图片字节和资源版本快照。
- `src/main/java/com/li/lipicturecloud/application/companion/AuthorizedPictureContentProvider.java`：受控取图端口。
- `src/main/java/com/li/lipicturecloud/application/companion/VisualObservationCandidate.java`：供应商无关的结构化视觉候选。
- `src/main/java/com/li/lipicturecloud/application/companion/VisualObservationProvider.java`：真实视觉 Provider 端口。
- `src/main/java/com/li/lipicturecloud/application/companion/VisionQuotaGuard.java`：每日视觉调用额度端口。
- `src/main/java/com/li/lipicturecloud/infrastructure/companion/CosAuthorizedPictureContentProvider.java`：COS 受控读取、二次授权与版本校验。
- `src/main/java/com/li/lipicturecloud/infrastructure/companion/DashScopeVisionClient.java`：百炼兼容接口调用和严格 JSON 解析。
- `src/main/java/com/li/lipicturecloud/infrastructure/companion/VisualPictureNutritionAdapter.java`：视觉候选到候选营养的确定性映射及元数据降级。
- `src/main/java/com/li/lipicturecloud/infrastructure/persistence/companion/MybatisVisionQuotaGuard.java`：按用户、上海自然日串行预占额度。
- `src/main/java/com/li/lipicturecloud/model/entity/CompanionVisionUsageEntity.java`、`src/main/java/com/li/lipicturecloud/mapper/CompanionVisionUsageMapper.java`：额度持久化。
- 对应单元、迁移、持久化、集成和 HTTP stub 测试文件。

### 修改

- `FeedingRun`、`GrowthRecord`、`PictureNutrition` 及其 Entity/Mapper/Repository：分离请求策略与实际来源。
- `PictureNutritionAnalyzer`、`CompanionLifeService`、`CompanionFeedingCoordinator`、`CompanionConfiguration`：选择策略、预占额度、分析、降级与原子落库。
- `CompanionFeatureProperties`、四套 application YAML、`.env.example`、`compose.yaml`：视觉配置与生产必填项。
- `NutritionStatusView`、`GrowthRecordView`、`CompanionViewAssembler`、伙伴前端：展示实际来源和降级，不夸大内容理解。
- 两份 ShardingSphere YAML：把 `primary.companion_vision_usage` 加入 `!SINGLE`。
- `docs/round-19-companion-life-core-guide.md` 和领域 `CONTEXT.md`：部署、隐私、额度和术语。

---

### Task 1: 分离请求策略与实际营养来源

**Files:**
- Create: `src/main/java/com/li/lipicturecloud/domain/companion/NutritionPolicy.java`
- Create: `src/main/java/com/li/lipicturecloud/domain/companion/NutritionProvenance.java`
- Create: `src/main/resources/db/changelog/changes/2026-08-13-companion-visual-provider.xml`
- Modify: `src/main/resources/db/changelog/db.changelog-master.xml`
- Modify: `src/main/java/com/li/lipicturecloud/domain/companion/{NutritionMode,PictureNutrition,FeedingRun,GrowthRecord}.java`
- Modify: `src/main/java/com/li/lipicturecloud/model/entity/{CompanionFeedRunEntity,CompanionGrowthRecordEntity}.java`
- Modify: `src/main/java/com/li/lipicturecloud/mapper/CompanionGrowthRecordMapper.java`
- Modify: `src/main/java/com/li/lipicturecloud/infrastructure/persistence/companion/{MybatisFeedingRunRepository,MybatisGrowthRecordRepository}.java`
- Test: `src/test/java/com/li/lipicturecloud/domain/companion/NutritionProvenanceTest.java`
- Test: `src/test/java/com/li/lipicturecloud/migration/CompanionSchemaMigrationTest.java`
- Test: `src/test/java/com/li/lipicturecloud/infrastructure/persistence/companion/CompanionPersistenceIntegrationTest.java`

**Interfaces:**
- Produces: `NutritionPolicy`, `NutritionProvenance`, `PictureNutrition.provenance()`。
- `FeedingRun` 保存 `requestedPolicy/requestedProviderCode/requestedModelCode`，不再声称分析前已经理解内容。
- `GrowthRecord` 保存实际 `NutritionProvenance`。

- [ ] **Step 1: 写领域失败测试**

```java
@Test
void visualPolicyAcceptsVisualOrExplicitMetadataFallbackOnly() {
    NutritionProvenance visual = NutritionProvenance.visual(
            "dashscope", "qwen3.6-flash", "companion-vision-v1",
            "visual-observation-v1", new BigDecimal("0.82"));
    NutritionProvenance fallback = NutritionProvenance.metadataFallback("VISION_TIMEOUT");

    assertThat(NutritionPolicy.VISUAL_WITH_METADATA_FALLBACK.accepts(visual)).isTrue();
    assertThat(NutritionPolicy.VISUAL_WITH_METADATA_FALLBACK.accepts(fallback)).isTrue();
    assertThat(NutritionPolicy.METADATA_ONLY.accepts(visual)).isFalse();
    assertThatThrownBy(() -> NutritionProvenance.visual(
            "dashscope", "qwen3.6-flash", "p", "s", new BigDecimal("1.01")))
            .isInstanceOf(IllegalArgumentException.class);
}
```

- [ ] **Step 2: 运行领域测试确认 RED**

Run:

```powershell
.\scripts\mvnw-java21.ps1 "-Dtest=NutritionProvenanceTest" test
```

Expected: 编译失败，`NutritionPolicy` 和 `NutritionProvenance` 尚不存在。

- [ ] **Step 3: 实现精确领域类型**

```java
public enum NutritionPolicy {
    DEMO_ONLY,
    METADATA_ONLY,
    VISUAL_WITH_METADATA_FALLBACK;

    public boolean accepts(NutritionProvenance value) {
        return switch (this) {
            case DEMO_ONLY -> value.actualMode() == NutritionMode.DEMO_DETERMINISTIC;
            case METADATA_ONLY -> value.actualMode() == NutritionMode.METADATA_DETERMINISTIC
                    && value.fallbackReasonCode() == null;
            case VISUAL_WITH_METADATA_FALLBACK -> value.actualMode() == NutritionMode.VISUAL_MODEL
                    || value.actualMode() == NutritionMode.METADATA_DETERMINISTIC
                    && value.fallbackReasonCode() != null;
        };
    }
}
```

`NutritionProvenance` 固定字段：

```java
public record NutritionProvenance(
        NutritionMode actualMode,
        boolean contentUnderstood,
        String providerCode,
        String modelCode,
        String promptVersion,
        String resultSchemaVersion,
        BigDecimal confidence,
        String fallbackReasonCode) { }
```

不变量：视觉模式必须 `contentUnderstood=true` 且置信度在 `[0,1]`；Demo/元数据必须为 `false`；只有元数据模式可带 `fallbackReasonCode`；所有代码字段使用 `[a-zA-Z0-9._-]{1,128}`。

- [ ] **Step 4: 写迁移 RED 测试**

测试必须验证：

1. `companion_feed_run.nutritionMode` 重命名为 `requestedPolicy`，旧值映射到 `DEMO_ONLY/METADATA_ONLY`；
2. 新增 nullable `requestedProviderCode/requestedModelCode`；
3. `companion_growth_record` 新增 `providerCode/modelCode/promptVersion/resultSchemaVersion/confidence/fallbackReasonCode`；
4. 旧成长记录回填 `internal/demo-v1` 或 `internal/metadata-v1`；
5. update → rollback → update 可重复执行。

- [ ] **Step 5: 编写可恢复 Liquibase changeSet**

每个 DDL 单独 changeSet，顺序固定为：重命名列 → 映射旧策略值 → 删除 run 的 `contentUnderstood` → 增加 run 请求 Provider/模型 → 逐列增加成长来源 → 回填 → 增加非空约束。MySQL 会自动提交 DDL，因此禁止把多个 DDL 合并进一个 changeSet。

- [ ] **Step 6: 更新实体、Mapper、仓储并跑 GREEN**

Run:

```powershell
.\scripts\mvnw-java21.ps1 "-Dtest=NutritionProvenanceTest,CompanionSchemaMigrationTest,CompanionPersistenceIntegrationTest" test
```

Expected: 全部通过，旧数据恢复为等价来源。

- [ ] **Step 7: 提交**

```powershell
git add src/main/resources/db/changelog src/main/java/com/li/lipicturecloud/domain/companion src/main/java/com/li/lipicturecloud/model/entity src/main/java/com/li/lipicturecloud/mapper src/main/java/com/li/lipicturecloud/infrastructure/persistence/companion src/test/java/com/li/lipicturecloud
git commit -m "refactor: separate companion nutrition policy and provenance"
```

---

### Task 2: 建立平台钱包的每日视觉硬额度

**Files:**
- Create: `src/main/java/com/li/lipicturecloud/application/companion/VisionQuotaGuard.java`
- Create: `src/main/java/com/li/lipicturecloud/infrastructure/persistence/companion/MybatisVisionQuotaGuard.java`
- Create: `src/main/java/com/li/lipicturecloud/model/entity/CompanionVisionUsageEntity.java`
- Create: `src/main/java/com/li/lipicturecloud/mapper/CompanionVisionUsageMapper.java`
- Modify: Task 1 的 Liquibase 文件
- Modify: `src/main/resources/sharding/{static,dynamic}.yaml`
- Test: `src/test/java/com/li/lipicturecloud/infrastructure/persistence/companion/VisionQuotaIntegrationTest.java`
- Test: `src/test/java/com/li/lipicturecloud/sharding/{CompanionSingleTableRoutingIntegrationTest,ShardingModeConfigurationTest}.java`

**Interfaces:**
- Produces: `VisionQuotaGuard.reserve(long subjectId, LocalDate usageDate, int dailyLimit)`。
- 返回 `VisionQuotaReservation(usageDate, used, limit)`；超过上限抛安全的 `BusinessException(FORBIDDEN_ERROR, "今日视觉营养额度已用完")`。

- [ ] **Step 1: 写并发额度 RED 测试**

```java
@Test
void tenConcurrentReservationsNeverExceedDailyLimit() throws Exception {
    int limit = 3;
    List<Future<Boolean>> results = runConcurrently(10, () -> {
        try {
            guard.reserve(7L, LocalDate.of(2026, 8, 13), limit);
            return true;
        } catch (BusinessException exhausted) {
            return false;
        }
    });
    assertThat(results.stream().filter(this::get).count()).isEqualTo(3);
    assertThat(mapper.selectAttempts(7L, LocalDate.of(2026, 8, 13))).isEqualTo(3);
}
```

- [ ] **Step 2: 运行确认 RED**

Run: `./scripts/mvnw-java21.ps1 "-Dtest=VisionQuotaIntegrationTest" test`

Expected: 缺少 Guard、表和 Mapper。

- [ ] **Step 3: 实现日桶与事务锁**

表结构固定为：`id BIGINT PK, subjectId BIGINT, usageDate DATE, attempts INT, revision BIGINT, createTime TIMESTAMP, updateTime TIMESTAMP`，唯一键 `(subjectId, usageDate)`。首次插入捕获唯一键竞争并重读；已有行使用 `SELECT ... FOR UPDATE`，只有 `attempts < dailyLimit` 时加一。日期由 `CompanionBalance` 使用的 `Asia/Shanghai` 规则计算，不直接调用系统默认时区。

- [ ] **Step 4: 加入两份 `!SINGLE` 并做真实路由测试**

两份 YAML 都增加：

```yaml
- primary.companion_vision_usage
```

测试必须通过真实 ShardingSphere DataSource 对该表 insert/select/update，不只做字符串 contains。

- [ ] **Step 5: 运行 GREEN**

Run:

```powershell
.\scripts\mvnw-java21.ps1 "-Dtest=VisionQuotaIntegrationTest,CompanionSingleTableRoutingIntegrationTest,ShardingModeConfigurationTest" test
```

- [ ] **Step 6: 提交**

```powershell
git add src/main src/test
git commit -m "feat: cap daily companion vision usage"
```

---

### Task 3: 从 COS 安全取得受控图片字节

**Files:**
- Create: `src/main/java/com/li/lipicturecloud/application/companion/AuthorizedPictureContent.java`
- Create: `src/main/java/com/li/lipicturecloud/application/companion/AuthorizedPictureContentProvider.java`
- Create: `src/main/java/com/li/lipicturecloud/infrastructure/companion/CosAuthorizedPictureContentProvider.java`
- Modify: `src/main/java/com/li/lipicturecloud/repository/PictureRepository.java`
- Test: `src/test/java/com/li/lipicturecloud/infrastructure/companion/CosAuthorizedPictureContentProviderTest.java`

**Interfaces:**
- Produces: `AuthorizedPictureContent load(AuthorizedPictureRef reference, long maxBytes)`。
- `AuthorizedPictureContent` 只含 `pictureId/resourceVersion/mimeType/byte[]`；构造器和访问器都防御性复制字节。

- [ ] **Step 1: 写安全边界 RED 测试**

覆盖以下用例：

```java
@Test
void rejectsForeignHostWithoutMakingAnyNetworkOrCosCall() { }

@Test
void rechecksPermissionAndVersionAfterDownloadBeforeReturningBytes() { }

@Test
void stopsAtMaxBytesInsteadOfAllocatingAnUnboundedArray() { }

@Test
void returnedBytesCannotMutateTheStoredSnapshot() { }
```

- [ ] **Step 2: 运行确认 RED**

Run: `./scripts/mvnw-java21.ps1 "-Dtest=CosAuthorizedPictureContentProviderTest" test`

- [ ] **Step 3: 实现安全取图**

实现顺序必须是：

1. 查询图片并拍摄 `id + originalUrl/url + updateTime` 快照；
2. `authorization.checkForUser(PICTURE_VIEW, id, subjectId)`；
3. 用 `URI` 精确比较 scheme/host/port，不使用 `contains` 或 `substring` 判断 host；
4. 只把规范化对象 key 交给 `CosManager.getObject`；
5. 用最多 `maxBytes + 1` 的有界读取判断超限，关闭 `COSObject` 与输入流；
6. 重新查询图片、重新授权，逐项比较快照；不一致抛 `NO_AUTH_ERROR`，不返回字节。

允许 MIME 映射固定为 `jpg/jpeg -> image/jpeg`、`png -> image/png`、`webp -> image/webp`。

- [ ] **Step 4: 运行 GREEN 并确认日志不含 URL/字节**

Run: `./scripts/mvnw-java21.ps1 "-Dtest=CosAuthorizedPictureContentProviderTest" test`

- [ ] **Step 5: 提交**

```powershell
git add src/main/java/com/li/lipicturecloud/application/companion src/main/java/com/li/lipicturecloud/infrastructure/companion src/main/java/com/li/lipicturecloud/repository src/test/java/com/li/lipicturecloud/infrastructure/companion
git commit -m "feat: load authorized companion image content"
```

---

### Task 4: 接入 DashScope Qwen 结构化视觉理解

**Files:**
- Create: `src/main/java/com/li/lipicturecloud/application/companion/{VisualObservationCandidate,VisualObservationProvider}.java`
- Create: `src/main/java/com/li/lipicturecloud/infrastructure/companion/{DashScopeVisionClient,DashScopeVisionResponse}.java`
- Modify: `src/main/java/com/li/lipicturecloud/config/CompanionFeatureProperties.java`
- Test: `src/test/java/com/li/lipicturecloud/infrastructure/companion/DashScopeVisionClientTest.java`

**Interfaces:**
- `VisualObservationProvider.observe(AuthorizedPictureContent content)` 返回 `VisualObservationCandidate`。
- Candidate 固定字段：`mood`、`sceneComplexity(0..4)`、`energy(0..4)`、`socialPresence`、`motionPotential(0..4)`、`creativity(0..4)`、`confidence(0..1)`。

- [ ] **Step 1: 用 `MockRestServiceServer` 写 HTTP 契约 RED 测试**

断言请求：

- URL 恰为配置 endpoint；
- `Authorization: Bearer ...`，日志不打印该值；
- model 为 `qwen3.6-flash`；
- 图片为正确 MIME 的 Data URL；
- `temperature=0`、`enable_thinking=false`；
- `response_format.type=json_schema`，Schema 禁止额外字段。

Stub 返回：

```json
{"choices":[{"message":{"content":"{\"mood\":\"JOYFUL\",\"sceneComplexity\":2,\"energy\":3,\"socialPresence\":true,\"motionPotential\":2,\"creativity\":3,\"confidence\":0.84}"}}]}
```

- [ ] **Step 2: 运行确认 RED**

Run: `./scripts/mvnw-java21.ps1 "-Dtest=DashScopeVisionClientTest" test`

- [ ] **Step 3: 实现 RestClient 与严格解析**

配置字段固定为：

```java
private URI visionEndpoint = URI.create(
        "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions");
private String visionProvider = "dashscope";
private String visionModel = "qwen3.6-flash";
private Duration visionTimeout = Duration.ofSeconds(20);
private DataSize visionMaxBytes = DataSize.ofMegabytes(8);
private int visionDailyLimit = 10;
```

Client 只接受 HTTPS endpoint。HTTP 401/403 映射 `VISION_CREDENTIALS`，429 映射 `VISION_RATE_LIMITED`，超时映射 `VISION_TIMEOUT`，5xx 映射 `VISION_UNAVAILABLE`，JSON/Schema 错误映射 `VISION_INVALID_RESPONSE`。异常对象不得携带响应原文。

- [ ] **Step 4: 补恶意/异常响应测试并跑 GREEN**

必须覆盖超大响应、Markdown code fence、缺字段、越界数值、额外字段、空 choices、401、429、500 和超时；除严格 JSON 对象外全部安全失败。

- [ ] **Step 5: 提交**

```powershell
git add src/main/java/com/li/lipicturecloud/application/companion src/main/java/com/li/lipicturecloud/infrastructure/companion src/main/java/com/li/lipicturecloud/config src/test/java/com/li/lipicturecloud/infrastructure/companion
git commit -m "feat: add dashscope companion vision client"
```

---

### Task 5: 把视觉候选映射为平衡营养并显式降级

**Files:**
- Create: `src/main/java/com/li/lipicturecloud/infrastructure/companion/VisualPictureNutritionAdapter.java`
- Modify: `src/main/java/com/li/lipicturecloud/application/companion/PictureNutritionAnalyzer.java`
- Modify: `src/main/java/com/li/lipicturecloud/infrastructure/companion/{DemoPictureNutritionAdapter,MetadataPictureNutritionAdapter}.java`
- Modify: `src/main/java/com/li/lipicturecloud/config/CompanionConfiguration.java`
- Test: `src/test/java/com/li/lipicturecloud/infrastructure/companion/VisualPictureNutritionAdapterTest.java`
- Test: `src/test/java/com/li/lipicturecloud/config/CompanionConfigurationTest.java`

**Interfaces:**
- `PictureNutritionAnalyzer.policy()` 返回请求策略；`analyze()` 返回携带实际 `NutritionProvenance` 的 `PictureNutrition`。
- Visual Adapter 依赖 `VisionQuotaGuard`、`AuthorizedPictureContentProvider`、`VisualObservationProvider` 和 Metadata Adapter。

- [ ] **Step 1: 写映射和降级 RED 测试**

测试使用候选 `JOYFUL, sceneComplexity=3, energy=3, socialPresence=true, motionPotential=2, creativity=4, confidence=0.84`，并锁定：

```java
assertThat(nutrition.requestedLifeExperience()).isEqualTo(48L);
assertThat(nutrition.requestedTraitDelta().playfulness()).isEqualByComparingTo("0.30");
assertThat(nutrition.requestedTraitDelta().empathy()).isEqualByComparingTo("0.20");
assertThat(nutrition.requestedSkillExperience())
        .containsEntry(CompanionSkill.IMAGE_OBSERVATION, 27L)
        .containsEntry(CompanionSkill.STORY_CREATION, 14L);
assertThat(nutrition.provenance().actualMode()).isEqualTo(NutritionMode.VISUAL_MODEL);
```

并验证 `VISION_TIMEOUT` 时返回 metadata provenance、`fallbackReasonCode=VISION_TIMEOUT`；已熟悉图片返回 `fallbackReasonCode=SKIPPED_FAMILIAR` 且不调用 Quota/Content/Visual Provider；`VISION_CREDENTIALS` 不降级而直接失败，防止错误密钥被长期掩盖。

- [ ] **Step 2: 运行确认 RED**

Run: `./scripts/mvnw-java21.ps1 "-Dtest=VisualPictureNutritionAdapterTest,CompanionConfigurationTest" test`

- [ ] **Step 3: 实现唯一映射表**

固定公式：

- XP：`35 + sceneComplexity*2 + energy + creativity`，最终范围 `[35,55]`；
- curiosity：`0.20 + sceneComplexity*0.10`；
- enthusiasm：`energy*0.10`；
- playfulness：`JOYFUL ? 0.20 : 0` 加 `motionPotential*0.05`；
- empathy：`socialPresence ? 0.20 : 0`，`MELANCHOLIC/TENSE` 再加 `0.05`；
- creativity：`creativity*0.10`；
- IMAGE_OBSERVATION：`12 + sceneComplexity*3 + confidence>=0.8 ? 6 : 0`；
- STORY_CREATION：`socialPresence ? 6 + creativity*2 : creativity*2`；
- EMOJI_CREATION：`mood != NEUTRAL ? 4 + energy : 0`。

这些仍是候选值，必须继续经过现有 `CompanionBalance` 单次/每日/重复图片上限。

- [ ] **Step 4: 实现降级白名单**

只对 `VISION_TIMEOUT`、`VISION_RATE_LIMITED`、`VISION_UNAVAILABLE`、`VISION_INVALID_RESPONSE`、`VISION_IMAGE_TOO_LARGE` 降级；`SKIPPED_FAMILIAR` 是结算优化而不是错误。`VISION_CREDENTIALS`、权限错误、额度耗尽和程序不变量错误不得降级。

- [ ] **Step 5: 运行 GREEN**

Run: `./scripts/mvnw-java21.ps1 "-Dtest=VisualPictureNutritionAdapterTest,MetadataPictureNutritionAdapterTest,DemoPictureNutritionAdapterTest,CompanionConfigurationTest" test`

- [ ] **Step 6: 提交**

```powershell
git add src/main src/test
git commit -m "feat: convert visual observations into companion growth"
```

---

### Task 6: 接入喂养事务、幂等重试与真实来源审计

**Files:**
- Modify: `src/main/java/com/li/lipicturecloud/application/companion/{CompanionLifeService,CompanionFeedingCoordinator,FeedReservation}.java`
- Modify: `src/main/java/com/li/lipicturecloud/domain/companion/FeedingRunRepository.java`
- Modify: `src/main/java/com/li/lipicturecloud/infrastructure/persistence/companion/MybatisFeedingRunRepository.java`
- Test: `src/test/java/com/li/lipicturecloud/application/companion/{CompanionFeedingCoordinatorTest,CompanionFeedingIntegrationTest}.java`

**Interfaces:**
- Reserve 输入固定为请求策略/Provider/模型；Complete 输入实际 `PictureNutrition.provenance()`。
- Completed replay 永远返回当时实际来源，不重新取图、不重新调模型、不重新扣视觉次数。

- [ ] **Step 1: 写幂等与来源 RED 测试**

覆盖：

1. 同 key 网络重放只产生一个视觉额度、一个成长记录、一次经验；
2. FAILED 重启会再次预占视觉次数，但必须保持相同请求策略/Provider/模型；
3. 配置从 visual 改 metadata 后，旧失败 key 返回“策略已变化，请使用新请求标识”；
4. visual 请求实际降级 metadata 时可完成且成长记录保留 fallback；
5. METADATA_ONLY 不接受 visual provenance；
6. 同一图片完整喂养后使用新 key，只增加熟悉度，不调用模型、不占视觉额度；
7. run completion CAS 失败时伙伴、技能和成长来源整体回滚。

- [ ] **Step 2: 运行确认 RED**

Run: `./scripts/mvnw-java21.ps1 "-Dtest=CompanionFeedingCoordinatorTest,CompanionFeedingIntegrationTest" test`

- [ ] **Step 3: 调整 reserve/complete 状态机**

`complete` 的第一条业务校验改为：

```java
if (!run.requestedPolicy().accepts(nutrition.provenance())) {
    throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片营养来源与请求策略不一致");
}
```

成长记录从 `nutrition.provenance()` 取实际来源。视觉额度只能在 `STARTED`、图片尚无完整喂养记录且真正准备出站时预占；REPLAY、REJECTED、IN_PROGRESS 和 `SKIPPED_FAMILIAR` 均不能扣次数。首次喂养的并发请求允许因无跨事务长锁而多发生一次模型调用，但只能有一条完整成长；用每日额度限制损失，不在本阶段引入分布式 single-flight。

- [ ] **Step 4: 跑 GREEN 和事务回滚测试**

Run: `./scripts/mvnw-java21.ps1 "-Dtest=CompanionFeedingCoordinatorTest,CompanionFeedingIntegrationTest" test`

- [ ] **Step 5: 提交**

```powershell
git add src/main/java/com/li/lipicturecloud/application/companion src/main/java/com/li/lipicturecloud/domain/companion src/main/java/com/li/lipicturecloud/infrastructure/persistence/companion src/test/java/com/li/lipicturecloud/application/companion
git commit -m "feat: audit actual companion vision provenance"
```

---

### Task 7: 配置、前端披露和运维文档

**Files:**
- Modify: `src/main/resources/application.yaml`
- Modify: `src/main/resources/application-prod.yaml`
- Modify: `src/test/resources/{application-test,application-e2e}.yaml`
- Modify: `.env.example`, `compose.yaml`
- Modify: `src/main/java/com/li/lipicturecloud/application/companion/view/{NutritionStatusView,GrowthRecordView}.java`
- Modify: `src/main/java/com/li/lipicturecloud/application/companion/CompanionViewAssembler.java`
- Modify: `li-picture-cloud-frontend/src/{utils/companion.js,views/CompanionView.vue}`
- Modify: `li-picture-cloud-frontend/tests/companion.test.mjs`
- Modify: `docs/round-19-companion-life-core-guide.md`
- Modify: `src/main/java/com/li/lipicturecloud/domain/companion/CONTEXT.md`
- Test: `src/test/java/com/li/lipicturecloud/deployment/DeploymentArtifactsTest.java`

**Interfaces:**
- `NutritionStatusView(policy, providerCode, modelCode, dailyLimit, notice)`。
- `GrowthRecordView` 增加 `providerCode/modelCode/confidence/fallbackReasonCode/nutritionLabel`。

- [ ] **Step 1: 写前端/部署 RED 测试**

前端断言文案：

- visual：`Qwen 视觉营养 · 已分析图片内容`；
- fallback：`视觉服务暂不可用，本次使用图片元数据营养`；
- metadata：仍明确 `未读取图片像素`；
- 页面不得展示 API key、COS URL、fallback 原始异常。

部署测试断言生产必须显式提供：

```text
COMPANION_NUTRITION_POLICY
COMPANION_VISION_PROVIDER
COMPANION_VISION_MODEL
COMPANION_VISION_DAILY_LIMIT
COMPANION_VISION_TIMEOUT
COMPANION_VISION_MAX_BYTES
DASHSCOPE_API_KEY
```

- [ ] **Step 2: 用后端返回 label，删除前端模式 switch**

前端只按 `contentUnderstood/fallbackReasonCode` 选择图标和辅助说明，主显示名使用后端 `nutritionLabel`，避免每增加 Provider 同时修改两端注册表。

- [ ] **Step 3: 固定非生产配置**

- `application-test.yaml`：`METADATA_ONLY`，不创建外部调用；
- `application-e2e.yaml`：`DEMO_ONLY`，保持浏览器断言稳定；
- 本地 `application.yaml`：默认 `METADATA_ONLY`；
- prod：所有视觉选项无默认值，功能总开关仍默认关闭。

- [ ] **Step 4: 更新指南和术语**

新增术语：请求策略、实际来源、视觉候选、显式降级、视觉日额度。指南写明 Qwen 会接收图片像素、用途、默认次数、失败计次、关闭方式，以及如何轮换 `DASHSCOPE_API_KEY`。

- [ ] **Step 5: 运行前后端验证**

```powershell
.\scripts\mvnw-java21.ps1 "-Dtest=DeploymentArtifactsTest,CompanionControllerTest" test
Set-Location li-picture-cloud-frontend
npm test
npm run lint
$env:VITE_COMPANION_ENABLED='true'; npm run build
```

- [ ] **Step 6: 提交**

```powershell
git add .env.example compose.yaml docs src li-picture-cloud-frontend
git commit -m "feat: disclose companion vision usage and fallback"
```

---

### Task 8: 全链路验证、Live smoke 手册与发布门禁

**Files:**
- Create: `src/test/java/com/li/lipicturecloud/infrastructure/companion/DashScopeVisionLiveSmokeTest.java`
- Modify: `li-picture-cloud-frontend/e2e/companion.spec.js`
- Modify: `.github/workflows/ci.yml`
- Modify: `docs/round-19-companion-life-core-guide.md`

**Interfaces:**
- CI 只跑 stub；Live smoke 仅在 `COMPANION_VISION_LIVE_TEST=true` 且存在 API key 时运行。

- [ ] **Step 1: 增加 stub 全链路集成测试**

使用 H2 + 本地 `MockRestServiceServer`，验证：授权私有图片 → 额度预占 → COS fixture → Qwen JSON → 性格/技能/XP → 实际来源落库 → 同 key replay 不二次调用。

- [ ] **Step 2: 增加浏览器披露断言**

E2E 继续使用 Demo，不出网；额外用 API fixture 注入一条 visual 和一条 fallback 历史，断言 label、Provider、模型、来源图片链接和刷新后顺序。

- [ ] **Step 3: 增加默认禁用的 Live smoke**

Live 测试只使用仓库内无隐私 fixture；断言返回满足 Schema，不断言具体语义文案，不打印响应。命令：

```powershell
$env:COMPANION_VISION_LIVE_TEST='true'
$env:DASHSCOPE_API_KEY='由操作者在当前终端临时提供'
.\scripts\mvnw-java21.ps1 "-Dtest=DashScopeVisionLiveSmokeTest" test
```

该命令只写进文档，任何自动化代理都不得代填或回显密钥。

- [ ] **Step 4: 运行完整后端门禁**

```powershell
.\scripts\mvnw-java21.ps1 "-Dmaven.compiler.fork=true" "-Dspring.profiles.active=test" verify
```

Expected: 所有测试通过，伙伴领域 JaCoCo 门禁通过。

- [ ] **Step 5: 运行前端、Bundle、E2E 和 Compose 门禁**

```powershell
Set-Location li-picture-cloud-frontend
npm test
npm run lint
npm run build
$env:VITE_COMPANION_ENABLED='true'; npm run build
npm run check:bundle
npm run test:e2e
Set-Location ..
docker compose config --quiet
git diff --check
```

- [ ] **Step 6: 人工隐私与钱包审核**

人工审核必须逐项确认：

1. 抓包中只出现发往配置 DashScope endpoint 的请求；
2. 请求不包含 COS Secret、永久签名参数或其他用户图片；
3. 应用日志不含 Data URL、原始模型 JSON、API key、图片 URL；
4. 第 11 次视觉调用在出站前被阻断；
5. 401 不降级，超时/429/5xx 按白名单降级且记录原因；
6. 撤销团队权限后，旧幂等键也不能读取历史结果；
7. 源图片的 ID、名称、URL、空间和审核状态在喂养后不变。

- [ ] **Step 7: 提交并等待人工合并决定**

```powershell
git add .github docs src li-picture-cloud-frontend
git commit -m "test: verify companion real vision flow"
git status --short --branch
```

不要自动合并 `main`。推送功能分支后由人工查看差异、CI 和钱包/隐私清单，再决定是否创建 PR。

## Self-Review Result

- Spec coverage：观察与成长分离、最小披露、真实模式、显式降级、权限复验、来源审计均有对应任务。
- 新增风险覆盖：平台 Token 日额度、失败计次、并发预占、永久 URL/COS 密钥隔离、外部响应不可信、配置错误不静默降级。
- Type consistency：全计划统一使用 `NutritionPolicy` 表示请求、`NutritionProvenance` 表示实际结果；`NutritionMode` 仅表示实际模式。
- Scope boundary：本计划不实现用户自带 Token、计费支付、长期记忆、主动行为或多 Provider 配置页面；这些在真实视觉链路稳定后单独立项。
- Primary references：[Spring AI 多模态输入](https://docs.spring.io/spring-ai/reference/api/multimodality.html)、[Spring AI 敏感提示默认不记录](https://docs.spring.io/spring-ai/reference/observability/)、[阿里云视觉理解与结构化输出](https://help.aliyun.com/zh/model-studio/vision-model/)、[Qwen VL OpenAI 兼容接口](https://help.aliyun.com/zh/model-studio/qwen-vl-compatible-with-openai)。
