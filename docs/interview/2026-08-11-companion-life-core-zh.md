# 伙伴生命核心纵向切片实施计划

> **针对智能体工作者：** 必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 这一子技能，逐任务实施本计划。步骤使用复选框（`- [ ]`）语法进行跟踪。

**目标：** 构建一个可运行的首个纵向切片：一个已认证的主体唤醒一个伙伴，明确地通过确定性演示营养 Adapter 喂养一张当前已获授权的图片，并在伙伴页面上获得持久化的生命经验、性格、技能以及可解释的成长反馈。

**架构：** 保持伙伴生命核心无框架依赖且具备确定性。HTTP/应用层负责受信任主体、权限复核、幂等喂养流程、事务边界和安全错误；可替换的营养与 MyBatis/Liquibase Adapter 隔离在小型 Interface 之后。已完成的喂养运行、乐观式伙伴快照更新、技能快照和追加式成长记录在一个原子事务中提交，而被拒绝或失败的运行仍可审计，但不会成为成长历史。

**技术栈：** Java 21、Spring Boot 3.5.14、MyBatis-Plus 3.5.9、Liquibase、MySQL 8 / H2 MySQL 模式、JUnit 5、AssertJ、Mockito、Vue 3.4、Pinia 2.1、Vue Router 4.3、Axios 1.7、Node 22、Vite 7、Playwright 1.62.1、GitHub Actions。

## 全局约束

- 本计划只实现实现切片 1，即 **伙伴生命核心纵向切片**。真实视觉理解与记忆、主动提案、能力/MCP 迁移、模型配置、DeepSeek、GPT Image 2、BYOK、配额、支付、故事、贴纸、图片融合、桌面宠物和极端状态剧情均不在本计划范围内。
- 使用已确定的领域词汇：**主体、伙伴、喂养、成长记录、生命经验、等级、生命阶段、性格轴、技能熟练度、图片、空间、权限、授权资源**。不要把喂养改称为上传、训练或文件消费。
- 每个主体最多拥有一个伙伴。通过数据库唯一约束和支持竞态安全的创建或加载行为强制执行；不要依赖插入前计数。
- 客户端绝不提交用户 ID、声明的空间 ID、营养结果、XP、性格值、技能值、平衡版本或伙伴修订号。服务器从已认证会话中推导主体，并将图片解析为已获授权的资源。
- 每次喂养尝试都必须在营养 Adapter 看到图片之前，通过 `SpaceAuthorizationAccessService.checkForUser(String permission, Long pictureId, Long userId)` 重新检查 `picture:view`。缺失图片和未授权图片必须返回同一条安全消息：`图片不可用或无权访问`。
- 喂养只把图片当作已授权的来源引用。它绝不编辑、删除、改名、移动或以其他方式修改原始图片、所属空间或权限。
- 确定性的演示 Adapter 绝不读取图片字节，也不声称理解内容。所有当前视图和历史视图都说明 `未读取图片内容，也未调用视觉模型`。
- 生命经验非负且单调递增。等级和生命阶段由版本化的 `life-core-v1` 平衡对象推导；权限、成本和主动频率绝不由等级推导。
- 性格轴为 `好奇↔谨慎、热情↔克制、淘气↔沉稳、共情↔理性、创造↔秩序`。存储值保持在 `[-100, 100]`；一次完整喂养对任意轴的变化最多为 `1.00`。
- 技能经验独立于生命经验。初始技能目录为 `IMAGE_OBSERVATION`、`STORY_CREATION`、`EMOJI_CREATION`、`IMAGE_FUSION` 和 `GALLERY_SEARCH`。
- 每个请求携带一个符合 `[a-z0-9_-]{16,64}` 的小写 16—64 字符幂等键，作用域为 `(companionId, idempotencyKey)`。同一键配合不同图片重用时属于参数冲突。重用已完成的键时，返回原始快照和成长记录，不再产生其他变更。小写规范化可避免 MySQL 不区分大小写排序规则与 H2 之间的差异。
- 对已喂养过的图片使用新键时，会创建 `PICTURE_REVISITED` 成长事实：最多增加 `1` 点生命经验，不改变性格，不改变技能，并且该伙伴—图片对在其整个生命周期内最多获得 `3` 点重复经验。
- `life-core-v1` 的硬上限为：每次完整喂养 `60` 点生命经验、Asia/Shanghai 日历日 `300` 点生命经验、每次喂养性格绝对变化 `1.00`、每项技能每次喂养 `25` 点技能经验，以及 `80.00` 的性格绝对软上限。领域合法范围仍为 `[-100, 100]`；软上限让新唤醒的伙伴在本切片中不会通过喂养达到极端状态事件，同时不会强行收缩已恢复的合法值。
- 伙伴快照、技能行、已完成喂养运行和成长记录在同一个数据库事务中提交。成长记录只允许追加。Adapter 调用失败不会改变伙伴状态或成长历史，并会留下可重试的失败喂养运行。
- 每个喂养运行都有一个 UUID 关联 ID。只持久化 ID、数值增量、安全原因、安全错误码和时间戳；不要持久化图片内容、图片描述、凭据、提示词或第三方异常正文。
- `app.companion.feeding-enabled=false` 是全局停止开关。`application-prod.yaml` 会让后端演示功能默认关闭，除非显式提供 `COMPANION_ENABLED=true`；生产前端构建会省略路由/导航，除非 `VITE_COMPANION_ENABLED=true`；发布时必须有意启用两侧。
- 所有新表都通过 Liquibase 引入，并带有显式回滚块。在分片 profile 下，Liquibase 直接连接物理 MySQL 数据源，而 ShardingSphere 通过显式的 `!SINGLE` 规则路由伙伴表。
- 现有 `JacksonConfig` 会把后端 `Long` 值序列化为 JSON 字符串；前端必须将所有用户、空间、图片、伙伴、修订号和成长记录 ID 保持为字符串。
- 伙伴领域分支覆盖率必须至少为 `85%`。真实 Chromium 测试必须覆盖登录/会话初始化、唤醒、已授权私有图片喂养、使用同一幂等键的模糊响应重试、持久化成长和重新加载。源字符串断言不能替代此浏览器流程。
- 本演示切片是本地/测试软件，不能作为生产视觉 AI 展示。面向公众的 Provider 支持发布仍需满足现有生产 CORS、密钥存储、真实 Provider 以及更广泛的发布门槛。

---

## 本计划锁定的范围决策

| 决策 | 精确契约 |
| --- | --- |
| 空状态 | `GET /companion/me` 以 HTTP 成功状态返回 `companion: null`；绝不自动唤醒。 |
| 唤醒 | `POST /companion/awaken` 具备幂等性，重复或并发调用都返回同一个伙伴。 |
| 喂养权限 | 现有 `SpaceUserPermissionConstant.PICTURE_VIEW` 是访问公共图片、私有空间图片或团队空间图片所需的权限。首个页面选择器列出主体最早创建的私有空间，而后端接受当前依据此规则获授权的任意图片。 |
| 演示分析 | `DEMO_DETERMINISTIC`；档案选择规则为 `Math.floorMod(pictureId, 3)`。不读取字节、URL、元数据或模型。 |
| 幂等保留 | 喂养运行在伙伴整个生命周期内保留。`FAILED` 运行可使用同一键重试；`REJECTED` 和 `COMPLETED` 运行保留原始结果。`PROCESSING` 运行可在五分钟后回收。 |
| 重复图片 | 每个新键都是一次新交互，但只有首次喂养获得完整营养。重访成长上限为每次交互 1 XP，且每个伙伴—图片对终身最多 3 XP。 |
| 每日边界 | Asia/Shanghai 本地午夜，由注入的 `Clock` 计算；持久化事件时间仍为 UTC `Instant`。 |
| 极端性格 | 新唤醒的 `life-core-v1` 伙伴不能通过喂养达到极端，因为平衡对象应用了 `±80.00` 软上限。已恢复的、位于该软范围之外的合法值不会被强行向内收缩：每次喂养最多只能将其向内移动 `1.00`，不能使其进一步向外移动。领域合法钳制仍为 `±100.00`，保留后续事件接缝。 |
| 历史隐私 | 只存储和展示来源图片 ID。不要把缩略图、URL、名称、标签、描述或推断出的特征复制到不可变成长历史中。 |
| 分片 | `picture` 仍保持分片。`user`、`space`、`space_user` 和四张伙伴表在 `primary` 上显式声明为单表。 |

## 稳定 HTTP 契约

所有端点使用现有的 `/api` servlet context 和 `{ code, message, data }` 响应封装。

```text
GET  /companion/me
POST /companion/awaken
POST /companion/feed
```

喂养请求体只有：

```json
{
  "pictureId": "102",
  "idempotencyKey": "6f26d166-0a82-4d9f-8a61-6c21cf2e59d0"
}
```

`GET /companion/me` 和 `POST /companion/awaken` 返回：

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

`POST /companion/feed` 在幂等重放时返回原始结果：

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

## 文件映射

### 迁移与配置

- 修改 `pom.xml`：添加 Liquibase，并强制伙伴领域分支覆盖率。
- 修改 `src/main/resources/application.yaml`：指向主 changelog，并定义伙伴功能/喂养开关。
- 修改 `src/main/resources/application-prod.yaml`：默认关闭生产环境中的演示功能。
- 修改 `src/main/resources/application-sharding-static.yaml`：为 Liquibase 提供直接的物理 MySQL 连接。
- 修改 `src/main/resources/application-sharding-dynamic.yaml`：为 Liquibase 提供直接的物理 MySQL 连接。
- 修改 `src/main/resources/sharding/static.yaml`：注册现有单表和伙伴单表。
- 修改 `src/main/resources/sharding/dynamic.yaml`：注册现有单表和伙伴单表。
- 修改 `src/test/resources/application-test.yaml`：为测试启用 Liquibase 和伙伴演示开关。
- 创建 `src/main/resources/db/changelog/db.changelog-master.xml`：包含版本化伙伴变更。
- 创建 `src/main/resources/db/changelog/changes/2026-08-11-companion-life-core.xml`：创建并回滚四张伙伴表。
- 创建 `scripts/migrate-companion-physical.ps1`：在 ShardingSphere 启动前，针对物理 MySQL 运行 changelog。
- 创建 `scripts/mvnw-java21.ps1`：当 `JAVA_HOME` 缺失时，让每次 Windows Maven 调用都能自包含运行。
- 创建 `src/test/java/com/li/lipicturecloud/migration/CompanionSchemaMigrationTest.java`：证明真实 H2 迁移可以应用并声明回滚。
- 修改 `src/test/java/com/li/lipicturecloud/sharding/ShardingModeConfigurationTest.java`：保护直接迁移和 `!SINGLE` 路由契约。
- 创建 `src/test/java/com/li/lipicturecloud/sharding/CompanionSingleTableRoutingIntegrationTest.java`：通过 ShardingSphere 加载两份真实规则 YAML，并操作已迁移的表。

### 无框架依赖的领域核心

- 创建 `src/main/java/com/li/lipicturecloud/domain/companion/Companion.java`：不可变伙伴聚合和 `feed` 行为。
- 创建 `src/main/java/com/li/lipicturecloud/domain/companion/CompanionTraits.java`：五轴值对象和合法范围校验。
- 创建 `src/main/java/com/li/lipicturecloud/domain/companion/TraitDelta.java`：五轴应用变化值。
- 创建 `src/main/java/com/li/lipicturecloud/domain/companion/CompanionStage.java`：`LIGHT`、`SEEDLING`、`COMPANION`。
- 创建 `src/main/java/com/li/lipicturecloud/domain/companion/CompanionSkill.java`：五个初始技能代码。
- 创建 `src/main/java/com/li/lipicturecloud/domain/companion/CompanionBalance.java`：不可变的 `life-core-v1` 曲线、限制和 Asia/Shanghai 日边界。
- 创建 `src/main/java/com/li/lipicturecloud/domain/companion/NutritionMode.java`：演示模式披露枚举。
- 创建 `src/main/java/com/li/lipicturecloud/domain/companion/PictureNutrition.java`：不受信任的 Adapter 观察候选值。
- 创建 `src/main/java/com/li/lipicturecloud/domain/companion/FeedingContext.java`：由应用层提供的既往图片和每日上限事实。
- 创建 `src/main/java/com/li/lipicturecloud/domain/companion/FeedingGrowth.java`：确定性的应用结果和应用后状态。
- 创建 `src/main/java/com/li/lipicturecloud/domain/companion/GrowthEventType.java`：`PICTURE_FED` 和 `PICTURE_REVISITED`。
- 创建 `src/main/java/com/li/lipicturecloud/domain/companion/GrowthRecord.java`：带应用后快照、只保留必要来源信息的追加式成长事实。
- 创建 `src/main/java/com/li/lipicturecloud/domain/companion/FeedingRun.java`：幂等/审计状态。
- 创建 `src/main/java/com/li/lipicturecloud/domain/companion/FeedingRunStatus.java`：`PROCESSING`、`COMPLETED`、`FAILED`、`REJECTED`。
- 创建 `src/main/java/com/li/lipicturecloud/domain/companion/CompanionRepository.java`：小型聚合持久化 Interface。
- 创建 `src/main/java/com/li/lipicturecloud/domain/companion/GrowthRecordRepository.java`：追加/历史/上限事实 Interface。
- 创建 `src/main/java/com/li/lipicturecloud/domain/companion/FeedingRunRepository.java`：预留和状态转换 Interface。
- 创建 `src/test/java/com/li/lipicturecloud/domain/companion/CompanionTest.java`：聚焦平衡和喂养示例。
- 创建 `src/test/java/com/li/lipicturecloud/domain/companion/FeedingRunTest.java`：合法/非法审计状态转换和值校验。
- 创建 `src/test/java/com/li/lipicturecloud/domain/companion/CompanionBalancePropertyTest.java`：确定性的随机不变量覆盖。

### 持久化 Adapter

- 创建 `src/main/java/com/li/lipicturecloud/model/entity/CompanionEntity.java`。
- 创建 `src/main/java/com/li/lipicturecloud/model/entity/CompanionSkillEntity.java`。
- 创建 `src/main/java/com/li/lipicturecloud/model/entity/CompanionFeedRunEntity.java`。
- 创建 `src/main/java/com/li/lipicturecloud/model/entity/CompanionGrowthRecordEntity.java`。
- 创建 `src/main/java/com/li/lipicturecloud/mapper/CompanionMapper.java`。
- 创建 `src/main/java/com/li/lipicturecloud/mapper/CompanionSkillMapper.java`。
- 创建 `src/main/java/com/li/lipicturecloud/mapper/CompanionFeedRunMapper.java`。
- 创建 `src/main/java/com/li/lipicturecloud/mapper/CompanionGrowthRecordMapper.java`。
- 创建 `src/main/java/com/li/lipicturecloud/infrastructure/persistence/companion/CompanionJsonCodec.java`：仅用于持久化的 JSON 载荷映射。
- 创建 `src/main/java/com/li/lipicturecloud/infrastructure/persistence/companion/MybatisCompanionRepository.java`。
- 创建 `src/main/java/com/li/lipicturecloud/infrastructure/persistence/companion/MybatisGrowthRecordRepository.java`。
- 创建 `src/main/java/com/li/lipicturecloud/infrastructure/persistence/companion/MybatisFeedingRunRepository.java`。
- 创建 `src/test/java/com/li/lipicturecloud/infrastructure/persistence/companion/CompanionPersistenceIntegrationTest.java`。

### 应用与 HTTP

- 创建 `src/main/java/com/li/lipicturecloud/config/CompanionConfiguration.java`：`Clock` 和 `life-core-v1` bean。
- 创建 `src/main/java/com/li/lipicturecloud/config/CompanionFeatureProperties.java`：功能、停止开关和过期运行设置。
- 创建 `src/main/java/com/li/lipicturecloud/application/companion/PictureNutritionAnalyzer.java`：可替换的营养 Interface。
- 创建 `src/main/java/com/li/lipicturecloud/application/companion/AuthorizedPictureRef.java`：受信任主体 + 已授权图片引用。
- 创建 `src/main/java/com/li/lipicturecloud/infrastructure/companion/DemoPictureNutritionAdapter.java`：确定性的伪造 Adapter。
- 创建 `src/main/java/com/li/lipicturecloud/application/companion/CompanionLife.java`：`home`、`awaken` 和 `feed` 应用 Interface。
- 创建 `src/main/java/com/li/lipicturecloud/application/companion/FeedPictureCommand.java`。
- 创建 `src/main/java/com/li/lipicturecloud/application/companion/FeedReservation.java`。
- 创建 `src/main/java/com/li/lipicturecloud/application/companion/CompanionFeedingCoordinator.java`：短小且隔离的预留/失败/完成事务。
- 创建 `src/main/java/com/li/lipicturecloud/application/companion/CompanionLifeService.java`：授权与流程编排。
- 创建 `src/main/java/com/li/lipicturecloud/application/companion/CompanionViewAssembler.java`。
- 创建 `src/main/java/com/li/lipicturecloud/application/companion/view/CompanionHomeView.java`。
- 创建 `src/main/java/com/li/lipicturecloud/application/companion/view/CompanionView.java`。
- 创建 `src/main/java/com/li/lipicturecloud/application/companion/view/CompanionTraitsView.java`。
- 创建 `src/main/java/com/li/lipicturecloud/application/companion/view/CompanionSkillView.java`。
- 创建 `src/main/java/com/li/lipicturecloud/application/companion/view/NutritionStatusView.java`。
- 创建 `src/main/java/com/li/lipicturecloud/application/companion/view/GrowthRecordView.java`。
- 创建 `src/main/java/com/li/lipicturecloud/application/companion/view/FeedPictureResult.java`。
- 创建 `src/test/java/com/li/lipicturecloud/application/companion/CompanionFeedingCoordinatorTest.java`。
- 创建 `src/test/java/com/li/lipicturecloud/application/companion/CompanionFeedingIntegrationTest.java`：H2 并发/事务证明。
- 创建 `src/test/java/com/li/lipicturecloud/application/companion/CompanionLifeServiceTest.java`。
- 创建 `src/test/java/com/li/lipicturecloud/infrastructure/companion/DemoPictureNutritionAdapterTest.java`：确定性伪档案和披露证明。
- 创建 `src/main/java/com/li/lipicturecloud/model/dto/companion/CompanionFeedRequest.java`。
- 创建 `src/main/java/com/li/lipicturecloud/controller/CompanionController.java`。
- 创建 `src/test/java/com/li/lipicturecloud/controller/CompanionControllerTest.java`。

### 前端与浏览器验收

- 创建 `li-picture-cloud-frontend/src/api/companion.js`。
- 修改 `li-picture-cloud-frontend/src/api/request.js`：保留 HTTP/封装错误元数据，以便安全地做同键重试决策。
- 修改 `li-picture-cloud-frontend/src/api/picture.js`：暴露现有的、未缓存且经过权限检查的图片页面端点。
- 创建 `li-picture-cloud-frontend/src/config/features.js`：除非显式启用，否则让生产构建中的伙伴 UI 默认关闭。
- 创建 `li-picture-cloud-frontend/src/constants/companion.js`。
- 创建 `li-picture-cloud-frontend/src/utils/authBootstrap.js`：可测试的单飞认证加载和终态错误分类。
- 创建 `li-picture-cloud-frontend/src/utils/companion.js`。
- 创建 `li-picture-cloud-frontend/tests/companion.test.mjs`。
- 修改 `li-picture-cloud-frontend/src/stores/user.js`：添加单飞认证就绪机制。
- 修改 `li-picture-cloud-frontend/src/App.vue`：调用就绪 action，而不是启动重复请求。
- 修改 `li-picture-cloud-frontend/src/router/index.js`：添加受保护的 `/companion` 路由。
- 修改 `li-picture-cloud-frontend/src/constants/navigation.js`：为已认证用户添加 `我的伙伴`。
- 修改 `li-picture-cloud-frontend/src/components/NavBar.vue`：把生产安全的伙伴 UI 开关传入导航模型。
- 修改 `li-picture-cloud-frontend/tests/navigation.test.mjs`。
- 修改 `li-picture-cloud-frontend/tests/responsiveViews.test.mjs`。
- 创建 `li-picture-cloud-frontend/src/components/companion/CompanionStats.vue`。
- 创建 `li-picture-cloud-frontend/src/components/companion/CompanionPicturePicker.vue`。
- 创建 `li-picture-cloud-frontend/src/components/companion/CompanionGrowthTimeline.vue`。
- 创建 `li-picture-cloud-frontend/src/views/CompanionView.vue`。
- 创建 `src/test/resources/application-e2e.yaml`。
- 创建 `src/test/resources/e2e-schema.sql`。
- 创建 `src/test/resources/e2e-data.sql`。
- 创建 `li-picture-cloud-frontend/scripts/start-e2e-backend.mjs`。
- 创建 `li-picture-cloud-frontend/playwright.config.js`。
- 创建 `li-picture-cloud-frontend/e2e/companion.spec.js`。
- 修改 `li-picture-cloud-frontend/vite.config.js`：允许隔离的 E2E 代理目标，同时不改变正常本地默认值。
- 修改 `li-picture-cloud-frontend/package.json` 和 `li-picture-cloud-frontend/package-lock.json`。
- 修改 `li-picture-cloud-frontend/Dockerfile`：接受生产伙伴构建开关。
- 修改 `li-picture-cloud-frontend/eslint.config.js` 和 `.gitignore`。
- 修改 `.github/workflows/ci.yml`。
- 修改 `compose.yaml` 和 `.env.example`：通过现有部署路径传递后端和前端伙伴开关。
- 修改 `src/test/java/com/li/lipicturecloud/deployment/DeploymentArtifactsTest.java`：保护这些构建/运行时契约。
- 创建 `docs/round-19-companion-life-core-guide.md`。
- 修改 `README.md`：链接指南并说明确定性演示边界。

---
### 任务 1：引入可回滚的伙伴模式和单表路由

**文件：**
- 修改：`docs/superpowers/plans/2026-08-11-companion-life-core.md`（用第一次实现提交跟踪此计划）
- 修改：`pom.xml`
- 修改：`src/main/resources/application.yaml`
- 修改：`src/main/resources/application-prod.yaml`
- 修改：`src/main/resources/application-sharding-static.yaml`
- 修改：`src/main/resources/application-sharding-dynamic.yaml`
- 修改：`src/main/resources/sharding/static.yaml`
- 修改：`src/main/resources/sharding/dynamic.yaml`
- 修改：`src/test/resources/application-test.yaml`
- 创建：`src/main/resources/db/changelog/db.changelog-master.xml`
- 创建：`src/main/resources/db/changelog/changes/2026-08-11-companion-life-core.xml`
- 创建：`scripts/migrate-companion-physical.ps1`
- 创建：`scripts/mvnw-java21.ps1`
- 创建：`src/test/java/com/li/lipicturecloud/migration/CompanionSchemaMigrationTest.java`
- 修改：`src/test/java/com/li/lipicturecloud/sharding/ShardingModeConfigurationTest.java`
- 创建：`src/test/java/com/li/lipicturecloud/sharding/CompanionSingleTableRoutingIntegrationTest.java`

**Interface：**
- 消费：现有的直接 MySQL/H2 `DataSource`、两个 ShardingSphere `primary` 存储单元以及 Spring Boot Liquibase 配置。
- 产出：`companion`、`companion_skill`、`companion_feed_run` 和 `companion_growth_record` 表；唯一键 `uk_companion_user`、`uk_companion_skill`、`uk_companion_feed_key` 和 `uk_companion_growth_run`；可恢复/可回滚的 changeSet `20260811-01` 至 `20260811-07`；可执行的物理 MySQL 预迁移；显式的 `!SINGLE` 路由。

- [ ] **步骤 0：创建并验证自包含的 Windows Java/Maven 包装脚本**

创建 `scripts/mvnw-java21.ps1`；之后所有 Windows Maven 命令都调用此文件，因为一次启动的 PowerShell 内部对环境的修改不会持久到下一次调用：

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

运行：

```powershell
.\scripts\mvnw-java21.ps1 -version
```

预期结果：Maven 报告 Java `21`。CI 从 `actions/setup-java` 获取 `JAVA_HOME`；不要把特定机器上的 JDK 路径加入仓库。

- [ ] **步骤 1：编写失败的迁移测试**

创建 `CompanionSchemaMigrationTest`，使用隔离的真实 Liquibase/H2 数据库。让 Boot 测试 profile 继续使用正常的 H2 URL，但不要使用 Boot 的 `test-rollback-on-update`：该选项会让 Liquibase 打开第二个连接，而 H2 的 `DATABASE_TO_LOWER=TRUE` 测试 URL 可能导致第二个连接找不到第一个连接创建的 `DATABASECHANGELOG` 表。下面的显式测试针对普通 MySQL 模式 H2 数据库，证明更强的契约：更新 → 完整回滚 → 再次更新。

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

- [ ] **步骤 2：扩展分片契约测试并验证 RED**

将以下测试方法添加到 `ShardingModeConfigurationTest`：

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

添加 `import java.util.List;`，然后运行：

```powershell
.\scripts\mvnw-java21.ps1 "-Dtest=CompanionSchemaMigrationTest,ShardingModeConfigurationTest" test
```

预期结果：`CompanionSchemaMigrationTest` 报告缺少表/changelog，新增加的分片断言失败。

同时将以下源码契约添加到 `CompanionSchemaMigrationTest`：

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

- [ ] **步骤 3：启用 Liquibase，但不修改旧版引导脚本**

将受管理的依赖添加到 `pom.xml`：

```xml
<dependency>
    <groupId>org.liquibase</groupId>
    <artifactId>liquibase-core</artifactId>
</dependency>
```

在 `build.plugins` 下添加这个未绑定的插件；只有操作人员显式调用 `liquibase:update` 时才运行。Spring Boot 3.5.14 通过其 parent 提供 `liquibase.version=4.31.1` 和 `mysql.version=9.7.0`：

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

不要将此目标绑定到应用生命周期阶段，也不要在源代码或 Maven 命令行中放置密码。

保留 `sql/user.sql`、`sql/picture.sql` 和 `sql/space.sql` 及其 Docker Compose 挂载不变。在 `application.yaml` 的基础 `spring` 块中添加：

```yaml
  liquibase:
    enabled: true
    change-log: classpath:/db/changelog/db.changelog-master.xml
```

在现有的 `app` 块中添加：

```yaml
  companion:
    enabled: ${COMPANION_ENABLED:true}
    feeding-enabled: ${COMPANION_FEEDING_ENABLED:true}
    processing-timeout: ${COMPANION_PROCESSING_TIMEOUT:5m}
```

在 `application-prod.yaml` 的 `app` 下添加生产环境覆盖：

```yaml
  companion:
    enabled: ${COMPANION_ENABLED:false}
    feeding-enabled: ${COMPANION_FEEDING_ENABLED:false}
```

在 `application-test.yaml` 中添加：

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

将这些键合并到现有的 `spring` 和 `app` 映射中；不要创建重复的 YAML 顶层键。

- [ ] **步骤 4：创建精确的版本化模式并实现回滚**

创建主 changelog：

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

将子 changelog 创建为七个有序的 changeSet，每个 changeSet 对应一条 MySQL 自动提交的 DDL 语句。Liquibase 会记录每条成功的语句，因此物理预迁移失败后会从第一个尚未应用的 changeSet 恢复，而不会与本次运行中更早创建的表冲突。保持表按父表优先创建；Liquibase 会按相反顺序回滚 changeSet，从而在父表之前移除索引和子表。使用下面这份精确的可移植内容，不要声称 DDL 具有事务性：

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

不要添加外键或逻辑删除列。跨上下文的用户/图片 ID 在应用层授权，而喂养运行和成长事实都必须保持可审计。

- [ ] **步骤 5：在分片 profile 中为 Liquibase 提供物理数据源**

在两份分片 profile 文件的 `spring` 下合并以下块：

```yaml
  liquibase:
    enabled: true
    change-log: classpath:/db/changelog/db.changelog-master.xml
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/${MYSQL_DATABASE:li_picture_cloud_data}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    user: ${MYSQL_USERNAME:root}
    password: ${MYSQL_PASSWORD}
```

此 URL 必须与 `spring.datasource.url=jdbc:shardingsphere:classpath:sharding/static.yaml` 和 `jdbc:shardingsphere:classpath:sharding/dynamic.yaml` 分开。预迁移之后，它是一条校验/空操作安全路径；由于 Boot 会在运行 Liquibase 初始化器之前构建主 ShardingSphere `DataSource`，它无法建立冷启动顺序。

创建 `scripts/migrate-companion-physical.ps1`：

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

每次分片部署引入表/规则变更时，都导出这五个物理 MySQL 变量，并在启动任一分片 profile 之前运行 `powershell -File scripts/migrate-companion-physical.ps1`。Linux 操作人员设置相同的环境变量，并运行 `./mvnw -q liquibase:update`。只有当该命令以 `0` 退出后，ShardingSphere 才可以初始化并加载声明的单表元数据。

- [ ] **步骤 6：在两份 ShardingSphere 规则文件中注册显式单表**

在 `sharding/static.yaml` 和 `sharding/dynamic.yaml` 的现有 `!SHARDING` 规则后追加以下第二条规则：

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

不要列出逻辑表 `picture`、物理图片分片或 Liquibase 元数据表。这遵循官方的 [ShardingSphere 5.5.2 单表 YAML 规则](https://shardingsphere.apache.org/document/5.5.2/en/user-manual/shardingsphere-jdbc/yaml-config/rules/single/)，并在同一个接缝处修复现有 `user`/`space`/`space_user` 的加载缺口。

- [ ] **步骤 7：针对已迁移的物理数据库运行两份真实 ShardingSphere YAML**

创建 `CompanionSingleTableRoutingIntegrationTest`，以 `sharding/static.yaml` 和 `sharding/dynamic.yaml` 为参数化测试对象。针对每个资源：

1. 使用 `MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1` 创建名称唯一的 H2 `HikariDataSource`。
2. 通过物理数据源创建最小的 `user`、`space` 和 `space_user` 表，每张表包含 `id` 和可变的 `marker` 列；同时创建包含 `id`、`userId` 和 `spaceId` 的 `picture_0` 至 `picture_3` 表。
3. 使用 `SpringLiquibase` 针对该物理数据源运行真实主 changelog。
4. 读取规则资源，只将其内嵌的 `dataSources` 部分替换为 `{}`，使工厂使用 `Map.of("primary", physical)`。对于这次 H2 驱动冒烟测试，将 `!SINGLE` 表清单替换为 `primary.*`：H2 大小写规范化后的元数据无法在工厂初始化期间加载检入仓库的 MySQL 显式表标识符。通过 `ShardingModeConfigurationTest` 保护检入仓库的精确 `!SINGLE` 清单；不要为了适配 H2 修改生产 YAML。
5. 通过返回的 ShardingSphere 数据源，对旧版 `user`、`space`、`space_user` 以及 `companion`、`companion_skill`、`companion_feed_run`、`companion_growth_record` 执行插入、查询、更新和删除；按子表优先顺序删除伙伴行。
6. 在 `finally` 中关闭路由数据源和物理数据源。

使用固定有效值：伙伴/用户 `8100`，独立的旧版路由 ID `8201..8203`，图片 `9100`，小写键 `routing-feed-key-01`，64 字符小写 fingerprint，UUID 关联 ID，`PROCESSING`，`DEMO_DETERMINISTIC`，`{}` JSON 字符串以及 `life-core-v1`。断言每次更新的计数都是 `1`，并且通过路由连接执行的最终查询能看到更新后的值。这明确保护新 `!SINGLE` 规则改变的现有 user/space/space_user 路由，而不仅是四张新表。不要把 YAML 重写成只用于测试的近似版本，也不要用源码字符串断言替换此测试。

- [ ] **步骤 8：运行迁移和分片验证**

```powershell
.\scripts\mvnw-java21.ps1 "-Dtest=CompanionSchemaMigrationTest,ShardingModeConfigurationTest,CompanionSingleTableRoutingIntegrationTest" test
```

预期结果：Liquibase 在 H2 上记录全部七个 changeSet，完成更新 → 反向回滚测试 → 再次更新；三项测试全部通过，测试执行时四张应用表都存在，两份已检入仓库的 ShardingSphere 规则文件都将真实 CRUD 路由到 `primary`。

- [ ] **步骤 9：提交模式基础**

```powershell
git add pom.xml scripts/mvnw-java21.ps1 scripts/migrate-companion-physical.ps1 src/main/resources/application.yaml src/main/resources/application-prod.yaml src/main/resources/application-sharding-static.yaml src/main/resources/application-sharding-dynamic.yaml src/main/resources/sharding/static.yaml src/main/resources/sharding/dynamic.yaml src/test/resources/application-test.yaml src/main/resources/db/changelog/db.changelog-master.xml src/main/resources/db/changelog/changes/2026-08-11-companion-life-core.xml src/test/java/com/li/lipicturecloud/migration/CompanionSchemaMigrationTest.java src/test/java/com/li/lipicturecloud/sharding/ShardingModeConfigurationTest.java src/test/java/com/li/lipicturecloud/sharding/CompanionSingleTableRoutingIntegrationTest.java docs/superpowers/plans/2026-08-11-companion-life-core.md
git commit -m "build: add companion schema migrations"
```

---

### 任务 2：构建确定性的伙伴领域核心

**文件：**
- 修改：`pom.xml`
- 创建：`src/main/java/com/li/lipicturecloud/domain/companion/Companion.java`
- 创建：`src/main/java/com/li/lipicturecloud/domain/companion/CompanionTraits.java`
- 创建：`src/main/java/com/li/lipicturecloud/domain/companion/TraitDelta.java`
- 创建：`src/main/java/com/li/lipicturecloud/domain/companion/CompanionStage.java`
- 创建：`src/main/java/com/li/lipicturecloud/domain/companion/CompanionSkill.java`
- 创建：`src/main/java/com/li/lipicturecloud/domain/companion/CompanionBalance.java`
- 创建：`src/main/java/com/li/lipicturecloud/domain/companion/NutritionMode.java`
- 创建：`src/main/java/com/li/lipicturecloud/domain/companion/PictureNutrition.java`
- 创建：`src/main/java/com/li/lipicturecloud/domain/companion/FeedingContext.java`
- 创建：`src/main/java/com/li/lipicturecloud/domain/companion/FeedingGrowth.java`
- 创建：`src/main/java/com/li/lipicturecloud/domain/companion/GrowthEventType.java`
- 创建：`src/main/java/com/li/lipicturecloud/domain/companion/GrowthRecord.java`
- 创建：`src/main/java/com/li/lipicturecloud/domain/companion/FeedingRun.java`
- 创建：`src/main/java/com/li/lipicturecloud/domain/companion/FeedingRunStatus.java`
- 创建：`src/main/java/com/li/lipicturecloud/domain/companion/CompanionRepository.java`
- 创建：`src/main/java/com/li/lipicturecloud/domain/companion/GrowthRecordRepository.java`
- 创建：`src/main/java/com/li/lipicturecloud/domain/companion/FeedingRunRepository.java`
- 创建：`src/test/java/com/li/lipicturecloud/domain/companion/CompanionTest.java`
- 创建：`src/test/java/com/li/lipicturecloud/domain/companion/FeedingRunTest.java`
- 创建：`src/test/java/com/li/lipicturecloud/domain/companion/CompanionBalancePropertyTest.java`

**Interface：**
- 消费：一个 `PictureNutrition` 候选值以及受信任的 `FeedingContext` 事实；不依赖 Spring、MyBatis、servlet、entity、JSON、模型或权限类型。
- 产出：`Companion.awaken(long, CompanionBalance)`、`Companion.restore(Long, long, long, int, CompanionStage, CompanionTraits, Map<CompanionSkill, Long>, String, long, CompanionBalance)`、`Companion.feed(PictureNutrition, FeedingContext, CompanionBalance)`、`CompanionBalance.v1()` 以及下方列出的三个 repository Interface。

确切的 repository 接口面如下：

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

- [ ] **步骤 1：编写聚焦的失败示例**

使用以下用例创建 `CompanionTest`：

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

- [ ] **步骤 2：运行聚焦测试并验证 RED**

```powershell
.\scripts\mvnw-java21.ps1 -Dtest=CompanionTest test
```

预期结果：由于领域类型尚不存在，编译失败。

- [ ] **步骤 3：定义枚举和不可变值记录**

使用以下精确枚举：

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

将 `CompanionTraits` 和 `TraitDelta` 实现为 record，包含五个 `BigDecimal` 字段：`curiosity`、`enthusiasm`、`playfulness`、`empathy` 和 `creativity`。使用 `RoundingMode.HALF_UP` 将每个字段规范化到小数位 `2`；拒绝 null；让 `CompanionTraits` 拒绝 `[-100.00, 100.00]` 之外的值；提供 `neutral()` 和 `zero()` 工厂方法。

严格实现观察值和上下文契约：

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

- [ ] **步骤 4：实现版本化平衡对象**

`CompanionBalance.v1()` 是包含这些常量的唯一位置。使用整数运算和确定性钳制实现其计算：

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

添加 `version()`、`nextLevelExperience(long)`、`nextSkillLevelExperience(long)`、`fullFeedExperience(requested, earnedToday)`、`revisitExperience(earnedToday, earnedForPicture)`、`skillExperience(requested)` 和 `applyTrait(current, requested)` 方法。它们的精确公式为：

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

因此，恢复出的 `100.00` 加上向内的 `-5.00` 请求后变为 `99.00`，而不是 `80.00`；向外的正值请求应用 `0.00`。在 `-100.00` 处对称处理这些情况。将四个示例都加入 `CompanionTest`，确保软上限不会违反每次喂养的硬性移动上限。

- [ ] **步骤 5：实现不可变聚合和成长结果**

使用以下精确的聚合状态和 feed 签名：

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

`private Companion grow(long experienceDelta, TraitDelta traitDelta, Map<CompanionSkill, Long> skillDelta, CompanionBalance balance)` 必须使用 `Math.addExact`，从新的累计经验推导等级/阶段，使用 `Math.addExact` 增加独立的技能经验，使用 `Math.addExact` 增加修订号，并返回新的聚合。`restore(Long id, long ownerId, long lifeExperience, int level, CompanionStage lifeStage, CompanionTraits traits, Map<CompanionSkill, Long> skillExperience, String balanceVersion, long revision, CompanionBalance balance)` 必须校验持久化 ID、非负经验/技能/修订号、在给定平衡下的等级/阶段一致性、合法性格，以及完整的防御性技能映射。

定义结果 record：

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

- [ ] **步骤 6：定义追加式事实和 repository Interface**

`GrowthRecord` 包含 `Long id`、`long feedingRunId`、`long companionId`、`long pictureId`、`GrowthEventType eventType`、各项增量、完整的 `Companion companionAfter` 快照、安全原因、营养模式、`boolean contentUnderstood`、平衡版本、幂等键、关联 ID 和 `Instant createdTime`。提供 `GrowthRecord.from(long feedingRunId, long companionId, long pictureId, FeedingGrowth growth, NutritionMode nutritionMode, boolean contentUnderstood, String idempotencyKey, String correlationId, Instant createdTime)` 和 `withId(long)`；不存在任何修改器或修订方法。`PictureNutrition` 特意没有 mode 字段：候选 Adapter 不能覆盖已绑定到受信任喂养运行中的模式。

`FeedingRun` 包含 `Long id`、伙伴/主体/图片 ID、键、fingerprint、关联 ID、状态、营养模式、`contentUnderstood`、可选的结果成长 ID、可选的安全错误代码/消息/时间、尝试次数、修订号以及创建/更新时间。提供工厂方法 `processing(long companionId, long subjectId, long pictureId, String idempotencyKey, String requestFingerprint, String correlationId, NutritionMode mode, boolean contentUnderstood, Instant now)`、`persistedAs(long id)`、`restarted(Instant now)`、`completed(long growthRecordId, Instant now)`、`failed(String safeCode, String safeMessage, Instant now)` 和 `rejected(String safeCode, String safeMessage, Instant now)`；每次状态转换都会增加修订号，并且绝不改变主体、图片、键、fingerprint、关联 ID 或模式。processing 工厂独立校验正数 ID、`[a-z0-9_-]{16,64}`、64 位小写十六进制 fingerprint、有效 UUID 关联 ID 和非 null 模式/时间，因此非 HTTP 调用方也无法绕过 MySQL/H2 稳定的键格式。`failed`/`rejected` 设置全部三个安全错误字段；`restarted` 和 `completed` 保留这些字段，使恢复的运行仍能披露上一次安全失败，而不会存储第三方细节。

使用本任务 Interface 块中的精确签名创建三个 repository Interface。它们保留在 `domain/companion` 下，只导入 JDK/领域类型。

- [ ] **步骤 7：覆盖喂养运行转换和无效值边界**

创建 `FeedingRunTest`：

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

让每个 `FeedingRun` 转换都严格按这些测试要求校验合法的来源状态。在 `CompanionTest` 中添加边界用例：生命阈值 `99 → level 1`、`100 → level 2`、`299 → level 2`、`300 → level 3`，两端 ± 限制上的性格请求，以及每次喂养和每日上限下的完整喂养 XP。

- [ ] **步骤 8：添加确定性的随机不变量覆盖**

创建 `CompanionBalancePropertyTest`：

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

让两个五轴 record 都以规范轴顺序，通过不可变列表暴露 `values()`。

- [ ] **步骤 9：运行领域和架构测试**

```powershell
.\scripts\mvnw-java21.ps1 "-Dtest=CompanionTest,FeedingRunTest,CompanionBalancePropertyTest,DomainDependencyTest" test
```

预期结果：所有聚焦示例、5,000 次确定性转换以及框架依赖规则均通过。

- [ ] **步骤 10：为领域包强制执行 85% 分支下限**

向现有 JaCoCo 插件添加一个 `check` 执行：

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
                    <include>com/li/lipicturecloud/domain/companion</include>
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

运行：

```powershell
.\scripts\mvnw-java21.ps1 -Dspring.profiles.active=test verify
```

预期结果：构建成功，JaCoCo 检查报告 `com/li/lipicturecloud/domain/companion` 至少达到 `0.85` 的分支覆盖率。JaCoCo 使用 `/` 匹配包的内部名称，而不是 Java 源码中的 `.` 表示法；保持这个精确的 include，避免规则静默匹配到零个包。确认 `target/site/jacoco/jacoco.xml` 包含 `<package name="com/li/lipicturecloud/domain/companion">`。如果规则报告某个领域分支未覆盖，在继续之前向某个领域测试添加具体边界示例；不要降低阈值或排除领域类。

- [ ] **步骤 11：提交领域核心**

```powershell
git add pom.xml src/main/java/com/li/lipicturecloud/domain/companion src/test/java/com/li/lipicturecloud/domain/companion
git commit -m "feat: model deterministic companion growth"
```

---

### 任务 3：持久化伙伴状态、喂养运行和追加式成长

**文件：**
- 创建：`src/main/java/com/li/lipicturecloud/model/entity/CompanionEntity.java`
- 创建：`src/main/java/com/li/lipicturecloud/model/entity/CompanionSkillEntity.java`
- 创建：`src/main/java/com/li/lipicturecloud/model/entity/CompanionFeedRunEntity.java`
- 创建：`src/main/java/com/li/lipicturecloud/model/entity/CompanionGrowthRecordEntity.java`
- 创建：`src/main/java/com/li/lipicturecloud/mapper/CompanionMapper.java`
- 创建：`src/main/java/com/li/lipicturecloud/mapper/CompanionSkillMapper.java`
- 创建：`src/main/java/com/li/lipicturecloud/mapper/CompanionFeedRunMapper.java`
- 创建：`src/main/java/com/li/lipicturecloud/mapper/CompanionGrowthRecordMapper.java`
- 创建：`src/main/java/com/li/lipicturecloud/infrastructure/persistence/companion/CompanionJsonCodec.java`
- 创建：`src/main/java/com/li/lipicturecloud/infrastructure/persistence/companion/MybatisCompanionRepository.java`
- 创建：`src/main/java/com/li/lipicturecloud/infrastructure/persistence/companion/MybatisGrowthRecordRepository.java`
- 创建：`src/main/java/com/li/lipicturecloud/infrastructure/persistence/companion/MybatisFeedingRunRepository.java`
- 创建：`src/test/java/com/li/lipicturecloud/infrastructure/persistence/companion/CompanionPersistenceIntegrationTest.java`

**Interface：**
- 消费：任务 2 的三个领域 repository Interface、MyBatis `BaseMapper`、任务 1 的四张表以及应用配置的 Jackson `ObjectMapper`。
- 产出：支持竞态安全的 `createIfAbsent`、行锁式 `findByOwnerIdForUpdate`、修订号 compare-and-set `save`、完整的技能 upsert、有序历史/上限查询以及 compare-and-set 喂养运行转换。

所有 entity ID 都使用 `@TableId(type = IdType.ASSIGN_ID)`。任何 entity 都不得使用 `@TableLogic`、`@Version` 或将领域类作为字段。

- [ ] **步骤 1：为唯一性和乐观保存编写失败的 H2 集成测试**

用以下内容开始 `CompanionPersistenceIntegrationTest`：

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

添加一个真实的双线程用例，使用其他测试未使用的用户 ID：

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

添加 `import org.springframework.transaction.annotation.Propagation;`。此方法会禁用类级测试事务，因此两个 worker 的提交以及 `finally` 中的清理都使用真实数据库提交。显式清理只针对主体 `599`；如果此处仍保持类级事务，清理会被回滚，从而泄漏 worker 线程创建的行。

- [ ] **步骤 2：添加失败的喂养运行和成长往返用例**

添加：

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

在测试本地实现 `sha256(String value)`：使用 `value.getBytes(StandardCharsets.UTF_8)` 进行哈希，调用 `MessageDigest.getInstance("SHA-256")`，并返回 `HexFormat.of().formatHex(digest)`。

- [ ] **步骤 3：运行集成测试并验证 RED**

```powershell
.\scripts\mvnw-java21.ps1 -Dtest=CompanionPersistenceIntegrationTest test
```

预期结果：测试类能够针对任务 2 的 Interface 编译，然后 Spring 测试上下文失败，因为尚不存在 `CompanionRepository`、`GrowthRecordRepository` 或 `FeedingRunRepository` 实现 bean。

- [ ] **步骤 4：创建四个仅用于持久化的 entity**

遵循现有的 Lombok/MyBatis 风格。`CompanionEntity` 开始如下：

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

创建其余 entity，并严格保持表列一致：

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

可为 null 的列使用包装类型，所有时间戳使用 `Date`。每个 ID 都设置 `@TableId(type = IdType.ASSIGN_ID)`。

- [ ] **步骤 5：按精确的锁和聚合查询面创建 mapper**

`CompanionMapper`、`CompanionSkillMapper`、`CompanionFeedRunMapper` 和 `CompanionGrowthRecordMapper` 分别继承 `BaseMapper<CompanionEntity>`、`BaseMapper<CompanionSkillEntity>`、`BaseMapper<CompanionFeedRunEntity>` 和 `BaseMapper<CompanionGrowthRecordEntity>`。向 `CompanionMapper` 添加以下锁查询：

```java
@Select("SELECT * FROM companion WHERE userId = #{userId} LIMIT 1 FOR UPDATE")
CompanionEntity selectByUserIdForUpdate(@Param("userId") long userId);
```

向 `CompanionGrowthRecordMapper` 添加以下查询：

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

任何 mapper 都不得专门为 `companion_growth_record` 暴露更新或删除方法。

- [ ] **步骤 6：实现持久化 JSON 映射，避免 Jackson 泄漏到领域层**

`CompanionJsonCodec` 是位于 `infrastructure/persistence/companion` 下的 Spring component。使用私有 payload record，而不是序列化聚合类：

```java
record CompanionSnapshotPayload(
        Long id, long ownerId, long lifeExperience, int level, String lifeStage,
        BigDecimal curiosity, BigDecimal enthusiasm, BigDecimal playfulness,
        BigDecimal empathy, BigDecimal creativity,
        Map<String, Long> skills, String balanceVersion, long revision) {}
```

暴露：

```java
String writeTraitDelta(TraitDelta value);
TraitDelta readTraitDelta(String json);
String writeSkillDelta(Map<CompanionSkill, Long> value);
Map<CompanionSkill, Long> readSkillDelta(String json);
String writeSnapshot(Companion companion);
Companion readSnapshot(String json, CompanionBalance balance);
```

通过 `CompanionSkill.name()` 映射枚举键，将所有缺失的技能代码重建为零；并将 `JsonProcessingException` 包装为 `IllegalStateException("伙伴持久化数据无法解析", cause)`。失败时绝不记录原始 JSON。

为 `MybatisCompanionRepository`、`MybatisGrowthRecordRepository` 和 `MybatisFeedingRunRepository` 添加 Spring `@Repository` 注解，并且只对它们实际需要的 mapper 和 `CompanionJsonCodec` 使用构造器注入。该注解属于可执行契约：它注册每个领域 repository Interface 的实现，并保持持久化异常转换有效。任务 3 按下方规定通过锁定的 `CompanionBalance.v1()` 工厂重新水合；在任务 4 创建 bean 之前，不要注入 `CompanionBalance` bean，也不要依赖组件扫描实例化未注解的具体类。

- [ ] **步骤 7：实现支持竞态安全的伙伴创建和行重新水合**

`MybatisCompanionRepository` 使用构造器注入。其创建契约为：

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

`findByOwnerId` 使用 `LambdaQueryWrapper.eq(CompanionEntity::getUserId, ownerId)`。`findByOwnerIdForUpdate` 调用自定义 mapper。两者都加载 `companion_skill` 行，并调用 `Companion.restore(row.getId(), row.getUserId(), row.getLifeExperience(), row.getLevel(), CompanionStage.valueOf(row.getLifeStage()), traits(row), skills(rows), row.getBalanceVersion(), row.getRevision(), CompanionBalance.v1())`；在本切片中，存储的平衡版本不是 `life-core-v1` 时，使用 `IllegalStateException("不支持的伙伴平衡版本: " + version)` 拒绝。

- [ ] **步骤 8：实现 compare-and-set 聚合持久化**

`save` 先执行快照更新：

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

对每个规范的 `CompanionSkill`，按 `(companionId, skillCode)` 查询。缺少行且经验为正时插入；否则仅在持久化经验不同时更新。插入时发生唯一键冲突，必须在同一事务内重试为更新。不要删除零值行。

- [ ] **步骤 9：实现追加式成长和上限查询**

`MybatisGrowthRecordRepository.append` 分配 JSON payload，调用 `insert`，使用 `selectById(entity.getId())` 重新加载插入的行，并解码该数据库规范化后的行。返回重新加载的时间戳，使首次响应在幂等重放时可以逐字节重建。`findByFeedingRunId` 和 `findRecent` 解码每个 payload，并返回不可变领域记录。将 `findRecent` 限制在 `1..50`，并按以下条件排序：

```java
new LambdaQueryWrapper<CompanionGrowthRecordEntity>()
        .eq(CompanionGrowthRecordEntity::getCompanionId, companionId)
        .orderByDesc(CompanionGrowthRecordEntity::getCreateTime)
        .orderByDesc(CompanionGrowthRecordEntity::getId)
        .last("LIMIT " + safeLimit)
```

将 `hasFullFeed`、`sumLifeExperienceSince` 和 `sumRevisitExperience` 委托给步骤 5 的三个 mapper 查询。每日边界使用 `Date.from(since)`。

- [ ] **步骤 10：实现 compare-and-set 喂养运行转换**

`MybatisFeedingRunRepository.findByKey` 查询唯一键对。`insert` 映射一个 `PROCESSING` 运行，并返回带分配 ID 的运行。使用一个包含当前修订号和合法来源状态的共享更新方法实现全部四种转换：

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

`restart` 重新加载当前行，构建 `current.restarted(now)`，并允许 `FAILED → PROCESSING` 或过期的 `PROCESSING → PROCESSING`；compare-and-set 成功后，coordinator 重新加载新修订号，再返回 `STARTED`。`complete`、`fail` 和 `reject` 重新加载当前行，构建相应的领域目标，并只允许 `PROCESSING` 作为来源。这样既保留任务 2 的公开 repository 签名，也确保调用者总是使用持久化后的修订号继续操作。

- [ ] **步骤 11：运行持久化和迁移测试**

```powershell
.\scripts\mvnw-java21.ps1 "-Dtest=CompanionPersistenceIntegrationTest,CompanionSchemaMigrationTest,DomainDependencyTest" test
```

预期结果：所有测试通过。SQL 输出显示第二次过期快照更新影响 `0` 行，并且没有更新/删除语句针对 `companion_growth_record`。

- [ ] **步骤 12：提交持久化 Adapter**

```powershell
git add src/main/java/com/li/lipicturecloud/model/entity/CompanionEntity.java src/main/java/com/li/lipicturecloud/model/entity/CompanionSkillEntity.java src/main/java/com/li/lipicturecloud/model/entity/CompanionFeedRunEntity.java src/main/java/com/li/lipicturecloud/model/entity/CompanionGrowthRecordEntity.java src/main/java/com/li/lipicturecloud/mapper/CompanionMapper.java src/main/java/com/li/lipicturecloud/mapper/CompanionSkillMapper.java src/main/java/com/li/lipicturecloud/mapper/CompanionFeedRunMapper.java src/main/java/com/li/lipicturecloud/mapper/CompanionGrowthRecordMapper.java src/main/java/com/li/lipicturecloud/infrastructure/persistence/companion src/test/java/com/li/lipicturecloud/infrastructure/persistence/companion
git commit -m "feat: persist companion feeding history"
```

---

### 任务 4：通过演示 Adapter 编排已授权的幂等喂养

**文件：**
- 创建：`src/main/java/com/li/lipicturecloud/config/CompanionConfiguration.java`
- 创建：`src/main/java/com/li/lipicturecloud/config/CompanionFeatureProperties.java`
- 创建：`src/main/java/com/li/lipicturecloud/application/companion/PictureNutritionAnalyzer.java`
- 创建：`src/main/java/com/li/lipicturecloud/application/companion/AuthorizedPictureRef.java`
- 创建：`src/main/java/com/li/lipicturecloud/infrastructure/companion/DemoPictureNutritionAdapter.java`
- 创建：`src/main/java/com/li/lipicturecloud/application/companion/CompanionLife.java`
- 创建：`src/main/java/com/li/lipicturecloud/application/companion/FeedPictureCommand.java`
- 创建：`src/main/java/com/li/lipicturecloud/application/companion/FeedReservation.java`
- 创建：`src/main/java/com/li/lipicturecloud/application/companion/CompanionFeedingCoordinator.java`
- 创建：`src/main/java/com/li/lipicturecloud/application/companion/CompanionLifeService.java`
- 创建：`src/main/java/com/li/lipicturecloud/application/companion/CompanionViewAssembler.java`
- 创建：`src/main/java/com/li/lipicturecloud/application/companion/view/CompanionHomeView.java`
- 创建：`src/main/java/com/li/lipicturecloud/application/companion/view/CompanionView.java`
- 创建：`src/main/java/com/li/lipicturecloud/application/companion/view/CompanionTraitsView.java`
- 创建：`src/main/java/com/li/lipicturecloud/application/companion/view/CompanionSkillView.java`
- 创建：`src/main/java/com/li/lipicturecloud/application/companion/view/NutritionStatusView.java`
- 创建：`src/main/java/com/li/lipicturecloud/application/companion/view/GrowthRecordView.java`
- 创建：`src/main/java/com/li/lipicturecloud/application/companion/view/FeedPictureResult.java`
- 创建：`src/test/java/com/li/lipicturecloud/application/companion/CompanionFeedingCoordinatorTest.java`
- 创建：`src/test/java/com/li/lipicturecloud/application/companion/CompanionFeedingIntegrationTest.java`
- 创建：`src/test/java/com/li/lipicturecloud/application/companion/CompanionLifeServiceTest.java`
- 创建：`src/test/java/com/li/lipicturecloud/infrastructure/companion/DemoPictureNutritionAdapterTest.java`

**Interface：**
- 消费：已认证的 `AuthorizationSubject`、`SpaceAuthorizationAccessService.checkForUser(PICTURE_VIEW, pictureId, subject.userId())`、领域 repository、注入的 `Clock` 以及演示分析器。
- 产出：

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

应用结果 record 为：

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

- [ ] **步骤 1：为安全授权和重放编写失败的编排测试**

使用 Mockito 构造器 mock 和固定时钟创建 `CompanionLifeServiceTest`。覆盖以下精确行为：

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

使用 `ErrorCode.NO_AUTH_ERROR` 重复第一个测试，并断言相同的外部 code/message。测试 `feedingEnabled=false` 时，在 reservation 之前以 `伙伴喂养已暂停` 拒绝。测试分析器失败时调用 `coordinator.fail(run, "NUTRITION_FAILED", "本次没有消化成功，图片未被消耗")`，并且绝不调用 `complete`。

在两个应用测试中使用以下精确的类内 fixture；`CompanionViewAssembler` 是由 `CompanionBalance.v1()` 和 mock 分析器支持的真实实例：

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

- [ ] **步骤 2：编写失败的 reservation 和完成测试**

使用以下核心用例创建 `CompanionFeedingCoordinatorTest`：

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

另外测试：`FAILED` 运行使用相同关联 ID/键重启并增加尝试次数；未过期的 `PROCESSING` 运行返回 `IN_PROGRESS`；过期运行重启；成长行缺失的 `COMPLETED` 运行抛出 `喂养回执不完整`；伙伴 CAS 返回 false 时，在追加成长之前抛出 `伙伴状态已变化，请重试`。

- [ ] **步骤 3：运行两项测试并验证 RED**

```powershell
.\scripts\mvnw-java21.ps1 "-Dtest=CompanionLifeServiceTest,CompanionFeedingCoordinatorTest" test
```

预期结果：由于应用 Interface、record 和 service 尚不存在，编译失败。

- [ ] **步骤 4：绑定功能开关、平衡和时钟**

实现可变配置属性，使 Boot 能够绑定现有 YAML：

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

创建配置：

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

- [ ] **步骤 5：实现确定性的伪营养 Adapter**

根据本任务 Interface 块创建 Interface 和授权引用。实现：

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

创建 `src/test/java/com/li/lipicturecloud/infrastructure/companion/DemoPictureNutritionAdapterTest.java`；对每个档案调用两次并断言结果相等、`contentUnderstood=false`，且不依赖墙上时钟。

- [ ] **步骤 6：实现 view record 和唯一 assembler**

严格按照本任务 Interface 块中的清单创建七个 view record。`CompanionViewAssembler` 负责所有领域到传输对象的映射：

为 `CompanionViewAssembler` 添加 `@Component` 注解，并使用构造器注入 `CompanionBalance` 和 `PictureNutritionAnalyzer`；整个应用中必须只有一个 assembler bean。

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

`GrowthRecordView growth(GrowthRecord record)` 将枚举技能键映射为其 `name()`，使用存储的应用后快照，并从不可变成长事实映射 `contentUnderstood`。`NutritionStatusView nutritionStatus()` 精确返回：

```java
new NutritionStatusView(
        analyzer.mode().name(),
        analyzer.contentUnderstood(),
        "仅根据图片 ID 选择固定营养档案，未读取图片内容，也未调用视觉模型。");
```

- [ ] **步骤 7：实现 reservation 状态和短事务**

定义：

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

为 `CompanionFeedingCoordinator` 添加 `@Service` 注解并使用构造器注入。保持 `reserve`、`reject`、`fail` 和 `complete` 为 public，使 Spring 能在 `CompanionLifeService` 调用 coordinator bean 时通过事务代理执行；不要将这些外部调用替换为 coordinator 自调用，否则会绕过 `REQUIRES_NEW` 和回滚语义。

`CompanionFeedingCoordinator.reserve(Companion companion, AuthorizationSubject subject, long pictureId, String idempotencyKey, String fingerprint, String correlationId, NutritionMode mode, boolean contentUnderstood)` 使用 `@Transactional(propagation = REQUIRES_NEW)`。它：

1. 加载 `(companionId, key)`。
2. 如果不存在，尝试 `runRepository.insert(FeedingRun.processing(companion.id(), subject.userId(), pictureId, idempotencyKey, fingerprint, correlationId, mode, contentUnderstood, clock.instant()))`；发生 `DuplicateKeyException` 时重新加载一次。
3. 如果图片或 fingerprint 不同，则以 `PARAMS_ERROR` 和 `幂等键已用于另一张图片` 拒绝。
4. 从 `findByFeedingRunId` 重建 `COMPLETED` 结果。
5. 原样返回已存储的 `REJECTED` 运行。
6. 使用 compare-and-set 重启 `FAILED`，或者重启早于 `now - processingTimeout` 的 `PROCESSING`；丢失 CAS 后重新加载。
7. 对新鲜的 `PROCESSING` 运行返回 `IN_PROGRESS`。

使用对 UTF-8 字符串 `pictureId=<decimal-id>` 计算的 SHA-256 作为请求 fingerprint。每次首次观察到新的客户端键时，只使用一次 `UUID.randomUUID().toString()` 作为其关联 ID。

`reject(FeedingRun run, String safeCode, String safeMessage)` 和 `fail(FeedingRun run, String safeCode, String safeMessage)` 使用 `REQUIRES_NEW` 并对运行执行 compare-and-set。如果另一个事务已经改变了它，则重新加载，并且只接受已经请求的终态；否则抛出 `喂养运行状态已变化，请重试`。

- [ ] **步骤 8：使用上限和乐观修订实现原子完成**

`complete(FeedingRun run, PictureNutrition nutrition)` 使用 `@Transactional(rollbackFor = Exception.class)`，并严格按以下顺序执行：

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

事务回滚可保证快照更新、技能 upsert、成长插入或运行完成失败时，不会留下部分成长。添加一个 coordinator 测试，让 mock `Clock` 返回 Asia/Shanghai 午夜之前的一个时间点以及之后的时间点；验证 `clock.instant()` 只调用一次，并且每日查询边界、成长 `createdTime` 和运行完成都由第一个时间点推导。这样可防止一次喂养在一天内计数，却被持久化到下一天。

- [ ] **步骤 9：实现公开的应用工作流**

为 `CompanionLifeService` 添加：

```java
@Service
@ConditionalOnProperty(prefix = "app.companion", name = "enabled",
        havingValue = "true", matchIfMissing = true)
```

在 `CompanionLifeService` 构造器中基于注入的 `PlatformTransactionManager` 构建一个读取模板：

```java
this.homeReadTransaction = new TransactionTemplate(transactionManager);
this.homeReadTransaction.setReadOnly(true);
this.homeReadTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
```

将 `home` 实现为 `homeReadTransaction.execute(status -> readHome(subject))`。`readHome` 只加载 `subject.userId()`，然后在同一个 repeatable-read 快照中加载聚合、技能以及最多 20 条最近成长记录。实现 `awaken` 时先调用支持竞态安全的 `createIfAbsent`，再调用 `home(subject)`；因为 `home` 使用显式模板，所以自调用不会绕过读取事务。不要将插入前读取/唯一键恢复放在同一个 repeatable-read 快照中，否则并发唤醒中失败的一方可能看不到胜者插入的行。

在 `CompanionLifeServiceTest` 中使用真实的 H2 `DataSourceTransactionManager` 构造此 service。让 mock 的伙伴和成长 repository 通过断言 `TransactionSynchronizationManager.isActualTransactionActive()`、`isCurrentTransactionReadOnly()` 以及 `getCurrentTransactionIsolationLevel() == Connection.TRANSACTION_REPEATABLE_READ` 来响应，然后返回伙伴/历史 fixture。这证明用于组装 home 的每个查询都位于显式快照边界内。

使用 `^[a-z0-9_-]{16,64}$` 校验喂养键；拒绝大写，而不是静默规范化，并为该边界添加应用测试。工作流顺序为：

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

严格实现以下 helper，使其绝不修改已完成/已拒绝的运行，也绝不记录异常消息：

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

为该路径添加编排测试，确保授权基础设施失败不会让新运行在 `PROCESSING` 中滞留五分钟。

授权完成后：`REPLAY` 返回 `reservation.replay()`；`REJECTED` 抛出 `BusinessException(ErrorCode.NO_AUTH_ERROR, reservation.run().safeErrorMessage())`；`IN_PROGRESS` 抛出 `BusinessException(ErrorCode.OPERATION_ERROR, "这次喂养还在消化中，请稍后重试")`；只有 `STARTED` 才调用 `analyzer.analyze(new AuthorizedPictureRef(command.subject(), command.pictureId()))` 和 `coordinator.complete(reservation.run(), nutrition)`。

分析器出现任何异常时，持久化 `NUTRITION_FAILED` 和 `本次没有消化成功，图片未被消耗`，只记录关联/主体/图片和异常类，然后抛出该安全消息。完成失败时，将仍处于处理中的运行标记为 `FAILED`，错误码为 `FEED_COMMIT_FAILED`，然后传播安全的 `OPERATION_ERROR`。不要将第三方的 `error.getMessage()` 放入日志、行或响应。

- [ ] **步骤 10：证明两个不同的并发请求不能同时获得完整营养**

使用真实 Spring 事务代理和 H2 repository 创建 `CompanionFeedingIntegrationTest`：

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

这个 `@SpringBootTest` 成功使用全部六个自动注入的切片 bean 构造，也是对三个 `@Repository` Adapter、代理 coordinator、service、assembler 和演示分析器进行 bean wiring 断言。使用任务 3 已定义的相同具体 `bd(String)` 和 `sha256(String)` helper。两个键满足 16 字符的最小长度。H2 必须显示一条 `PICTURE_FED`、一条 `PICTURE_REVISITED`、两个已完成运行以及修订号 `2`。

再添加一个主体为 `699` 的真实事务用例，证明最终运行 CAS 失败时的回滚，而不是只信任注解：

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

将此测试用与第一个并发用例相同的、按子表优先且按主体限定的清理放入 `try/finally`。失败的 `runRepository.complete` 发生在 `coordinator.complete` 内部的聚合/技能/成长写入之后；这三者都必须消失，而独立提交的重启运行必须保留。

再添加第三个 H2 用例，覆盖最危险的幂等竞态：两个线程并发调用 `coordinator.reserve`，主体 `697`、图片 `102`、键 `feed-same-key-0001`、fingerprint/模式/披露相同，但拟议的关联 ID 不同。使用一个 latch 释放它们，然后断言返回类型恰好包含一个 `STARTED` 和一个 `IN_PROGRESS`，两个结果引用相同的持久化运行 ID 和存储的关联 ID，并且 `(companionId, idempotencyKey)` 的 SQL 计数为 `1`。在 `finally` 中清理该主体。此测试必须在真实 H2 上执行“读取不存在 → 竞争插入 → `DuplicateKeyException` → 重新加载”；不要用 mock 或顺序调用替换。

- [ ] **步骤 11：运行应用测试和领域覆盖率检查**

```powershell
.\scripts\mvnw-java21.ps1 "-Dtest=CompanionLifeServiceTest,CompanionFeedingCoordinatorTest,CompanionFeedingIntegrationTest,DemoPictureNutritionAdapterTest,CompanionTest,FeedingRunTest,CompanionBalancePropertyTest" test
.\scripts\mvnw-java21.ps1 -Dspring.profiles.active=test verify
```

预期结果：授权规范化、精确重放、失败运行重试、每日/重复上限、原子完成和演示确定性全部通过；伙伴领域分支覆盖率仍至少为 85%。

- [ ] **步骤 12：提交应用工作流**

```powershell
git add src/main/java/com/li/lipicturecloud/config/CompanionConfiguration.java src/main/java/com/li/lipicturecloud/config/CompanionFeatureProperties.java src/main/java/com/li/lipicturecloud/application/companion src/main/java/com/li/lipicturecloud/infrastructure/companion src/test/java/com/li/lipicturecloud/application/companion src/test/java/com/li/lipicturecloud/infrastructure/companion/DemoPictureNutritionAdapterTest.java
git commit -m "feat: orchestrate authorized companion feeding"
```

---

### 任务 5：暴露已认证的伙伴 HTTP 接口

**文件：**
- 创建：`src/main/java/com/li/lipicturecloud/model/dto/companion/CompanionFeedRequest.java`
- 创建：`src/main/java/com/li/lipicturecloud/controller/CompanionController.java`
- 创建：`src/test/java/com/li/lipicturecloud/controller/CompanionControllerTest.java`

**Interface：**
- 消费：`CompanionLife`、`UserService.getLoginUserEntity(HttpServletRequest)`、`UserService.isAdmin(User)` 以及 `AuthorizationSubject` 工厂。
- 产出：`GET /companion/me`、`POST /companion/awaken` 和 `POST /companion/feed`；任何端点都不接受主体/用户 ID。

- [ ] **步骤 1：编写失败的 controller 测试**

创建带有 `GlobalExceptionHandler` 的独立 MockMvc 测试：

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

添加成功唤醒用例，以及一个包含 `{}` 的喂养请求，预期 HTTP 400 / code `40000`。

严格定义 controller 测试的结果 helper：

```java
private FeedPictureResult feedResult() {
    return new FeedPictureResult(
            "GROWN",
            "fef53056-2d9f-467d-9b1d-1afe9a6638fe",
            null,
            null);
}
```

- [ ] **步骤 2：运行 controller 测试并验证 RED**

```powershell
.\scripts\mvnw-java21.ps1 -Dtest=CompanionControllerTest test
```

预期结果：由于 DTO 和 controller 尚不存在，编译失败。

- [ ] **步骤 3：创建精简的请求 DTO**

```java
package com.li.lipicturecloud.model.dto.companion;

import lombok.Data;

@Data
public class CompanionFeedRequest {
    private Long pictureId;
    private String idempotencyKey;
}
```

不要添加 `userId`、`spaceId`、XP、性格、技能、营养模式、平衡版本或修订号。

- [ ] **步骤 4：使用构造器注入实现 controller**

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

方法级 `@AuthCheck` 提供标准认证守卫；在 controller 内重新构建主体，确保请求 JSON 无法覆盖它。

- [ ] **步骤 5：运行 controller、应用和授权测试**

```powershell
.\scripts\mvnw-java21.ps1 "-Dtest=CompanionControllerTest,CompanionLifeServiceTest,CompanionFeedingCoordinatorTest,SpacePermissionInterceptorTest,SpaceAuthorizationManagerTest" test
```

预期结果：所有选定测试通过；恶意的 `userId` JSON 永远不会出现在应用命令中，现有授权决策/拦截器测试仍保持绿色。

- [ ] **步骤 6：提交 HTTP 接口**

```powershell
git add src/main/java/com/li/lipicturecloud/model/dto/companion/CompanionFeedRequest.java src/main/java/com/li/lipicturecloud/controller/CompanionController.java src/test/java/com/li/lipicturecloud/controller/CompanionControllerTest.java
git commit -m "feat: expose companion life endpoints"
```

---

### 任务 6：构建伙伴页面和可靠的喂养重试 UX

**文件：**
- 创建：`li-picture-cloud-frontend/src/api/companion.js`
- 修改：`li-picture-cloud-frontend/src/api/request.js`
- 修改：`li-picture-cloud-frontend/src/api/picture.js`
- 创建：`li-picture-cloud-frontend/src/config/features.js`
- 创建：`li-picture-cloud-frontend/src/constants/companion.js`
- 创建：`li-picture-cloud-frontend/src/utils/authBootstrap.js`
- 创建：`li-picture-cloud-frontend/src/utils/companion.js`
- 创建：`li-picture-cloud-frontend/tests/companion.test.mjs`
- 修改：`li-picture-cloud-frontend/src/stores/user.js`
- 修改：`li-picture-cloud-frontend/src/App.vue`
- 修改：`li-picture-cloud-frontend/src/router/index.js`
- 修改：`li-picture-cloud-frontend/src/constants/navigation.js`
- 修改：`li-picture-cloud-frontend/src/components/NavBar.vue`
- 修改：`li-picture-cloud-frontend/tests/navigation.test.mjs`
- 修改：`li-picture-cloud-frontend/tests/responsiveViews.test.mjs`
- 创建：`li-picture-cloud-frontend/src/components/companion/CompanionStats.vue`
- 创建：`li-picture-cloud-frontend/src/components/companion/CompanionPicturePicker.vue`
- 创建：`li-picture-cloud-frontend/src/components/companion/CompanionGrowthTimeline.vue`
- 创建：`li-picture-cloud-frontend/src/views/CompanionView.vue`

**Interface：**
- 消费：任务 5 的精确 HTTP 接口、`listSpaceVOByPage`、现有的权限检查 `/picture/list/page/vo` 端点、Pinia 认证状态和 JSON 字符串 ID。
- 产出：

```javascript
getCompanionHome()                    // GET  /companion/me
awakenCompanion()                     // POST /companion/awaken
feedCompanion({ pictureId, idempotencyKey }) // POST /companion/feed
listPictureVOByPageUncached(data)     // POST /picture/list/page/vo
```

页面绝不计算成长，只渲染服务器提供的总量和增量。

- [ ] **步骤 1：编写失败的纯前端行为测试**

创建 `tests/companion.test.mjs`：

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

- [ ] **步骤 2：运行聚焦 Node 测试并验证 RED**

```powershell
Set-Location li-picture-cloud-frontend
node --test --test-name-pattern="idempotency|server result|old idempotent replay|fractional precision|oldest owned|bipolar traits|retains a feed key|auth loading|late bootstrap" tests/companion.test.mjs
```

预期结果：模块未找到或导出缺失。

- [ ] **步骤 3：实现伙伴常量和纯 helper**

创建规范的 UI 元数据：

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

严格实现 helper：

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

另外使用两位小数导出 `formatSignedDelta(value)`，并为正变化显式添加 `+`。

创建 `src/utils/authBootstrap.js`：

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

- [ ] **步骤 4：添加 API 封装**

创建 `src/api/companion.js`：

```javascript
import request from './request'

export const getCompanionHome = () => request.get('/companion/me')
export const awakenCompanion = () => request.post('/companion/awaken')
export const feedCompanion = data => request.post('/companion/feed', data)
```

在 `src/api/request.js` 的两个拒绝分支中，都拒绝一个保留 `status` 和后端封装 `code` 的 `Error`：

```javascript
function toApiError(message, status, code) {
  const error = new Error(message || '请求失败')
  error.status = status
  error.code = code
  return error
}
```

对于非零成功封装响应，使用 `toApiError(body.message, res.status, body.code)`；对于 Axios rejection，使用 `toApiError(msg, err.response?.status, err.response?.data?.code)`。不要在 error 对象上暴露响应正文或请求头。

在 `src/api/picture.js` 中添加：

```javascript
/** 分页获取当前主体有权查看的空间图片，不使用 Redis 列表缓存。 */
export function listPictureVOByPageUncached(data) {
  return request.post('/picture/list/page/vo', data)
}
```

伙伴页面使用未缓存路由，因此图片列表不依赖 Redis 缓存状态；后端端点已经解析空间并强制执行 `picture:view`。真实登录流程仍使用仓库的 Sa-Token Redis DAO，因此本地 E2E 需要 Redis，CI 提供 service 容器。

创建 `src/config/features.js`：

```javascript
export const COMPANION_UI_ENABLED = import.meta.env.DEV
  || import.meta.env.VITE_COMPANION_ENABLED === 'true'
```

因此开发环境和 E2E 会显示此功能。生产构建会隐藏其路由和导航入口，除非 `VITE_COMPANION_ENABLED=true`；启用发布版本需要同时设置该构建开关和后端 `COMPANION_ENABLED=true`。

- [ ] **步骤 5：让认证初始化支持单飞并感知路由**

更新用户 store：

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

从 `@/utils/authBootstrap` 导入 `createAuthSessionGate`、`createSingleFlightLoader` 和 `isTerminalAuthFailure`。

让 `fetchCurrentUser()` 委托给 `ensureCurrentUser()`。登录成功和退出登录后都调用 `authSessionGate.invalidate()`、设置 `authReady=true` 并清除 `authBootstrapError`；这可防止较早的初始化响应覆盖较新的显式登录/退出。导出 `authReady`、`authBootstrapError` 和 `ensureCurrentUser`。修改 `App.vue`，调用 `userStore.ensureCurrentUser().catch(() => {})`；store 保留安全的重试状态，因此这种 fire-and-forget 初始化不会产生未处理的 promise rejection。

只在 `COMPANION_UI_ENABLED` 为 true 时添加路由，然后添加元数据和守卫：

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

在 router 中导入 `useUserStore` 和 `COMPANION_UI_ENABLED`。`main.js` 会在 router 之前安装 Pinia，因此守卫获得的是活动 store。临时网络/5xx 失败只允许继续，以便 `CompanionView` 渲染可重试的认证初始化错误；明确的 401/403 仍重定向到登录页。不要把同步的 `if (!isLoggedIn) replace('/login')` 模式复制到 `CompanionView`。

- [ ] **步骤 6：添加已认证导航和契约断言**

让 `buildNavigationGroups` 接受 `companionEnabled = true`；只有该标志为 true 时，才在 `我的空间` 后插入这个工作区项目：

```javascript
{ label: '我的伙伴', to: '/companion' }
```

在 `NavBar.vue` 中导入 `COMPANION_UI_ENABLED`，并在组件构建共享桌面/移动导航组的所有位置，将其作为 `companionEnabled` 传入。

扩展 `navigation.test.mjs`：

```javascript
assert.equal(user.flatMap(group => group.items).some(item => item.to === '/companion'), true)
assert.equal(buildNavigationGroups({ isLoggedIn: false, isAdmin: false })
  .flatMap(group => group.items).some(item => item.to === '/companion'), false)
assert.equal(buildNavigationGroups({
  isLoggedIn: true, isAdmin: false, companionEnabled: false
}).flatMap(group => group.items).some(item => item.to === '/companion'), false)
```

在 `responsiveViews.test.mjs` 的“空间和 AI 界面定义手机布局”文件列表中加入：

```javascript
'views/CompanionView.vue',
'components/companion/CompanionStats.vue',
'components/companion/CompanionPicturePicker.vue',
'components/companion/CompanionGrowthTimeline.vue'
```

- [ ] **步骤 7：使用自然语言性格构建统计组件**

`CompanionStats.vue` 接受一个必需的 `companion` 对象。渲染：

- 来自 `LIFE_STAGE` 的阶段标签和描述；
- `等级 {{ companion.level }}` 和 `{{ lifeExperience }} / {{ nextLevelExperience }} 生命经验`；
- 根据 `levelStartExperience` 和 `nextLevelExperience` 计算的进度条；
- 五行双极性格行，包含负向标签、中性中心、正向标签、来自 `traitPosition` 的标记以及来自 `describeTrait` 的文本；
- 五项技能及服务器提供的等级/经验/下一个阈值。

为每个渲染的技能行提供稳定定位器 `data-testid="skill-<skill.code>"`；这是验收接缝，不是样式选择器。

使用下面的进度计算，不要重新计算后端曲线：

```javascript
const lifeProgress = computed(() => {
  const start = Number(props.companion.levelStartExperience)
  const next = Number(props.companion.nextLevelExperience)
  const current = Number(props.companion.lifeExperience)
  return next <= start ? 100 : Math.min(100, Math.max(0, (current - start) / (next - start) * 100))
})
```

使用 `aria-valuemin="-100"`、`aria-valuemax="100"` 和服务器提供的 `aria-valuenow`，但不要把性格绝对值打印成可追求最大化的分数。

- [ ] **步骤 8：构建无障碍的单图片选择器**

`CompanionPicturePicker.vue` 的 props 为 `pictures`、`selectedId`、`loading` 和 `disabled`，并触发 `select`。每张图片都是一个真实按钮：

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

渲染明确的加载和空状态。组件不负责导航，也绝不接受多选。

- [ ] **步骤 9：构建只保留来源信息的成长时间线**

`CompanionGrowthTimeline.vue` 接受 `records`。对每条记录渲染事件标签、`+N 生命经验`、非零性格/技能增量、原因、平衡版本、营养披露和格式化时间。将来源渲染为链接 `图片 #<末 6 个字符>`，指向 `/picture/:id`；不要渲染复制的名称、缩略图、URL、标签或描述。

性格使用 `formatSignedDelta`，技能代码使用 `SKILL_LABEL`；当 `contentUnderstood === false` 时始终渲染以下持久徽章：

```vue
<span class="demo-badge">未进行内容理解</span>
```

为非零性格增量行提供 `data-testid="growth-trait-<axis-key>"`。来源必须是真实的 router link，渲染出的 `href` 为 `/picture/<完整 ID>`，即使标签只显示末六位。

空列表显示 `伙伴还没有成长记录，选择一张图片开始第一次喂养。`

- [ ] **步骤 10：组合完整页面并保留不确定的喂养尝试**

`CompanionView.vue` 编排各组件。如果挂载时存在 `userStore.authBootstrapError`，则渲染 `暂时无法确认登录状态` 和一个 `重试` 按钮，而不是调用伙伴 API。重试按钮再次调用 `ensureCurrentUser()`；成功后加载 home，明确未认证结果则重定向到登录页。否则调用 `getCompanionHome()`。如果 `home.companion` 存在，加载私有来源：

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

如果初始 home 请求因后端功能关闭而返回 HTTP 404，渲染 `伙伴功能暂未开放`，且不显示唤醒/喂养控件。其他初始失败则渲染安全错误和 `重新加载` 按钮。不要把 HTTP 失败误判为有效的 `companion: null` 唤醒状态。

唤醒调用 `awakenCompanion()`，替换 `home`，然后加载来源。喂养使用：

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

更改选择的图片会清除待处理尝试和喂养错误。中止/网络、限流、处理中或服务器失败的响应会保留同一尝试，并将按钮标签改为 `重试这次喂养`；从 `pendingAttempt && feedError` 计算该标签，而不是只根据错误计算。明确的 400/401/403/404 会清除终态尝试，使用户之后的新意图获得新键。未选择图片前禁用提交，请求运行期间禁用选择和提交。即使按钮已禁用，也要保留同步的 `feedBusy` 函数守卫；单靠 DOM 更新无法阻止同一个事件循环轮次中的两次点击。

页面有四个可见区域：

1. 带有 `唤醒我的伙伴` 的 Hero/空状态。
2. 由服务器驱动的 `演示营养（确定性）` banner 以及 `home.nutrition.notice`。
3. 统计和私有图片选择器；没有私有空间时链接到 `/space/create`；没有图片时链接到 `/upload`。
4. 成长时间线。

使用两列桌面网格，并让四个新增 Vue 文件在 `@media (max-width: 767px)` 下都设置 `grid-template-columns: 1fr`。为可选择按钮提供可见的 `:focus-visible` 轮廓和至少 44px 的触控目标。

- [ ] **步骤 11：运行前端测试、lint、构建和 bundle 预算检查**

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

预期结果：所有 Node 测试通过，ESLint 零警告。默认生产构建没有伙伴路由 chunk；显式启用的构建包含 `CompanionView-*.js` 懒加载 chunk；两个构建都通过现有 bundle 预算。

- [ ] **步骤 12：提交伙伴页面**

```powershell
Set-Location ..
git add li-picture-cloud-frontend/src/api/companion.js li-picture-cloud-frontend/src/api/request.js li-picture-cloud-frontend/src/api/picture.js li-picture-cloud-frontend/src/config/features.js li-picture-cloud-frontend/src/constants/companion.js li-picture-cloud-frontend/src/utils/authBootstrap.js li-picture-cloud-frontend/src/utils/companion.js li-picture-cloud-frontend/tests/companion.test.mjs li-picture-cloud-frontend/src/stores/user.js li-picture-cloud-frontend/src/App.vue li-picture-cloud-frontend/src/router/index.js li-picture-cloud-frontend/src/constants/navigation.js li-picture-cloud-frontend/src/components/NavBar.vue li-picture-cloud-frontend/tests/navigation.test.mjs li-picture-cloud-frontend/tests/responsiveViews.test.mjs li-picture-cloud-frontend/src/components/companion li-picture-cloud-frontend/src/views/CompanionView.vue
git commit -m "feat: add companion growth page"
```

---

### 任务 7：证明完整浏览器流程并记录演示边界

**文件：**
- 创建：`src/test/resources/application-e2e.yaml`
- 创建：`src/test/resources/e2e-schema.sql`
- 创建：`src/test/resources/e2e-data.sql`
- 创建：`li-picture-cloud-frontend/scripts/start-e2e-backend.mjs`
- 创建：`li-picture-cloud-frontend/playwright.config.js`
- 创建：`li-picture-cloud-frontend/e2e/companion.spec.js`
- 修改：`li-picture-cloud-frontend/vite.config.js`
- 修改：`li-picture-cloud-frontend/package.json`
- 修改：`li-picture-cloud-frontend/package-lock.json`
- 修改：`li-picture-cloud-frontend/Dockerfile`
- 修改：`li-picture-cloud-frontend/eslint.config.js`
- 修改：`.gitignore`
- 修改：`.github/workflows/ci.yml`
- 修改：`compose.yaml`
- 修改：`.env.example`
- 修改：`src/test/java/com/li/lipicturecloud/deployment/DeploymentArtifactsTest.java`
- 创建：`docs/round-19-companion-life-core-guide.md`
- 修改：`README.md`

**Interface：**
- 消费：真实 Vite 应用、真实 Spring Boot HTTP/controller/application/domain/MyBatis 栈、H2 迁移、现有会话登录、一个私有空间和一个私有图片 fixture。
- 产出：`npm run test:e2e`、单 worker Chromium 测试、CI 任务 `companion-e2e` 以及操作人员/用户指南。不 mock 任何 HTTP 路由。

- [ ] **步骤 1：安装 Playwright 并声明浏览器命令**

在前端目录运行：

```powershell
npm install --save-dev @playwright/test@^1.62.1
```

向 `package.json` 的 scripts 添加：

```json
"test:e2e": "playwright test"
```

此命令会同时更新 `package.json` 和 `package-lock.json`；不要手动编辑 lockfile。

- [ ] **步骤 2：添加跨平台后端启动器**

创建 `scripts/start-e2e-backend.mjs`：

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

- [ ] **步骤 3：编写 Playwright 配置和最初失败的真实浏览器测试**

创建 `playwright.config.js`：

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

修改 `vite.config.js`，使现有代理保持正常默认值，同时允许隔离的浏览器运行覆盖它：

```javascript
target: process.env.VITE_API_PROXY_TARGET || 'http://localhost:8124'
```

专用端口和 `reuseExistingServer: false` 会让端口被占用时明确失败，而不是静默地把 E2E 数据写入开发者后端。将 Playwright 重试次数保持为零，因为这个有状态的 H2 流程没有重置端点；进程级重试会复用已提交的成长，并使首次喂养断言失效。

创建 `e2e/companion.spec.js`：

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

- [ ] **步骤 4：在添加 fixture 前运行浏览器测试并验证 RED**

```powershell
npx playwright install chromium
npm run test:e2e
```

运行此命令前，在 `127.0.0.1:6379` 启动本地 Redis，因为真实的 Sa-Token 登录 DAO 会使用它，即使 Spring Session 已禁用。预期结果：由于 E2E profile 和旧版表 fixture 尚不存在，后端启动或登录失败。这证明测试使用的是真实后端，而不是路由 mock。

- [ ] **步骤 5：添加隔离的 E2E 数据源和旧版模式 fixture**

创建 `src/test/resources/application-e2e.yaml`：

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

创建 `e2e-schema.sql`，只包含真实登录、私有空间列表、图片列表和授权链所需的现有上下文：

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

- [ ] **步骤 6：填充一个已认证主体、私有空间和图片**

创建 `e2e-data.sql`：

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

密码哈希是现有仅限本地使用的 `LocalUser123!` BCrypt fixture。将此文件保留在测试资源中，生产 profile 绝不能引用它。

- [ ] **步骤 7：运行真实浏览器流程并验证 GREEN**

在 `li-picture-cloud-frontend` 中：

```powershell
npm run test:e2e
```

预期结果：Chromium 通过真实会话端点登录，Liquibase 创建伙伴表，UI 唤醒一个伙伴；第一次喂养即使响应被中止也只提交一次，重试复用精确的键并返回存储的结果，H2 包含一行成长记录，重新加载保留披露信息和总量，并且授权的来源图片读取证明其 ID、位置、名称和 URL 没有因喂养而改变。

- [ ] **步骤 8：添加 E2E lint/忽略规则和 CI 门禁**

在编辑 CI 前，通过现有 Docker 路径接入生产开关。在 `li-picture-cloud-frontend/Dockerfile` 的 `RUN npm run build` 前添加：

```dockerfile
ARG VITE_COMPANION_ENABLED=false
ENV VITE_COMPANION_ENABLED=$VITE_COMPANION_ENABLED
```

在 `compose.yaml` 中，添加到 `backend.environment`：

```yaml
      COMPANION_ENABLED: ${COMPANION_ENABLED:-false}
      COMPANION_FEEDING_ENABLED: ${COMPANION_FEEDING_ENABLED:-false}
      COMPANION_PROCESSING_TIMEOUT: ${COMPANION_PROCESSING_TIMEOUT:-5m}
```

添加到 `web.build`：

```yaml
      args:
        VITE_COMPANION_ENABLED: ${VITE_COMPANION_ENABLED:-false}
```

将这四个变量都添加到 `.env.example`，默认值依次为 `false`、`false`、`5m` 和 `false`。扩展 `DeploymentArtifactsTest`，断言 Dockerfile 声明了 `ARG`、Compose 传递了 web 构建参数和全部三个后端变量、`.env.example` 记录了这些变量。同时断言前后端默认值都是 false；生产操作人员修改构建标志后必须重新构建 web 镜像。

扩展 `eslint.config.js` 的 Node 文件块，使其包含：

```javascript
files: ['vite.config.js', 'playwright.config.js', 'eslint.config.js',
  'scripts/**/*.mjs', 'e2e/**/*.js']
```

向 `.gitignore` 添加：

```text
# Playwright artifacts
**/playwright-report/
**/test-results/
```

在现有的默认前端构建/bundle 步骤之后，添加一个显式的生产启用变体，使 CI 证明 UI 标志的两种状态：

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

在当前 backend/frontend 任务之后添加一个任务：

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

在前端目录中运行 `..\scripts\mvnw-java21.ps1 -Dtest=DeploymentArtifactsTest test`（或在根目录运行等效命令），并期望 Docker/Compose 标志契约在继续之前通过。

- [ ] **步骤 9：编写 Round 19 指南和 README 条目**

创建 `docs/round-19-companion-life-core-guide.md`，包含以下精确章节和事实：

1. `这次交付了什么`：唤醒、演示喂养、XP/性格/技能/历史、每个主体一个伙伴。
2. `演示营养不是视觉 AI`：确定性的图片 ID 档案；不读取图片字节/内容/模型；持久化 `contentUnderstood=false`。
3. `本地体验`：启动 MySQL/Redis/后端/前端，打开 `/companion`，唤醒，选择私有图片，喂养，重试行为。
4. `配置开关`：后端 `COMPANION_ENABLED`、`COMPANION_FEEDING_ENABLED`、`COMPANION_PROCESSING_TIMEOUT`，以及构建期前端 `VITE_COMPANION_ENABLED`；生产环境需要前端和后端启用标志，二者默认禁用，修改前端标志必须重新构建 web 镜像。
5. `数据与一致性`：四张表、唯一键、修订号 CAS、追加式成长、每日/重复上限、关联和安全审计字段。
6. `分片模式上线顺序`：设置五个 `MYSQL_*` 变量后，先针对直接物理 MySQL 运行 `powershell -File scripts/migrate-companion-physical.ps1`（Windows）或 `./mvnw -q liquibase:update`（Linux）；仅在以 `0` 退出后，才启动带有新 `!SINGLE` 声明的 ShardingSphere。
7. `验证命令`：本计划中的每个聚焦/完整后端、前端和 Playwright 命令。
8. `明确未包含`：真实视觉、记忆、主动行为、模型/MCP 控制中心、图片生成、BYOK/配额/支付、极端剧情。
9. `生产发布警告`：不要将此演示公开为 AI 图片理解；支持 Provider 的发布仍需满足主规范的 CORS、凭据、隐私和更广泛 E2E 门禁。

在 `README.md` 的文档导航下添加：

```markdown
- [图像伙伴生命核心与演示喂养指南](docs/round-19-companion-life-core-guide.md)
```

在测试章节添加一句：`CI 还会启动 H2 后端与真实 Chromium，验证伙伴唤醒、私有图片喂养及幂等重试。`

- [ ] **步骤 10：运行完整的发布规模验证**

在仓库根目录执行：

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

预期结果：

- Maven 以 0 退出；Liquibase rollback-on-update 通过；伙伴领域分支覆盖率至少为 85%。
- 生产依赖审计没有 high/critical 发现。
- ESLint 和所有 Node 测试通过；默认生产输出不包含伙伴 chunk，启用后的输出包含它，两个 bundle 检查均以 0 退出。
- Chromium 证明完整真实后端流程及单行幂等结果。
- `git status --short` 只列出任务 1—7 有意修改的文件；没有暂存任何本地密钥/配置文件。

- [ ] **步骤 11：提交浏览器门禁和指南**

```powershell
git add src/test/resources/application-e2e.yaml src/test/resources/e2e-schema.sql src/test/resources/e2e-data.sql src/test/java/com/li/lipicturecloud/deployment/DeploymentArtifactsTest.java li-picture-cloud-frontend/scripts/start-e2e-backend.mjs li-picture-cloud-frontend/playwright.config.js li-picture-cloud-frontend/e2e/companion.spec.js li-picture-cloud-frontend/vite.config.js li-picture-cloud-frontend/package.json li-picture-cloud-frontend/package-lock.json li-picture-cloud-frontend/Dockerfile li-picture-cloud-frontend/eslint.config.js compose.yaml .env.example .gitignore .github/workflows/ci.yml docs/round-19-companion-life-core-guide.md README.md
git commit -m "test: verify companion life browser flow"
```

---

## 执行评审门禁

每个任务之后，评审者都必须在下一个任务开始前回答以下所有问题：

- 该任务是否保留了受信任主体和已授权资源的边界？
- 模型/Adapter/客户端能否直接写入领域总量？要求答案为否。
- 同一个幂等键、同一图片的不同键或并发请求能否产生意外的完整成长？要求答案为否。
- 失败能否留下没有成长事实的伙伴状态，或留下没有已完成运行的成长事实？要求答案为否。
- 任何当前或历史 UI 是否暗示了视觉内容理解？要求答案为否。
- 所有新引入的常量是否由 `CompanionBalance.v1()` 或显式功能配置拥有？
- 领域文件是否仍通过 `DomainDependencyTest`？
- 任务的聚焦测试是否在提交前为绿色？

## 为后续规范保留的延展接缝

- `PictureNutritionAnalyzer` 可在切片 2 接收真实视觉理解 Adapter，而无需改变伙伴成长计算。
- `GrowthRecord` 已携带来源图片 ID、说明、平衡版本和应用后快照；记忆候选和删除传播仍将在切片 2 中作为独立记录。
- 五个技能代码包含未来故事、表情、融合和图库搜索能力，但本切片只改变其熟练度数字；不调用这些能力。
- `CompanionLife` 保持 `home / awaken / feed`；主动提案、反馈、时间推进、自主性、模型路由、MCP 和图片创建需要单独评审的 Interface 和计划。
- 喂养运行状态和关联支持未来的异步/结果未知设计，但这个伪 Adapter 同步完成，且永不调用外部 Provider。
