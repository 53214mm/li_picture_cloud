# R08 — Companion Global Interaction

## 决策与边界

2026-09-27，基线最新 `main` / `fe835eb`，分支 `feat/r08-companion-global-interaction`。R05 角色与资产、R06 事实投影、R07 动画调度保持各自职责。

选择固定 Shell 槽位 + 同一原生 dialog 互动抽屉。桌面角色按钮与「伙伴空间」导航并列；手机底栏的伙伴按钮打开相同抽屉。Home 身体和聊天头像也能打开它。不自动弹出、不增加悬浮球，不复制聊天或喂养表单。相比 Hover Popover，显式打开的抽屉可以一致支持触摸、键盘、焦点约束和屏幕阅读器，并复用 Shell 的 top-layer / inert / Escape / 滚动锁。

交互状态是应用实例内短暂的 UI 指令：打开面板、站内拖拽票据、待选择图片。它不写 R06、不持久化、不模拟 Mood/Relationship。R06 在离开 Home 后仍为 unobserved；抽屉不请求伙伴状态或主动提案，不假装知道该用户是否拥有伙伴。

## 首版范围

- Hover/Focus：静态入口有边框与焦点反馈，不自动开面板。Home 身体一次短眨眼由 R07 接收直接输入触发；静态保护 > 业务 focus > proposal attention > 直接输入提示 > idle。被暂停、隐藏、忙碌或 proposal 压过的直接输入不排队补播。
- Click/Tap/Enter/Space：原生按钮打开同一互动抽屉，提供「聊一聊」「选择图片」「打开伙伴空间」。Escape/关闭/背景关闭，焦点回触发点；导航后焦点交给目标控件。原有暂停动作按钮保持独立。
- 图片拖拽：Gallery、PictureList、Picture 详情及现有 Companion picker 的站内图片可拖到桌面 Shell 或 Home 身体。只接受本应用本账户这次拖拽的随机票据；不解析文本、URL、文件或任意外部 JSON。拖放和详情页「选给绫页」按钮走同一校验流程，后者为键盘/触摸提供等价入口。
- 校验通过只代表可交接的图片候选。通过已有图片详情/空间详情 API 重新读取，限定当前用户自己的私有空间，与现有 Companion 选图 UI 范围一致。后端 Feed 的实际授权是 PICTURE_VIEW，这里采用更窄的前端入口范围，不把“公开/团队图片不可从此入口选择”说成后端禁止。
- 交接仅预选到现有 Companion picker，仍需用户点击既有「喂给伙伴」。进入 Home 再次读取并验证；换账号、关闭、换图或卸载使旧读取无效。未决喂养重试不能被交接图片覆盖。R10 再做完整 Feeding UX，R09 再改 Habitat/Home。

## 实施与验证单元

1. 测试先定义 ID/权限范围、一次性拖拽票据、晚到响应和 R07 直接输入裁决。
2. 实现轻量指令 store、抽屉与入口，复用 Shell overlay；接入站内图片拖拽与现有选图交接。
3. 浏览器验证鼠标/键盘/触摸、焦点与 modal 排他、非法拖拽/拒绝/竞态、零隐式 Feed、动画中断恢复和 feature-off；跑前端全测试、lint、build、bundle、关键业务与 Shell 回归。
4. 独立审查全局生命周期及权限边界，补充验证记录，提交推送；止于 R08。

## 组件与生命周期

| 模块 | 职责 |
| --- | --- |
| `presentation/companionInteraction.js` | ID 无损校验、一次性拖拽会话、服务端候选复核、异步代次隔离；可纯函数测试 |
| `stores/companionInteraction.js` | 应用内短暂指令与候选状态，复用现有图片/空间 API，不调用 Feed/Chat/Proposal |
| `AppLayout` / `CompanionInteractionPanel` | Shell 的同一个 panel 开关保证导航、账户、互动抽屉排他；复用原生 ShellDialog |
| `CompanionPresenceSlot` / `CompanionBody` / Chat Portrait | 原生按钮入口与可见反馈；仅 Body 向 SpritePlayer 发送直接输入序号 |
| `CompanionView` | 消费导航指令，重新校验、预选并定位现有控件；保留旧的 Feed 请求与幂等规则 |

候选状态为 `null → loading → ready/error`。关闭、下一次打开、离开当前路由或换账号使上一代结果失效，错误只显示统一说明。拖拽票据仅存在于当前应用实例内，同次拖拽只可消费一次；拖拽结束、隐藏页面、关闭面板、换路由、换账号或卸载都清理它。跨标签页、外部文件/链接拖拽不接入。

导航指令只记录 `chat/feed` 目标、可选 pictureId 和账户标识。只有前往 `/companion` 才保留交接，其他路由清除；Home 成功读取伙伴后消费，读取失败或伙伴尚未唤醒时等待现有页面流程。图片交接还等待当前图库读取结束，随后再次复核并将候选置于既有 12 张 picker 的首位。若来自另一个自己的私有空间，则显示该空间的候选，不混入其他空间图片。聊天定位不依赖图库读取。

Home 中手动改选、开始 Feed、新指令或卸载都会使旧候选复核失效；账户变化也禁止旧结果回写。进行中的 Feed 或结果未知、保留幂等键的重试优先，图片交接不能改选或替换该键。交接失败保留原选择。前端复核不替代服务端授权，确认喂养仍由已有 Feed API 最终校验。

## 动画协调与各端降级

R06 仍是业务 Presentation State 的唯一来源；直接输入序号只表示用户进入了身体的 Hover/Focus 区域。Hover 与 Focus 合并，重叠时不重复触发。R07 仲裁顺序如下：

1. `static`：R06 不可播放或既有隐藏、离屏、modal、用户暂停、reduced-motion、资源错误等保护条件。
2. `focus`：R06 已裁决的 Feed / Chat / Proposal 业务活动。
3. `attention`：R06 有效 proposal 提示，保持 R07 的一次性额度。
4. `greeting`：空闲时用户直接进入身体，播放既有 atlas 的 `0(160ms) → 1(140ms) → 0(160ms)`，之后回 idle。
5. `idle`：沿用 R07 闲置眨眼。

新输入不会重启正在播放的 greeting；高优先级状态立即打断它。被保护或业务状态压过的输入当场消费，恢复后不补播；退出抽屉后按最新 R06 状态继续。控制器仍只有一个 timeout，卸载清理、失败静态回退沿用 R07。没有新增动作素材、动画依赖、计时器循环或文本关键词推断。

| 环境 | 支持方式 |
| --- | --- |
| 桌面鼠标 | Shell 角色按钮、Home 身体、聊天头像点击；Body Hover 短眨眼；站内原生图片拖放 |
| 键盘 | Tab 可到达入口，Enter/Space 打开；抽屉约束焦点，Escape 关闭并归还焦点；详情按钮替代拖放 |
| 窄屏/触摸 | 底栏伙伴按钮、身体/头像点按、详情「选给绫页」；不依赖 Hover 或拖拽；抽屉可滚动 |
| reduced-motion / 暂停 / 图片失败 | 静态角色或 R05 占位，语义按钮与正常选图、导航仍可用 |
| feature-off | 无伙伴互动按钮、图片候选入口或伙伴 API/图片请求；保留原功能关闭行为 |

不提供跨站收图、上传/批量队列、自动投喂、全局聊天输入、主动消息推送或新的 Habitat。R09 负责 Home/Habitat，R10 负责完整 Feeding UX；本轮没有数据库、后端协议、领域规则或资产文件变更。

## 验证记录

- 严格先测后实现：ID/权限、票据、晚到响应、greeting 裁决先出现失败，再实现；独立审查发现聊天定位被无关图库加载阻塞，浏览器回归先复现再修复；自检补测并修复手动改选后的残留等待提示。
- Node 22.23.3：`npm test` **87/87**；`npm run lint` 零 warning；启用 Companion 的 `npm run build` 成功；`npm run check:bundle` 通过，最大 chunk 为 ECharts **390035 bytes**，未新增依赖或资产。
- 第一轮 R05–R08 / Shell 组合 E2E **49/49** 通过；补充手动改选竞态后并入最终全量回归。
- 生产 feature-off：`node node_modules/@playwright/test/cli.js test --config e2e/config/companion-disabled.config.js` **1/1**；包含新增全局按钮与详情按钮缺席检查。
- 最终默认全量 E2E：**88 passed / 1 skipped / 0 failed**（5.0m，无重试）。唯一 skip 是必须单独运行的生产 feature-off，上条已单独通过。包含 R05–R08、Shell、图库、Landing、真实后端 Companion 四段故事线、MCP 管理、模型网关、Recipe、故事生成等全部既有 spec。测试后端使用标准 `scripts/start-e2e-backend.mjs`（Java 21、test/e2e、H2 与临时 Redis 6380），预先启动；临时 Playwright 配置继承 `playwright.config.js`，仅复用该后端并单独启动 Vite，未过滤用例或改断言。
- R08 新增 **10** 条浏览器用例，覆盖桌面原生拖拽、键盘焦点闭环、触摸替代、与旧 modal 排他、外部/伪造拖拽拒绝、公共/团队/其他用户空间拒绝、权限撤回、晚到响应、未决 Feed 幂等保护和 R07 中断恢复。所有候选交接断言零隐式 Feed。
- 已人工检查桌面 1280×720、触摸模拟 390×844 的互动抽屉截图，布局、入口与焦点环正常，无横向溢出。图片背景使用 API fixture；浏览器证据为 Windows Chromium 和触摸模拟，未宣称实机 iOS/Android 验证。截图存于忽略目录 `li-picture-cloud-frontend/test-results/r08-full/`。
- 复跑方式：标准全量 `npm run test:e2e`（需测试 Redis 6380）；不依赖后端的 R05–R08/Shell 组合 `node node_modules/@playwright/test/cli.js test --config e2e/config/companion-interaction.config.js`；生产关闭用例按上条单独执行。不要在进行中的 Vite 浏览器验证期间修改组件文件，以免 HMR 改变页面生命周期。
