# 伙伴站内对话与主动提案指南

> 日期：2026-08-14
>
> 依据：三年规划第二季度"站内陪伴与主动提案"；设计见[站内对话设计](../superpowers/specs/2026-08-14-companion-chat-design.md)与[主动提案闭环设计](../superpowers/specs/2026-08-14-companion-proactive-proposal-design.md)。

## 这次交付了什么

伙伴主页新增两块第二季度能力：

1. **站内对话**：用户可以主动和伙伴聊天。伙伴的回复基于**可解释上下文**
   （等级/阶段、五轴性格、当前情绪、关系状态、最近 5 条已确认记忆），
   只会引用提示词里列出的记忆，不编造经历。默认 `DEMO_ONLY` 档零外发；
   生产显式配置 `MODEL` 档后走平台语言模型（DashScope，qwen-max），每主体每日轮次硬上限。
2. **主动提案（三段式第一版）**：`Observe`（三个机会源：每周回顾 / 纪念日 / 相似图片故事）
   → `Propose`（自主契约守门：总开关 → 频率 → 安静时段 → 间隔）→ 用户反馈
   （接受 / 忽略 / 敲打）。契约默认关闭；敲打立即抑制当前提案，
   30 天内满 3 次才缓慢下调"好奇"性格。提案在读取时惰性生成与惰性过期（48 小时）。
   机会源按注册顺序尝试，第一个有候选的产生提案。

## 配置

| 变量 | 默认值 | 作用 |
| --- | --- | --- |
| `COMPANION_CHAT_POLICY` | `DEMO_ONLY` | 对话策略：`DEMO_ONLY`（零外发）或 `MODEL` |
| `COMPANION_CHAT_DAILY_LIMIT` | `50` | 每主体每日对话轮次上限 |
| `COMPANION_CHAT_HISTORY_LIMIT` | `20` | 每轮带进模型的最近消息数上限 |
| `COMPANION_CHAT_MEMORY_LIMIT` | `5` | 每轮带进模型的最近确认记忆数 |
| `COMPANION_CHAT_CONTEXT_BUDGET` | `12000` | MODEL 档每轮提示词总码点预算（系统提示 + 历史 + 当前消息），超出从最旧历史截断 |

## API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/companion/chat/history?limit=50` | 对话历史（正序） |
| POST | `/companion/chat/stream` | SSE 流式回复（body `{message}`，1-500 字） |
| GET | `/companion/contract` | 自主契约 |
| PUT | `/companion/contract` | 更新契约（active/quietStart/quietEnd/maxFrequencyHours） |
| GET | `/companion/proposals/active` | 当前活跃提案（惰性生成/过期） |
| POST | `/companion/proposals/{id}/accept` `/ignore` `/scold` | 反馈 |

## 数据模型

- `companion_chat_message`：追加式消息（USER/COMPANION + provider/model）。
- `companion_chat_usage`：每日轮次日桶（模式同视觉额度）。
- `companion_autonomy_contract`：每伙伴每主体一行（开关、安静时段、频率、revision）。
- `companion_proposal`：提案（机会类型、冲动得分、文案、状态机、守门原因）。
- `companion_proposal_reaction`：追加式反馈（ACCEPT/IGNORE/SCOLD）。

## 验证命令

后端聚焦验证：

```powershell
.\scripts\mvnw-java21.ps1 "-Dtest=CompanionChatMessageTest,CompanionChatServiceTest,CompanionChatPersistenceIntegrationTest" test
.\scripts\mvnw-java21.ps1 "-Dtest=ProposalGateTest,CompanionProposalTest,CompanionAutonomyContractTest,CompanionProposalServiceTest" test
.\scripts\mvnw-java21.ps1 "-Dtest=CompanionSchemaMigrationTest" test
.\scripts\mvnw-java21.ps1 -B "-Dspring.profiles.active=test" verify
```

前端验证：

```powershell
Set-Location li-picture-cloud-frontend
npm test
npm run lint
npm run build
npm run check:bundle
```

## 边界与隐私

- Demo 档不发起模型调用，用户消息只存库不外发；MODEL 档只外发
  "本轮消息 + 组装好的上下文摘要 + 最近历史"，不含图片 URL、Token 或记忆原文之外的数据。
- 日志只记 subjectId/字数/provider/model/原因码，不记录聊天或提案内容原文。
- 契约只能收紧主动空间；守门失败只写指标，不打扰用户。
- 敲打≠删除：单次敲打只抑制当前提案，重复敲打才缓慢影响"好奇"性格（经 `CompanionBalance` 截断）。
- 主动健康指标口径见[指标文档](../metrics/companion-funnel-metrics.md)第 2 节。
