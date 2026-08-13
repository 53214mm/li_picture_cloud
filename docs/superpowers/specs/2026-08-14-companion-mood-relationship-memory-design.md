# 伙伴情绪、关系状态与来源化记忆设计

> 日期：2026-08-14
>
> 依据：[三年总规划](../../roadmap/2026-2029-image-life-companion-roadmap.md)「未来 90 天执行清单」第 2—4 条，与第 19 轮「属性系统」brainstorm（五层属性中的当前情绪、关系状态）。
>
> 范围：当前情绪、关系状态和来源化记忆的数据模型、后端闭环与最小页面；把伙伴解释统一为聊天气泡。**不包含**：站内对话、主动提案、自主契约、敲打反馈、长期记忆检索、模型/MCP 控制中心。

## 1. 目标

1. 伙伴获得会随喂养变化、随时间自然回落的**当前情绪**（五轴），并在伙伴页解释。
2. 伙伴与主人之间形成可累积的**关系状态**（五轴），按主体独立存储。
3. 每次真实内容理解的喂养产生一条**带来源、置信度、状态的记忆候选**；用户可以确认、纠正、忽略或删除；来源图片撤权或消失后，相关记忆自动失效并不再展示内容。
4. 喂养故事、记忆与情绪摘要统一使用**聊天气泡**（`CompanionMessageBubble`）作为伙伴发言的唯一视觉语言，为站内对话预留展示口径。

## 2. 领域模型

### 2.1 当前情绪 `CompanionMood`

- 五轴强度，全部 `[0, 100]`，两位小数：
  - `energy` 精力、`joy` 愉悦、`loneliness` 孤独、`inspiration` 灵感、`irritation` 烦躁。
- 情绪是**快状态**，与长期性格分开存储，不与伙伴聚合根的 revision CAS 竞争。
- 自然衰减：每满 1 小时，每轴向 0 方向减少 `5.00`（`CompanionMoodRules.decayPerHour`），最小到 0；读取伙伴主页时惰性衰减并写回。
- 喂养影响：一次喂养的每轴变化量上限 `15.00`（`maxImpact`），超过按符号截断。
- 不变量：五轴都在 `[0,100]`；`updatedAt` 不能早于 `createdAt`。

### 2.2 关系状态 `CompanionRelationship`

- 五轴：`familiarity` 熟悉度、`trust` 信任、`closeness` 亲密度、`tacit` 默契（均 `[0,100]`），`recentFeedback` 近期反馈（`[-100,100]`）。
- 按 `(companionId, subjectId)` 独立一行，为未来团队共同培养预留（当前一名用户一个伙伴，恒为一行）。
- 喂养影响（`CompanionRelationshipRules`）：
  - 首次完整喂养：熟悉度 +5、信任 +2、亲密度 +1、默契 +1、近期反馈 +5；
  - 重复图片熟悉感：熟悉度 +2、默契 +1、近期反馈 +2；
  - 单轴单次变化上限 `10.00`。
- 关系是**慢状态**，不随时间衰减。
- 敲打、安静等用户反馈本阶段不落地，`recentFeedback` 保留字段语义。

### 2.3 来源化记忆 `CompanionMemory`

- 字段：`companionId`、`subjectId`、`pictureId`（来源图片，可空）、`growthRecordId`（来源成长记录，唯一）、`sourceType`、`content`、`originalContent`、`confidence`、`status`、`invalidatedReason`、`revision`、时间戳。
- `MemorySourceType`：`VISUAL`、`DEMO`。只有**真实视觉理解**（或 Demo 测试档）产生候选；元数据喂养与显式降级不生成候选，避免把"未理解内容"包装成记忆。
- 候选内容 = 视觉观察的伙伴独白（`VisualObservationCandidate.companionMessage`），已通过纯文本边界校验；Demo 档使用固定文案。置信度来自视觉候选 `confidence`（Demo 固定 `0.50`）。
- 状态机：

```text
PENDING ──confirm──▶ CONFIRMED ──dismiss──▶ DISMISSED
   │                    │ ▲                    │
   │ ──correct──▶  CONFIRMED（改写 content）    │
   │ ◀──confirm────────────────────────────────┘
PENDING / CONFIRMED / DISMISSED ──invalidate──▶ INVALIDATED（终态，内容不再展示）
任意非 DELETED ──delete──▶ DELETED（终态）
```

- `correct` 是动作而非状态：对 `PENDING/CONFIRMED/DISMISSED` 记忆改写文案并置为 `CONFIRMED`；`originalContent` 永远保留最初候选，保证可追溯。
- 不变量：`content`/`originalContent` 非空、≤ 300 码点、无控制字符与外部链接；`confidence ∈ [0,1]`；`growthRecordId` 唯一（一次成长至多一条候选）；终态不可再变更。
- 同一图片只有一次完整喂养，因此"每图片一条候选"由 `growthRecordId` 唯一键天然保证。

### 2.4 撤权失效传播（最小实现）

- 读取记忆列表（`GET /companion/memories`）时执行**惰性失效**：
  1. 取出活跃记忆（`PENDING/CONFIRMED/DISMISSED`）涉及的 `pictureId` 去重集合；
  2. 对每个图片调用 `authorization.checkForUser(PICTURE_VIEW, pictureId, subjectId)`，同一图片只检查一次；
  3. `NOT_FOUND` 或 `NO_AUTH` 的记忆在同一事务内 CAS 置为 `INVALIDATED(invalidatedReason=PICTURE_UNAVAILABLE)`；
  4. `INVALIDATED`/`DELETED` 记忆不返回内容原文，只返回状态与原因。
- **转移端点同样守门**：`confirm/correct/dismiss/delete` 在操作前校验来源图片；
  已撤权的记忆直接拒绝操作且不返回内容，避免绕过列表路径读出撤权内容。
- 失效只影响单条记忆，不阻塞列表读取；后续可用事件驱动替换为主动传播，接口保持稳定。

## 3. 数据模型（MySQL / H2）

### 3.1 `companion_mood`（每伙伴一行）

```sql
id BIGINT PK,
companionId BIGINT NOT NULL,           -- UNIQUE
energy/joy/loneliness/inspiration/irritation DECIMAL(6,2) NOT NULL DEFAULT 0,
revision BIGINT NOT NULL DEFAULT 0,
createTime / updateTime TIMESTAMP
CHECK (各轴 BETWEEN 0 AND 100)
```

### 3.2 `companion_relationship`（每伙伴每主体一行）

```sql
id BIGINT PK,
companionId BIGINT NOT NULL,
subjectId BIGINT NOT NULL,
familiarity/trust/closeness/tacit DECIMAL(6,2) NOT NULL DEFAULT 0,
recentFeedback DECIMAL(6,2) NOT NULL DEFAULT 0,
revision BIGINT NOT NULL DEFAULT 0,
createTime / updateTime TIMESTAMP
UNIQUE (companionId, subjectId)
CHECK (familiarity/trust/closeness/tacit BETWEEN 0 AND 100, recentFeedback BETWEEN -100 AND 100)
```

### 3.3 `companion_memory`（追加式事实）

```sql
id BIGINT PK,
companionId BIGINT NOT NULL,
subjectId BIGINT NOT NULL,
pictureId BIGINT NULL,
growthRecordId BIGINT NOT NULL,        -- UNIQUE
sourceType VARCHAR(24) NOT NULL,
content VARCHAR(512) NOT NULL,
originalContent VARCHAR(512) NOT NULL,
confidence DECIMAL(4,3) NOT NULL,
status VARCHAR(24) NOT NULL,
invalidatedReason VARCHAR(64) NULL,
revision BIGINT NOT NULL DEFAULT 0,
createTime / updateTime TIMESTAMP
CHECK (confidence BETWEEN 0 AND 1)
INDEX (companionId, status, createTime, id)
```

三张表都加入两份 ShardingSphere 配置的 `!SINGLE` 清单；Liquibase changeSet 沿用"每 DDL 一个 changeSet + 可恢复 precondition + 全局结构校验"的既有模式；只有物理 MySQL 可运行迁移。

## 4. 应用流程

### 4.1 喂养完成时（`CompanionFeedingCoordinator.complete` 事务内）

在现有「伙伴 CAS → 追加成长记录 → run CAS」基础上追加三笔**同事务**写入：

1. 情绪：读当前行（无则中性创建）→ 按 `updatedAt` 惰性衰减 → 叠加 `nutrition.requestedMoodImpact()`（按 `maxImpact` 截断）→ CAS 写回；
2. 关系：读当前行（无则创建）→ 按 `PICTURE_FED` / `PICTURE_REVISITED` 应用规则 → CAS 写回；
3. 记忆：仅当 `eventType == PICTURE_FED` 且 `nutrition.memorySeed() != null` 时追加一条 `PENDING` 候选（sourceType、置信度来自 provenance）。

任何一步失败整体回滚，保证"一次喂养的完整效果"原子化；replay 走已有 REPLAY 分支，天然不会重复应用。

### 4.2 营养携带情绪影响与记忆种子

- `PictureNutrition` 增加两个可选字段：`MoodImpact requestedMoodImpact`（可空）、`String memorySeed`（可空）；旧构造器保持兼容（两者为 null）。
- `VisualPictureNutritionAdapter`：从 `VisualObservationCandidate` 映射 `MoodImpact`，`memorySeed = companionMessage`。
- `MetadataPictureNutritionAdapter`：从 `PictureObservation` 映射 `MoodImpact`，不生成记忆种子。
- `DemoPictureNutritionAdapter`：固定 `MoodImpact`（每轴 +2）与固定 `memorySeed`（Demo 测试档）。
- 显式降级路径不携带 `memorySeed`。

### 4.3 主页读取时

`home()` 先在一个独立写事务中执行情绪惰性衰减（`CompanionMoodRepository.decayIfNeeded`），再在只读可重复读事务中组装主页（伙伴、技能、成长记录、情绪、关系视图）。没有情绪行时返回中性视图，不落库。

### 4.4 记忆 API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/companion/memories?limit=50` | 列表 + 惰性失效传播；`INVALIDATED/DELETED` 不返回内容 |
| POST | `/companion/memories/{id}/confirm` | `PENDING/DISMISSED → CONFIRMED` |
| POST | `/companion/memories/{id}/correct` | 改写文案并置 `CONFIRMED`（body: `{content}`） |
| POST | `/companion/memories/{id}/dismiss` | `PENDING/CONFIRMED → DISMISSED` |
| DELETE | `/companion/memories/{id}` | 软删为 `DELETED` |

所有操作先校验记忆属于当前登录主体的伙伴，状态机非法转换返回参数错误。

## 5. 前端最小页面

- `CompanionView.vue` 伙伴主页新增三块：
  - `CompanionMoodPanel.vue`：五轴强度条 + 「随时间自然回落」说明 + 伙伴口吻的情绪摘要（确定性前端函数，气泡展示）；
  - `CompanionRelationshipPanel.vue`：五轴展示 + 近期反馈方向；
  - `CompanionMemoryPanel.vue`：记忆列表（状态徽章、来源图片链接、置信度、时间），操作：确认 / 纠正（内联输入） / 忽略 / 删除；`INVALIDATED` 显示"来源图片已不可用"。
- 记忆文案与情绪摘要统一使用 `CompanionMessageBubble` 展示，与成长档案的喂养故事保持同一视觉语言。
- `api/companion.js` 增加五个记忆接口；`utils/companion.js` 增加情绪摘要纯函数；Node 原生测试覆盖摘要函数与面板状态转换。

## 6. 测试策略

- 领域单测：情绪范围/衰减/影响截断；关系范围/规则；记忆状态机、内容边界、终态不可变。
- 持久化集成（H2）：三表 CRUD、CAS 失败、唯一键冲突、迁移 update→rollback→update。
- 应用集成：喂养后情绪/关系/记忆原子生成；replay 不重复；Demo/元数据/视觉三档行为；撤权后列表惰性失效。
- 控制器：五个记忆端点的主体归属、状态机非法操作、删除图片后的失效传播。
- 前端：Node 测试覆盖情绪摘要与面板逻辑；浏览器 E2E 沿用 Demo 档验证记忆候选与确认流。
- 隐私约束延续：记忆不保存用户简介原文、图片 URL、模型原始响应；日志只记安全错误码与 correlation ID。

## 7. 明确不做

- 站内自由对话、主动提案、自主契约、敲打按钮（第二季度）。
- 记忆检索/语义召回（第二年）。
- 情绪事件的实时推送；情绪只在喂养与读取时更新。
- 用户自带 Token、模型控制中心、平台额度（第三季度）。
- 团队空间共同培养（数据模型预留 subjectId，行为后续实现）。

## 8. 风险与应对

| 风险 | 应对 |
| --- | --- |
| 情绪写入与喂养 CAS 竞争 | 独立行 + revision CAS；冲突整体回滚由幂等重试收敛 |
| 撤权传播逐条校验权限较慢 | 活跃记忆上限 100 条、个人规模可接受；预留批量接口 |
| 记忆内容泄漏用户原文 | 候选只来自经校验的伙伴独白/Demo 固定文案，不拼用户输入 |
| 情绪衰减与喂养并发读改写 | 同一 `companion_mood` 行短事务 CAS，失败重试一次 |
