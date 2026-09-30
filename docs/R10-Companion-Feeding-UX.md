# R10 — Companion Feeding UX

日期：2026-09-30。基于最新 `origin/main`，快进接入已提交的 R08 / R09，分支 `codex/r10-companion-feeding-ux`。

## 目标与取舍

照片桌承接 R08 的真实图片交接，让选图、确认、等待和结果在同一个地方完成。选择或拖入图片只产生候选，只有「喂给伙伴」发出 Feed 请求。沿用自己的私有空间入口、最近 12 张窗口；「挑更多」直达当前私有空间，其他图片沿用空间图片列表的拖放 / 图片详情交接；不增加上传队列、批量喂养或新的权限规则。空图库的上传入口也携带当前空间 ID。

采用页面内确认卡，不再叠一层 Modal，也不另造喂养角色或动画。等待只描述请求事实，不展示虚构进度、模型分析步骤或预估成长。真实回执到达后展示本次经验、结果类型、营养来源与内容理解标记；详细变化仍在成长档案。

## 功能单元与风险

1. 抽出可测试的前端请求会话：防连点、同键重试、确定拒绝、卸载失效、同标签页恢复。
2. 照片桌确认与回执组件：可见选图、明确提交、等待 / 未决 / 拒绝 / 完成反馈；键盘和触摸共用原生按钮。
3. 接入 Home 与 R08：锁住未决图片，恢复预览重新查权限，沿用 R06 的 Feed 请求事实和 R07 动画。
4. Node、浏览器故障场景与现有业务回归，随后 lint / build / bundle 和完整 E2E。

## 请求会话

`empty → selected → submitting → succeeded / uncertain / rejected`。

- `submitting` 同步上锁，阻止连点、换图和交接覆盖。
- `uncertain` 包括网络中断、超时和非确定性服务错误。保留图片与原请求键，只允许用户主动重试；不自动轮询或重发。
- HTTP 400 / 401 / 403 / 404 延续现有确定拒绝分类，清除未决键，允许重新选图。服务端每次执行仍负责授权。
- `succeeded` 只来自服务器 Feed 回执。查看档案或再选一张不会再次喂养；再提交需要新的用户确认。
- 在请求发出前，将版本、用户 ID、伙伴 ID、图片 ID、请求键保存在 `sessionStorage`。不保存图片 URL、名称、正文、回执或模型内容。刷新 / 离开再返回时恢复为 `uncertain`，不猜测后台是否完成。
- 恢复记录必须匹配当前用户与伙伴；损坏或不匹配的记录删除。账户切换清除本页面的恢复记录；卸载保留未决键，但丢弃晚到的 UI 回调。普通路由切换不需要拦截确认。
- 恢复校验与后端契约一致：图片 ID 为正的 Long 十进制字符串，请求键为 16–64 位小写字母、数字、下划线或连字符。空 ID、非法键不能锁住照片桌。
- 存储不可用时允许当前页面内同键重试，并明确提示离开 / 刷新后无法保证恢复。关闭标签页、清除浏览器数据、跨设备恢复不在本轮范围。

## 领域与后续边界

R10 会话只管理 HTTP 请求和用户选择，不定义 Mood、Relationship、成长、重复图片奖励或每日上限。成功后仍先按 revision 合并回执，再读取权威 Home；刷新失败保留成功回执并提示状态尚未刷新，不能变成「喂养失败」。动画继续读取 R06；Chat / Proposal 并发优先级保持 R07 规则。

Home 刷新结束前暂不允许下一次喂养，保持权威读取串行。零经验如实展示，不声称「获得了一点熟悉感」。`SKIPPED_FAMILIAR` 单独说明熟悉图片跳过视觉分析，不把它当作视觉服务降级。接口未提供内容理解标记时，不推断模型是否看过图片。

`CompanionFeedingCard` 负责确认 / 结果展示，`companionFeeding.js` 负责请求会话，Home 负责真实来源读取、R08 交接、回执合并和 R06 发布。恢复预览不存在时保留原 ID 与请求键，仍可由 Feed 接口重新授权并给出确定结果。触摸、键盘和鼠标共用确认 / 收回 / 再选 / 查看档案按钮；图片加载失败有文字退路，窄屏纵向排布，没有新增环境动效或 reduced-motion 负担。

不改后端、数据库或协议。R11 负责完整 Mood / Relationship 可视化；R12 负责 Chat / Proposal 重构。R09 的空间结构保持稳定。

## 验证

- Node 22.23.3：全前端 **96/96**，其中新增 7 组请求会话测试。请求状态机与恢复校验先写失败用例再实现；覆盖连点、同键重试、确定拒绝、卸载、账户隔离、损坏数据与存储不可用。
- 新增 **11 个浏览器用例**：键盘选择与确认、真实来源回执、零经验、刷新恢复、离开再返回、失去图片权限、成功后的 Home 刷新失败、熟悉图片跳过分析、串行刷新锁、存储拒绝、退出账号、登录重试，以及触摸 / reduced-motion / 图片失败 / 320px 窄屏。仅模拟 Chromium 触摸，未宣称实机验证。
- 独立只读检查发现空图片 ID 可锁死恢复会话，以及 `SKIPPED_FAMILIAR` 被误写成降级；已修复并补回归。恢复键长度、字符集与 Java 契约保持一致。
- 调整两处旧 E2E 的操作入口：未决期间从图库交接新图，完成后读取「再选一张」。仍保留原请求键与选择不被覆盖、并发 scold 后 Proposal 不复活的断言，两项定向回归通过。
- lint 零警告；开启 Companion 的生产 build 通过；bundle budget 通过，最大 chunk **396005 bytes**。无新增依赖、动画资产、后端或数据库变更。
- 生产 feature-off **1/1**：关闭后没有 Companion 路由内容、图片 / player / 互动入口或伙伴 API 请求。
- 最终全量 E2E：**107 passed / 1 skipped / 0 failed**（6.7m，无自动重试）。唯一 skip 是独立运行的生产 feature-off，已通过。全量包含 R05–R10 + Shell 的全部 69 个组合用例，以及真实后端唤醒、丢失响应后的同键恢复、成长不重复、Memory 生命周期、Proposal 契约、Story 创作和其他全站回归。
- 运行标准 `scripts/start-e2e-backend.mjs` 预热 Java 21 的 test/e2e 后端，使用 H2 内存库与本轮临时 Redis 6380。临时 Playwright 配置继承默认配置，仅复用预热后端，不筛选用例、不改超时和重试。截图在忽略目录 `li-picture-cloud-frontend/test-results/r10-full/`；检查了桌面回执与 390 / 320px 触摸确认、资源失败退路。结束后关闭本轮后端、Redis 和 Vite 服务。

复跑方式（前端目录、Node 22）：`npm test`、`npm run lint`、`npm run build`（`VITE_COMPANION_ENABLED=true`）、`npm run check:bundle`。R05–R10 与 Shell 组合：`node node_modules/@playwright/test/cli.js test --config e2e/config/companion-feeding.config.js`；全量：`npm run test:e2e`（Java 21、测试 Redis 6380）；生产关闭：`node node_modules/@playwright/test/cli.js test --config e2e/config/companion-disabled.config.js`。
