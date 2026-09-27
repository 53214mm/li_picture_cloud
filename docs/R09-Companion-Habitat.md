# R09 — 留影小屋 / Companion Home & Habitat

## 基线与决定

2026-09-27。分支 `feat/r09-companion-habitat` 从最新 `origin/main` (`fe835eb`) 创建；main 尚未合入 R08，因此 fast-forward 接入已推送的 `913d2b5`，再开始 R09。没有修改 main 或重写 R08 历史。

本轮把 `/companion` 从功能堆叠页变成有入口、有房间、有生活区域的留影小屋。选择「常驻房间主视觉 + 页内区域导航 + 原业务组件」；不使用切换时卸载组件的标签页，也不把聊天、提案、喂养搬进第二套弹窗。这样既能让身体和空间占据首屏，也能保留 R06 观察源、R07 播放器、R08 图片交接与所有在途操作。

## 信息与视觉结构

- **留影小屋**：纸色墙面、拱窗、木色地面、书架和地毯作为静态布景，成年绫页在场景中心落地。布景不对应可购买/获得的物品，不表达时间、天气、旅行、事件或后台自主生活。只有真实实例存在才挂载身体。
- **照片留位**：没有选择时显示空相框；选择后只展示当前 picker 的图片，标明「当前选择」，不宣称已喂养或已成为记忆，不存储布置。权限与候选来源沿用 R08。
- **窗边对话**：原 Chat 与 Proposal，流式响应、契约、操作和反馈不变。
- **照片桌**：原 picker、Feed 确认、错误/重试与营养来源说明。只移动和调整布局，不改 R10 的完整喂养流程。
- **留影手记**：原 Memory 与 Story；故事仍是创作草稿，不冒充绫页的生平。尚未开放的 Emoji / Fusion 保留真实能力说明和既有记录。
- **相处与成长**：Mood、Relationship、Traits、Skills、成长记录退至下方。保留原数据与解释，不在 R09 改造 R11 的可视化规则。

现有按钮语义、焦点目标、服务端状态空态和失败恢复保留。页内导航只滚动并转移焦点，不改路由、不重建实例、不产生请求。小屏布局收为单列；减少动态、离屏与遮罩仍交给 R07。

## 可验证实施单元

1. 明确小屋状态文案只格式化 R06 协议，以测试先约束未知/陈旧/请求活动的安全输出。
2. 实现无 IO 的房间组件与当前图片留位；重排 Home 模板、局部样式和页内导航，保持业务方法与子组件生命周期。
3. 验证首屏主体、手机/平板/桌面、键盘、图片选择与 R08 交接、原有 Feed/Chat/Proposal/Memory/Story 回归、加载/错误/无实例及资源降级。
4. 跑 Node、E2E、lint、build、bundle，复核截图与差异，记录结果并提交推送；止于 R09。

## 实现分界

`CompanionHabitat.vue` 只负责布景、身体放置、当前图片预览和导航事件，不调用 API。`CompanionBody` 新增一个仅用于外观的 `habitat` 开关，去掉原独立卡片底色；R05 资产和落脚锚点、R07 动作与暂停、R08 身体入口仍由原组件负责。没有增加背景图片、字体下载或依赖。

`presentation/companionHabitat.js` 只把 R06 的 version / availability / activity / attention / freshness 格式化为有限文案。不读取 Mood 数值、Relationship、用户文本或时钟；未知协议/不可用状态不宣称人在小屋，陈旧空闲状态提示待更新。文案不反向写入 R06，也不影响 R07。

Home 保留现有观察源和所有业务子组件。区域导航只定位现有 input 或可聚焦标题；静态模式、长页面离屏与弹层暂停均沿用既有播放器机制。照片留位读取 `pictures` 中与 `selectedPictureId` 对应的一项，图片错误保留文字和导航；页面刷新清空这一临时选择。它既不是永久挂照片，也不是已确认记忆，未新增自动 Feed。

房间在 768px 以下改成纵向构图，保留身体、状态、照片留位和两个入口；所有生活区域单列排列。桌面采用图文留白与两列业务区域，较窄平板自动收起业务双列，避免压缩表单。样式只作用于 Companion 页面，Shell 不改版。

## 后续边界

- R10：图片递交、选图和喂养过程的完整 UX；本轮保留现有 picker、12 张上限、确认按钮和同键重试。
- R11：Mood / Relationship / Traits 可视化与解释；本轮只调整位置和页面配色，不改数值、阈值或领域意义。
- R12：Chat / Proposal 的完整表现与交互；本轮移动原组件，不改 SSE、契约、频率、确认和抑制流程。
- 旅行、事件、物品系统、长期小屋布置、多角色、背景日夜或天气不在本轮。所有布景都是静态装饰，未形成数据库或后端协议承诺。

## 验证记录

- Node 22.23.3：全前端 **89/89**（新增 2 组 R06 文案边界测试，先红后绿）；lint 零警告；开启 Companion 的生产 build 通过；bundle budget 通过，最大 chunk **390035 bytes**。R05 资产保持原文件，无新增依赖或后端/数据库变更。
- R05–R09 + Shell 组合 **57/57**；覆盖既有阶段、动画节奏、资源失败、遮罩/离屏、状态冲突、R08 拖放与权限、未决 Feed，以及首版 7 个小屋浏览器用例。
- 独立只读审查发现两个导航可访问性问题：目标被 sticky toolbar 遮挡；发送中 disabled input 无法接收焦点。均先用浏览器断言复现，再分别补实际目标的 `scroll-margin-top` 和对话标题回退。后者新增为第 8 个 R09 用例，单独回归通过，并检查等待状态及请求最终回复仍能抵达。
- 生产 feature-off **1/1**，关闭时仍没有 Companion 路由内容、身体资源、互动入口或伙伴 API 请求。
- 截图已检查 1440 桌面、1024/768 平板、390/320 窄屏、未唤醒空间，以及窄屏下各业务区域。触摸由 Chromium 390×844 模拟；未宣称实机移动设备验证。已确认角色完整落地、首次视口内可见、数据在下方、无横向溢出。最终截图保存在忽略目录 `li-picture-cloud-frontend/test-results/r09-full/`。
- 最终默认全量 E2E：**96 passed / 1 skipped / 0 failed**（9.8m，无自动重试）。唯一 skip 是要求单独执行的生产 feature-off，上条已通过。覆盖全部既有 spec 与新增 8 个 R09 用例；真实后端的唤醒、幂等喂养恢复、实例/成长保持、Memory 生命周期、Proposal 契约和 Story 创作闭环全部通过。
- 全量测试采用标准 `scripts/start-e2e-backend.mjs` 预热 Java 21 的 test/e2e 后端，H2 内存库与本轮临时 Redis 6380。临时 Playwright 配置继承默认配置，只将后端改为复用已预热进程，未筛选用例、修改超时或增加重试。结束后关闭本轮 Java / Redis / Vite 服务。

复跑方式（前端目录、Node 22）：`npm test`、`npm run lint`、`npm run build`（`VITE_COMPANION_ENABLED=true`）、`npm run check:bundle`。小屋与前轮组合：`node node_modules/@playwright/test/cli.js test --config e2e/config/companion-habitat.config.js`；全量：`npm run test:e2e`（Java 21 与测试 Redis 6380）；生产关闭：`node node_modules/@playwright/test/cli.js test --config e2e/config/companion-disabled.config.js`。
