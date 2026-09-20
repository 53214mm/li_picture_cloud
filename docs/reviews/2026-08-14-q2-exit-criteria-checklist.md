# 第二季度出口条件验收清单（站内陪伴与主动提案）

> 日期：2026-08-14
>
> 依据：三年规划第 5 节"第二季度出口条件"。本文是人工验收签字单，逐项打勾后才认为 Q2 第一批（站内对话 + 主动提案）完成。

## 出口条件

### 1. 关闭主动功能后所有新提案立即停止

- [ ] 契约 `active=false`（默认）时，`GET /companion/proposals/active` 不产生任何新提案，只返回 null；
- [ ] 用户关闭开关后，已有的 PENDING 提案不消失但不再新增；
- [ ] 守门原因码（`CONTRACT_DISABLED/FREQUENCY_ZERO/QUIET_HOURS/FREQUENCY_BUDGET`）出现在日志，用户不可见。

### 2. 离线默认最多每 72 小时排队一个站内提案，且可设为零

- [ ] 契约默认 `maxFrequencyHours=72`；两次提案间隔不足 72 小时时第二提案被 `FREQUENCY_BUDGET` 拦截；
- [ ] `maxFrequencyHours=0` 时完全不提案；
- [ ] 安静时段（默认 23:00-08:00，跨午夜）内不提案；起止相同表示不设安静时段。

### 3. 伙伴不能因高好奇突破安静时段、次数、预算或权限

- [ ] 冲动得分（情绪/关系计算）只影响"是否生成候选"，守门顺序先于候选生成；
- [ ] 敲打立即抑制当前提案（`SUPPRESSED/SCOLDED`），不依赖任何性格数值；
- [ ] 30 天内满 3 次敲打才下调"好奇"性格，且下调量经 `CompanionBalance.applyTrait` 截断，不能跌破软限。

### 4. 主动提案的接受、忽略、敲打和关闭原因均可观测

- [ ] 日志包含 `companion_proposal_generated/gated/reaction`（含 subjectId/proposalId/type/reasonCode），见[指标口径](../metrics/companion-funnel-metrics.md)第 2 节；
- [ ] 三种机会源（每周回顾 / 纪念日 / 相似图片）的生成与拦截都可从日志区分。

### 5. 站内对话可用且不产生非预期成本（第二批出口条件补充）

- [ ] Demo 档（默认）对话可用、零模型调用、每轮回复确定性；
- [ ] `MODEL` 档只外发：组装好的上下文 + 最近历史 + 当前消息（当前消息不重复）；
- [ ] 每主体每日对话轮次预占在消息落库前，失败/中断不退还；
- [ ] 历史接口只返回当前主体伙伴的消息，正序展示。

## 自动化证据（验收时记录）

| 项 | 命令 | 期望 |
| --- | --- | --- |
| 后端 | `.\scripts\mvnw-java21.ps1 -B "-Dspring.profiles.active=test" verify` | BUILD SUCCESS，JaCoCo 门禁通过 |
| 前端 | `npm test` / `npm run lint` / `npm run build` / `npm run check:bundle` | 全过 |
| E2E | `npm run test:e2e`（需本地 Redis） | 唤醒、喂养、对话面板、提案空态流程通过 |

## 签字

| 角色 | 姓名 | 结论 | 日期 |
| --- | --- | --- | --- |
| 实现人 | | | |
| 领域审核人 | | | |
| 安全审核人 | | | |
