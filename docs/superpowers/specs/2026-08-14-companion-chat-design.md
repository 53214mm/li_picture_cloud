# 伙伴站内对话设计

> 日期：2026-08-14
>
> 依据：三年规划第二季度"站内陪伴与主动提案"第一项：伙伴站内聊天，以统一聊天气泡承载喂养故事和普通互动；将稳定人格、当前情绪、关系状态和检索记忆组合成可解释上下文。

## 1. 目标

伙伴主页内提供**被动对话**（用户发起、伙伴回应）：对话以聊天气泡展示，
伙伴回复基于可解释的上下文（人格 + 情绪 + 关系 + 已确认记忆），
第一版不接工具、不做主动插话。主动提案在独立规格中另立阶段。

## 2. 关键决策

1. **模型通道**：复用项目已有 `dashScopeChatModel`（Spring AI DashScope，qwen-max），
   伙伴对话不接工具（`ToolCallback`），只做 persona 对话。
2. **双档策略**：`chat-policy=DEMO_ONLY`（默认，无模型调用，确定性回复，测试/E2E 稳定）
   或 `MODEL`（生产显式开启）。Demo 档从不外发用户消息。
3. **上下文可解释**：系统提示词由后端从**已持久化事实**组装（等级/阶段、五轴性格摘要、
   情绪摘要、关系摘要、最近 5 条 CONFIRMED 记忆，每条截断 120 码点）；模型只引用提示词内
   列出的记忆，未列出的内容不得声称记得。
4. **成本与防刷**：每主体每日聊天轮次上限（默认 50），出站前预占、失败不退款，模式同视觉日额度。
5. **隐私**：Demo 档零外发；MODEL 档只外发"本轮用户消息 + 组装好的上下文摘要"，
   不含图片 URL、Token、记忆原文之外的任何库内数据；日志不记录聊天内容原文。

## 3. 数据模型

```sql
companion_chat_message -- 追加式：id, companionId, subjectId, role(USER/COMPANION),
                       -- content(≤1000 码点、无 ISO 控制字符), modelProvider, modelCode,
                       -- revision, createTime；INDEX (companionId, createTime, id)
companion_chat_usage   -- 日桶：id, subjectId, usageDate, attempts, revision；
                       -- UNIQUE (subjectId, usageDate)，模式同 companion_vision_usage
```

两张表加入两份 ShardingSphere `!SINGLE`；迁移沿用每 DDL 一个 changeSet + 可恢复 precondition。

## 4. 应用流程

### 4.1 发送一轮对话

1. 校验消息 1–500 码点、无 ISO 控制字符；
2. `ChatQuotaGuard.reserve(subjectId, 上海日, dailyLimit)` 预占（耗尽 → `FORBIDDEN_ERROR`）；
3. 落库 USER 消息（同事务）；
4. Demo 档：确定性模板回复（情绪/记忆/关系关键词路由，固定文案）；MODEL 档：
   `ChatClient` 以 persona 提示词 + 最近 `chatHistoryLimit` 条历史 + 组装上下文流式生成；
5. 流结束后把完整回复落库 COMPANION 消息（含 provider/model）；中断/失败不落库，
   额度不退款。

### 4.2 历史

`GET /companion/chat/history?limit=50` 返回最近消息（倒序），只属于当前主体的伙伴。

### 4.3 指标

- `companion_chat_message_sent`（subjectId, policy）
- `companion_chat_reply_completed`（subjectId, provider, model, characters）

## 5. API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/companion/chat/history?limit=50` | 历史消息 |
| POST | `/companion/chat/stream`（`{message}`） | SSE 流式回复；限流 + 长度校验 |

## 6. 前端

- `CompanionChatPanel.vue`：伙伴页新增对话卡片；伙伴消息复用 `CompanionMessageBubble`，
  用户消息使用同款右侧气泡样式（同一视觉语言）；`fetch` + `ReadableStream` 消费 SSE。
- 流式解析函数放 `utils/companion.js` 并配 Node 测试。

## 7. 测试

- 领域：消息校验边界；额度日桶并发（复用视觉额度测试模式）。
- 服务：Demo 回复确定性；上下文组装（记忆截断、空记忆不编造、字段脱敏）；额度耗尽。
- 控制器：鉴权、长度/空白校验、流端点存在性。
- 迁移：新表 update→rollback→update；`!SINGLE` 路由测试。
- 前端：Node 测试（流解析 + 组件契约）。

## 8. 明确不做（本阶段）

- 聊天工具调用、图片操作能力（后续统一能力内核）。
- 主动插话与提案（独立规格另立阶段）。
- 语义检索记忆（第二年）；本阶段只按时间取最近确认记忆。
- 语音、桌宠、IM 渠道。
