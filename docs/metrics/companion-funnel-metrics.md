# 伙伴关键漏斗、主动健康与 Provider 成本指标口径

> 日期：2026-08-14
>
> 对应三年规划「未来 90 天执行清单」第 6 条与第一季度交付"首批埋点"。
>
> 当前实现以**结构化日志**为唯一埋点通道（无独立分析 SDK）；日志字段只含
> subjectId / pictureId / companionId / memoryId / correlationId / 状态码，
> 不含聊天原文、记忆原文、图片 URL 或 Token。日志前缀统一为 `companion_*`，
> 便于按前缀聚合。

## 1. 关键漏斗

| 指标 | 日志事件 | 口径说明 |
| --- | --- | --- |
| 伙伴唤醒 | `companion_awakened` | 唤醒成功一次记一条；含 companionId/level/stage |
| 首次喂养 | `companion_feed_completed` + `eventType=PICTURE_FED` | 同一幂等键只记一次（完成即记，回放不重复） |
| 再次喂养 | `companion_feed_completed` + `eventType=PICTURE_REVISITED` | 重复图片的熟悉感结算 |
| 故事阅读（代理） | `companion_home_viewed` | 已唤醒主体加载伙伴主页；成长档案与记忆都在主页内，以主页加载为阅读代理 |
| 记忆操作 | `companion_memory_action`（action=confirm/correct/dismiss/delete） | 每条记忆每次有效操作一条 |
| 负反馈（代理） | `companion_memory_action` + `action=dismiss` | 第二季度"敲打"落地前，以记忆忽略为负反馈代理 |

## 2. 主动健康（第二季度已启用）

主动提案已落地，实际日志事件（前缀 `companion_proposal_*`，只含
subjectId/proposalId/opportunityType/reasonCode/type，不含提案文案）：

| 指标 | 日志事件 | 口径说明 |
| --- | --- | --- |
| 提案生成 | `companion_proposal_generated`（type=WEEKLY_REVIEW/ANNIVERSARY/SIMILAR_STORY） | 守门通过且机会源有候选时一条 |
| 守门抑制 | `companion_proposal_gated`（reason=CONTRACT_DISABLED/FREQUENCY_ZERO/QUIET_HOURS/FREQUENCY_BUDGET） | 按原因码聚合可得"安静时段/频率/开关各抑制了多少机会" |
| 接受/忽略 | `companion_proposal_reaction`（type=ACCEPT/IGNORE） | 展示→接受、展示→忽略转化 |
| 敲打 | `companion_proposal_reaction`（type=SCOLD）+ `companion_scold_trait_applied` | 前者计敲打率；后者计性格下调次数（30 天满 3 次触发） |
| 过期 | `companion_proposal_expire_conflict`（仅冲突时）/ 状态迁移 EXPIRED | 未响应抑制在下次读取时惰性发生 |

提案展示率、接受率、忽略率、敲打率、关闭率均可由上述事件推导；
口径与[主动提案闭环设计](../superpowers/specs/2026-08-14-companion-proactive-proposal-design.md)第 6 节一致。

## 3. Provider 成本

| 指标 | 日志事件 | 口径说明 |
| --- | --- | --- |
| 视觉调用次数 | `companion_vision_quota_reserved` | 每次真实预占一条（含 used/limit）；失败与降级不退款，因此预占数即成本发生数 |
| 视觉额度耗尽 | 业务异常 `FORBIDDEN_ERROR`（"今日视觉营养额度已用完"） | 出站前拦截，无对应预留日志 |
| 每次喂养的模型成本归属 | `companion_feed_completed` 的 mode/provider/model/confidence | 按 Provider/模型聚合单位喂养成本 |

## 4. 聚合建议

按日志前缀聚合：`companion_*`。建议的最小看板：

- 日活跃伙伴 = `companion_home_viewed` 的去重 subjectId；
- 唤醒→首次喂养转化 = `companion_awakened` 与 `companion_feed_completed(PICTURE_FED)` 的去重主体比；
- 再次喂养率 = 有 `PICTURE_REVISITED` 的主体 / 有 `PICTURE_FED` 的主体；
- 视觉日成本 = `companion_vision_quota_reserved` 按 subjectId+usageDate 求和。

## 5. 边界

- 无独立事件管线：指标依赖日志采集（生产环境按现有日志轮转/采集策略处理）。
- "故事阅读""负反馈"是代理指标；真实埋点（前端事件/行为 SDK）在指标验证有效后再引入。
- 主动健康、聊天成本等第二季度指标落地时补充本文件。
