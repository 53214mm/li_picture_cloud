# R02 App Shell — 方案 A 实现记录

日期：2026-09-21。阶段：实现 / 迭代；不代表第三阶段完整验收已经完成。

## 基线与范围

- 分支：`kimi`。
- 起始提交：`17d9647f1385ed5bc6f1eaff5d88e763a7c8bd3e`（R02 需求确认）。
- 按已确认的方案 A 实施；R01 heading 继承修复保持不变。
- 原有后端修改、文档删除/移动、已暂存文档均未操作。第一版实现已以 `0192c2264cf3f9256793385d69d440f1e9674da2` 提交并推送到 `origin/kimi`。
- 未增加依赖；未改 API、数据库、Pinia 用户状态、后端或业务页面结构。

## 功能单元与文件

以下前端路径相对于 `li-picture-cloud-frontend/`。

### 1. 布局与认证边界

- 新增 `src/layouts/PublicLayout.vue`：官网导航、公开内容、备案页脚。
- 新增 `src/layouts/AppLayout.vue`：侧栏、工作栏、主区域、底导、账户及导航面板。
- 新增 `src/constants/shell.js`：现有路由的 layout、workspace、frame、标题、分区、认证和管理权限元数据。
- 新增 `src/utils/shellAccess.js`：认证加载/失败/访客/无权限门禁，以及安全登录回跳。
- 修改 `src/App.vue`：按路由和认证状态选择壳；受保护页面仅在认证成功后挂载；网络失败可重试。
- 修改 `src/router/index.js`：保留现有 URL 和懒加载；补全认证边界、adaptive 公开页及不可用兜底。
- 修改 `src/views/LoginView.vue`：只调整成功后的目的地，安全原目标优先，否则 `/space/my`。
- 新增 `src/views/UnavailableView.vue`：不存在/未开放入口的通用反馈。

公开首页即使已登录也保持 Public Layout。Gallery 和图片详情按会话选择壳，公开访问规则不变。
认证失败不会被误判为未登录；认证就绪前不挂载含同步登录判断的旧页。非管理员直访管理页显示无权限，不请求管理页面数据。
现有 user store 的 single-flight 和会话代数机制未改动。

### 2. 导航与工作区

- 修改 `src/constants/navigation.js`：新增 Public/App 导航模型；原模型保留用于旧消费者。
- 修改 `src/components/NavBar.vue`：仅负责官网少量入口，不再承载整份功能目录。
- 新增 `src/components/shell/AppNavigation.vue`：空间、图库；工具和管理使用可展开分组。
- 新增 `src/components/shell/AppToolbar.vue`：位置、图库搜索入口、上传、账户。
- 新增 `src/components/shell/AppMain.vue`：text/wide/fluid 与 legacy/managed 接口。
- 新增 `src/components/shell/SiteFooter.vue`：两种布局共用备案信息。

本轮所有既有页面均为 legacy frame，Shell 不额外施加 gutter 或 hard max-width；没有覆盖旧 `.container`。
未来 managed frame 的 text/wide 分别采用 R01 宽度令牌，fluid 不限制硬上限。
旧页面自己的标题、操作、视觉样式、图库宽度限制仍保留，等待对应阶段迁移。

### 3. 响应式、伙伴槽位与弹层兼容

- 新增 `src/components/shell/CompanionPresenceSlot.vue`：静态伙伴入口，无生命数据、动画或 Avatar。
- 新增 `src/components/shell/ShellDialog.vue`：原生 modal dialog + Tab 圈禁、ESC、焦点恢复、背景 inert 和滚动锁。
- 新增 `src/composables/useLegacyOverlayIsolation.js`：观察现有业务弹层的挂载/移除，仅隔离 Shell 控件。

桌面 ≥1024 为 224px 侧栏，768–1023 为 88px 紧凑导航，手机使用空间/图库/伙伴/更多底导。
手机底导留出内容占位及 safe-area，表单编辑时隐藏；没有修改旧页面表单结构。断点使用 480/768/1024 字面量。
伙伴随现有 feature flag 一起显隐，与工具/配方/喂养日志路由保持一致。生产默认关闭，关闭后的深链显示不可用。
Shell 未添加包住旧业务弹层的 transform 或全局 stacking context；Presence 不高于浮层体系。

旧弹层兼容桥识别现有 `.modal-overlay/.share-overlay/.panel-overlay/.fullscreen-overlay`。
只把侧栏、顶栏、底导、Shell 跳转链接和页脚设为 inert，不会禁用包含弹层本体的 main。
这不是旧业务弹层的完整无障碍重写；其内部焦点管理仍由旧页面负责，后续迁移应替换为显式弹层状态接口。

## 验证记录

### 自动检查

| 检查 | 结果 |
| --- | --- |
| `npm test` | 56 / 56 通过 |
| `npm run lint` | 通过 |
| 默认生产构建 | 通过，伙伴 chunk 不存在 |
| `VITE_COMPANION_ENABLED=true` 生产构建 | 通过，伙伴 chunk 存在 |
| `npm run check:bundle` | 通过 |
| `npx playwright test --config playwright.shell.config.js` | Chromium 24 / 24 通过（第一轮交互迭代） |
| 本轮前端 diff whitespace 检查 | 通过 |

新增 `tests/shellAccess.test.mjs` 覆盖门禁、登录回跳、元数据和导航矩阵，先写失败测试再实现。
新增 `e2e/shell.spec.js` 和 `playwright.shell.config.js`，运行开发模式和默认生产包两组检查。
浏览器测试使用显式假数据，只拦截真实 `/api/` 前缀，不启动或依赖当前有外部修改的后端。

覆盖：认证延迟与失败重试、重试确认访客、深链登录、默认落点、不安全 redirect、非管理权限、
Public/App 切换、退出、响应式宽度、焦点圈禁、滚动恢复、当前路由菜单关闭、跨断点焦点回落、
旧业务弹层的 Shell 隔离、开关关闭深链、ModelGateway heading 颜色继承，以及页面 JS 运行异常。

基线已有两项测试维护问题一并校正：

- `tests/responsiveFoundation.test.mjs` 仍按 R01 之前的文件/断点检查，现改为读取真实 base/motion 分层和已有断点。
- `tests/visualFoundation.test.mjs` 补显式 Node process import，消除已有 lint 错误；不改变颜色继承断言。
- `tests/navigation.test.mjs` 将已移除的旧 NavBar 抽屉源码正则检查改成官网导航模型检查；新抽屉由浏览器行为测试覆盖。

首次浏览器测试的两个测试脚本问题已修正：API 通配符误拦截 Vite 源码模块；URL 断言错误地强制斜杠编码。
第一版最终全量 22 项通过，不将早期失败归为应用缺陷；后续交互迭代扩充到 24 项。

### 视觉与独立审查

- 核对 1440 / 768 / 390 截图；自动检查还覆盖 1024 / 480 / 320 的 Shell 宽度与溢出。
- 深色 ModelGateway 标题仍继承所属 Surface 的颜色。
- 独立审查提出的 3 个问题均已修复并添加行为回归：旧弹层 Shell 隔离、跨 768 断点关闭与焦点回落、点击当前页导航关闭。
- 截图位于前端被忽略的 `test-results/`，不加入源码。

## 限制与下一阶段

- 本机验证使用 Node 24.19.0；项目声明 Node 22。未为本轮更换运行时，正式验收应在项目规定的 Node 22 环境复核。
- 本轮是前端 mock 集成检查，未运行真实后端业务 E2E、完整跨浏览器/辅助技术/实体移动设备验收。
- 未宣称旧页面全部完成视觉或无障碍迁移；旧页面现存样式与操作继续保留。
- R03–R16 的后续页面/Avatar/业务重构未启动；没有增加通知、全域搜索或账号设置等不存在的能力。
- 实现停在这里，下一阶段是 R02「验证 / 验收」。

## 交互迭代：导航协调性与伙伴辨识度

根据用户反馈“菜单切换生硬、展开三角与空间/图库不协调、伙伴不显眼”，在 R02 范围内调整：

- 新增 ShellIcon：少量同线宽的内联 SVG，空间/图库/工具/管理及手机底导共用；无图标库依赖。
- 工具/管理改成原生 button disclosure，右侧统一细箭头；采用 240ms 展开、160ms 颜色/透明度过渡。
- 保留 aria-expanded/aria-controls；收起后立即 inert、aria-hidden，不把动画中的链接留在键盘导航里。
- 空间与二级菜单区分选中层级，减少连续两块相同高亮。
- App 页切换加入进入阶段的轻淡入，不延迟离开、不改路由身份、不因 query 变化重新挂载；不对业务页面施加 transform。
- 伙伴入口使用淡色栖居区域、静态叶芽标记和“伙伴空间”文字；平板和手机沿用同一标记。没有 Avatar、虚构生命状态或 Ambient 动画。
- 按 ui-ux-pro-max 的一致性、触达尺寸和无障碍规则核对；减少动态效果模式禁用新增 transition property。

本次复核：56 项单元测试、lint、默认生产 build 通过；浏览器增加 disclosure 对齐、展开/收起、键盘隔离及 reduced-motion 用例，开发/默认生产两组共 24 项通过。桌面、手机及管理分组截图已核对。
原有业务源码、后端改动和暂存区仍未操作；该轮随第一版 Shell 进入 `0192c2264cf3f9256793385d69d440f1e9674da2` 并已推送。

## 第二轮人工 Smoke 反馈修正

本轮从已推送的 `0192c2264cf3f9256793385d69d440f1e9674da2` 继续，只修正 R02 Shell 的信息层级和交互，不迁移业务页面：

- 空间与工具、管理统一为 disclosure；进入空间下任一路由时自动展开当前分组，其他分组收起。手动收起后子项立即 `inert`，保留 `aria-expanded` / `aria-controls`、键盘隔离、展开动效与 reduced-motion 降级。
- 顶栏面包屑改由 Shell 路由元数据生成，不再把所有页面默认挂在“空间”下。空间、图库、工具、管理和伙伴分别使用各自的语义根节点；不猜测 URL，也不请求 API。
- 图库搜索、上传、账户收拢为同一组 44px 工具控件；上传改为浅色强调，保留精确文案“图库搜索”和真实 `/gallery`、`/upload` 跳转。
- 账户入口显示真实用户名首字和名称；账户面板仅展示现有 `userName` / `userAccount` 与退出登录，移除“返回官网”，未虚构设置或通知能力。
- 首页仅在 `HeroSection.vue` 内将“云上智能管理。”调整为更清晰的暖红色 `#c85f49`；未修改全局 danger token 或首页结构。
- ui-ux-pro-max 的触达尺寸、分组一致性和 reduced-motion 规则用于约束本轮细化，没有引入新的视觉方向、组件库、图标库或依赖。

第二轮增加纯函数断言与浏览器行为覆盖：空间 disclosure 的自动展开/手动收起/焦点隔离，各语义分区面包屑，顶栏三项对齐与真实跳转，账户真实字段，以及 1440 / 768 / 390 指定页面截图。最终 `npm test` 57 / 57、lint、默认生产 build、bundle budget 均通过；开发/默认生产两组 Chromium 共 30 / 30 通过。截图仍位于前端被忽略的 `test-results/`，已人工复核指定页面和账户面板。

当前仍保留的边界：业务页继续使用 legacy frame；面包屑只表达已确认的静态 Shell 元数据，不虚构动态实体名称；伙伴保持静态 Presence；真实后端、Node 22、跨浏览器与辅助技术验收仍留给 R02 正式验证阶段。R03–R16 未启动。

### 2026-09-22 配色与入口细化

按后续截图反馈覆盖上述第二轮的三处视觉选择：上传移除常驻蓝色，直接共用图库搜索的中性文字和 hover 样式；顶栏账户入口恢复为纯文字“账户”（手机端同样保留文字），面板内的真实账户信息保持可用；首页强调字从棕红 `#c85f49` 改为更明亮鲜红的 `#f04438`，仍仅作用于 Hero 局部。手机上传图标保留明确的无障碍名称。
