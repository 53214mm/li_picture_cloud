# R07 — Companion Animation State Machine

## 边界与设计

2026-09-27，基于最新 `origin/main` / R06 `57426a2`，分支 `feat/r07-companion-animation-state`。仅修改前端动画消费、测试和本文；已有工作区修改保持原样。R06 是唯一 Presentation State 来源，不读原始业务事实、文本或 HTTP，不改变 Mood / Relationship / Feed / Chat / Proposal。

比较过三种方案：在各组件内独立猜动作会重复优先级；全局动作事件队列需要 R06 未提供的事件 ID，易重播旧成功；选择 **纯映射 + Home 实例内的单计时器控制器**。Shell / Portrait 共用映射但保持静态，无动画时钟或图集请求。动画不会发出任何业务写入。

## 动作与优先级

| 动画意图 | R06 条件 | 实际播放 |
| --- | --- | --- |
| static | 协议版本未知、非 ready、缺实例、未知资产/视觉阶段、allowIdle 非 true、未知 activity | 中性静态立绘 |
| focus | responding / thinking / feeding / acknowledging | 一组轻微下沉与眨眼的专注循环，共用动作，不声称说话、进食或成功 |
| attention | idle 且 attention=proposal | 一次双眨眼提示，结束自动回 idle |
| idle | 其他合法 idle | R05 睁眼 4200ms、闭眼 140ms |

优先级：静态安全条件 > R06 当前请求活动 > 提案提示 > idle。R06 已裁决 responding > thinking > feeding > acknowledging，R07 不重算它。四类请求共用 focus，活动交接不会重启动作。affect / rapport / freshness / issues 本轮不增加表情，更新这些字段不重置时钟。

复用原有 `adult-home-v1.webp` / 两帧 `adult-idle-v1.webp`；focus 只在现有 Sprite 上作最大 2 CSS px 的垂直位移，attention 只改变眨眼节奏。无新栅格资产、音频、依赖或动画引擎。Portrait / Shell 不伪造尚不存在的表情。

focus 周期为睁眼原位 1600ms → 睁眼下沉 2px / 220ms → 闭眼下沉 2px / 140ms → 睁眼原位 1800ms。attention 为睁眼 600ms → 闭眼 140ms → 睁眼 180ms → 闭眼 140ms，总计 1060ms，然后进入 idle。这些动作是中性表现，不是情绪/关系或操作结果。

代码入口：`src/presentation/companionAnimation.js` 包含纯映射、有限动作表和单 timer 控制器；`SpritePlayer.vue` 只提供资源/可见性许可并画帧；`CompanionBody.vue` 按实例 ID 管理播放器生命周期与用户暂停。静态入口复用映射。原 `spriteClock.js` 已删除，其时钟回归迁至 `tests/companionAnimation.test.mjs`，避免保留两套调度器。`data-animation-intent` 是映射意图，`data-animation-state` 是当前实际渲染状态，因此暂停、资源失败或提示播放完后两者可以不同。

## 中断、恢复和降级

- 活动立即中断提案提示；活动结束按**最新**意图恢复，没有挂起动作栈，也不播放成功庆祝或回执。
- 提示一旦开始即消耗本次 SpritePlayer 挂载中该实例的提示额度；播放完、被活动/暂停中断、提案刷新后均不重复。R06 没有 proposal ID，因此不猜“新提案”，保守地每实例每次播放器挂载至多提示一次。尚未播放的提示仅在当前仍为 proposal 且可播放时启动。离开页面、实例切换或静态安全条件导致播放器卸载后，重新挂载会获得新额度；普通活动/暂停/刷新不重建播放器。
- 页面隐藏、离屏、遮罩/inert、外部 paused/visible、用户暂停和 reduced-motion 都停止 timeout，并回中性第一帧。恢复从当前动作第一步完整停留开始，不追赶后台时间。已消费的提示恢复为 idle；隐藏期间已消失的提示不补播。
- 实例切换清除旧动画周期与提示额度；卸载永久销毁控制器。代数屏障阻断中断前、卸载后晚到的 timeout 回调。
- 首帧就绪且允许动态效果时才请求图集。图集失败本次挂载不重试，回静态首帧；首帧失败回有名称、固定几何的占位。失败不阻断聊天/导航，也不改变 R06。
- 无 IntersectionObserver 时保守静态；初始 reduced-motion 不请求图集。Shell / Portrait 从不启动播放器。

## 可验证实施单元

1. 先定义纯映射及控制器测试：并发结果消费、无文本推断、提示额度、切换/恢复、单 timer、过期回调与卸载。
2. 实现映射/控制器并接入 SpritePlayer；保持 Home 暂停控件、原资产尺寸及静态入口。
3. 浏览器验证真实组件的请求竞争、动作帧/位移、隐藏/恢复、资源失败、缩小动态与路由清理；跑全前端测试、lint、build、包体和 R05/R06 回归。
4. 独立检查状态机及生命周期边界，记录结果，限定文件提交推送；止于 R07。

## 验证记录

Node 22.23.3、Windows、本机 Chromium：

- 全前端 Node 测试 **82/82**。新增 8 组动画测试覆盖 R06 全部 12 种并发组合、异常/未知协议与资产、无文本/情绪猜测、单计时器、循环交接、一次提示、中断与后台恢复、换实例、晚到回调和永久销毁。替换旧时钟的 4 条测试，不降低原资产哈希/预算或阶段兼容覆盖。
- 浏览器组合 **24/24**：R07 5 条、R05 身体 11 条、R06 协议 8 条。R07 检查真实图集帧与 CSS 位移、三入口意图、Chat/Feed/Proposal 并发、暂停/隐藏后的最新状态、reduced-motion 无图集请求、图集失败不重试。沿用 R05 的遮罩/离屏、三次路由卸载、全部资产失败、四阶段和 320–1440 宽度检查。
- feature-off 生产构建与浏览器 **1/1**：无伙伴路由内容、资产、播放器及伙伴 API 请求。
- lint（0 警告）、开启伙伴功能的 production build、包体检查通过；最大 JS chunk **395,986 bytes**，低于 500 KiB。现有四份资产仍为 **215,410 bytes**，无新增传输或解码资源。
- 已检查本轮桌面提示/专注和手机 Idle 截图；Idle 实测闭眼约 **150ms**、睁眼约 **4200ms**。本机无网络节流，手机为浏览器尺寸模拟；不宣称实体手机功耗或公网性能。
- 独立只读审查未发现阻断性缺陷，修正了“Home 挂载”与“播放器挂载”的提示额度措辞。

首轮组合 23/24：清理旧 `lingyeAssets` 配置时触发 Vite HMR，`CompanionBody` 被重建，正在采样旧 DOM 的真实节奏测试超时。trace 记录了该时刻的组件 hot updated。停止源码修改后，未更改产品行为或测试、未添加自动重试，完整重跑 **24/24** 通过。

真实后端 Companion 故事线 **4/4**：JDK 21、`test,e2e` profile、H2 内存库与本轮启动的临时无持久化 Redis 6380。覆盖唤醒、同 key 喂养重试/无重复成长、Mood/Relationship 刷新、Chat、Memory 生命周期、Proposal 契约与身份/成长/档案保持。为并行预热后端，本次使用临时 Playwright 配置，仅将既有 `companion.spec.js` 指向已启动的同一测试后端；用例本身未修改。测试后 Java / Redis / Vite 均已关闭，没有修改原 Redis 配置。浏览器合计 **29 个不同用例通过**。

R06 协议、观察源、业务页面和 Feed/Chat/Proposal 请求实现、数据库/后端协议、领域规则及原始图像均无本轮改动。工作区原有暂存文档删除、Java 注释、其他 E2E 和 pnpm 文件不包含在 R07 提交中。完成范围止于 R07。

可重跑命令（前端目录，Node 22）：

```text
node --test tests/*.test.mjs
node node_modules/eslint/bin/eslint.js . --max-warnings 0
node node_modules/@playwright/test/cli.js test --config e2e/config/companion-animation.config.js
node node_modules/@playwright/test/cli.js test --config e2e/config/companion-disabled.config.js
# 环境 VITE_COMPANION_ENABLED=true：
node node_modules/vite/bin/vite.js build
node scripts/check-bundle-size.mjs
# JDK 21、测试 Redis 6380 就绪：
node node_modules/@playwright/test/cli.js test e2e/companion.spec.js
```
