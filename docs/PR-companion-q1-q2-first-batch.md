# PR：伙伴情绪、关系、记忆、站内对话与主动提案（Q1 收尾 + Q2 第一批）

> 分支：`deepseek/companion-mood-relationship-memory`（基于 `main` @ `11a6182`）
>
> 31 个提交，154 个文件，+9378/−53
>
> 对应三年规划：[roadmap](../docs/roadmap/2026-2029-image-life-companion-roadmap.md) 90 天清单 2-7 条 + 第二季度第一批

## 变更摘要

### 1. 伙伴情绪、关系状态与来源化记忆（90 天清单 2-4 条）

- **当前情绪**：五轴强度（精力/愉悦/孤独/灵感/烦躁），每小时衰减 5 点，与长期性格分离存储；
  喂养结算与主页读取分别惰性衰减（CAS + 只读快照外写回）。
- **关系状态**：按 `(伙伴, 主体)` 独立存储（熟悉/信任/亲密/默契/近期反馈），不随时间衰减。
- **来源化记忆**：只有真实视觉理解（或 Demo 档）产生候选；确认/纠正/忽略/删除；
  撤权失效传播覆盖列表与全部转移端点；`originalContent` 永远保留最初候选。
- **聊天气泡统一**：喂养故事/情绪摘要/记忆/提案/聊天回复统一走 `CompanionMessageBubble`。
- 新表：`companion_mood`、`companion_relationship`、`companion_memory`。

### 2. 生产安全与指标（90 天清单 5-6 条）

- 生产 CORS 白名单 fail-fast（缺 `CORS_ALLOWED_ORIGINS` 启动失败；非开发 profile 禁 `*`）。
- 关键漏斗/主动健康/Provider 成本结构化日志（`companion_*` 前缀，无内容原文）。
- 发布检查清单与季度出口条件验收文档。

### 3. 站内对话（第二季度第一批）

- 可解释上下文：等级/阶段/五轴性格/情绪/关系/最近 5 条确认记忆（120 码点截断），
  提示词只列已落库事实，模型不得编造。
- SSE 流式 + `DEMO_ONLY`（默认零外发）/`MODEL` 双档；每日轮次硬上限；
  提示词总码点预算守卫；消息只追加，当前消息不重复进上下文。
- 新表：`companion_chat_message`、`companion_chat_usage`。

### 4. 主动提案三段式 + 自主契约 + 敲打

- 机会源（按 `@Order` 短路）：每周回顾 → 纪念日（往年同月同日）→ 相似图片故事。
- 守门顺序固定：总开关 → 频率上限 → 安静时段（跨午夜）→ 间隔；失败只记原因码不打扰用户。
- 契约默认关闭；敲打立即抑制当前提案，30 天满 3 次才缓慢下调"好奇"性格（经 Balance 截断）。
- 提案生成由伙伴行锁串行化 + `pendingGuard` 生成列部分唯一约束双保险。
- 新表：`companion_autonomy_contract`、`companion_proposal`、`companion_proposal_reaction`。

### 5. 规格先行（后续季度）

- Q3「模型与 MCP 控制中心」设计规格；Q4「图片炼金 MVP」设计规格。

## 验证证据

- 后端：`mvn verify`（`-Dspring.profiles.active=test`）→ **337 测试全过，0 失败，JaCoCo 分支覆盖率门禁通过**。
- 前端：29 Node 测试 + ESLint 0 警告 + 生产构建 + 包体预算全过。
- 迁移：新表/索引/生成列 update→rollback→update 与崩溃恢复测试通过；新表全部加入两份 ShardingSphere `!SINGLE`。
- E2E 断言已更新（聊天/提案面板空态）；本地实跑需 Redis，留待 CI/人工。

## 审查历史

| 轮次 | 范围 | 结论 | 处理 |
| --- | --- | --- | --- |
| 1（独立 subagent） | 情绪/关系/记忆全量 | P1×1、P2×4 | 全部修复（`a9a3fb0`、`86a360b`） |
| 2（独立 subagent） | 聊天 + 提案 | P2×2、P3×13 | P2 全修（`4c22b3b`、`fb9623f`、`fd95770`），P3 按建议处理 |
| 3（独立 subagent） | 机会源增量 | P1×2、P2×3 | 全部修复（`d4414ac`） |
| 自查 | 全程 | — | 提案行锁串行化、聊天消息去重、预算守卫、SSE CRLF 等 |

## 合并前人工清单

- [ ] 生产冷启动验收（按[发布检查清单](docs/reviews/2026-08-14-production-security-release-checklist.md)执行）；
- [ ] 真实 MySQL 执行 20260814 系列迁移（生成列 + 部分唯一约束需 MySQL 8.0.16+）；
- [ ] `npm run test:e2e`（需本地 Redis）通过；
- [ ] 启用 `COMPANION_CHAT_POLICY=MODEL` 前确认 DashScope 语言模型配额与成本预期。
