# R02 — 登录后 App Shell · 需求确认报告

- 阶段：需求确认 / 设计（三阶段工作流第 1 阶段）
- 日期：2026-09-21
- 状态：**用户已确认方案 A「窄侧栏 + 顶部工作栏」；设计方向已收口，正式实现尚未开始**
- 只读基线：`kimi@e3a7d5f863075634688da8b1b727ab6eb941484a`
- 视觉依据：[R01 需求确认报告](R01-新视觉设计体系-需求确认报告.md) 第 5.2、6、7.2 节，以及当前正式 R01 CSS
- 原型：[比较入口](design-exploration/r02-shell/index.html) · [A](design-exploration/r02-shell/scheme-a.html) · [B](design-exploration/r02-shell/scheme-b.html) · [C](design-exploration/r02-shell/scheme-c.html)
- 方向确认记录：用户明确选择“那就方案A”。方案 A 作为后续 R02 实现的结构基线，B/C 留作设计对照。
- 以下“建议”“拟议”仍表示具体接口与实施建议，不表示已修改正式应用；本次选择不扩大 R02 的阶段和业务范围。

## 1. 当前 Shell 实现

### 1.1 基线与工作区

本轮已执行 `git status --short --branch`、`git rev-parse HEAD`、`git log --oneline -8`。当前分支为 kimi，开始时与 origin/kimi 同步。

R01 提交均存在：

| 提交 | 事实 |
| --- | --- |
| b7daf4d | R01 方向与约束收口 |
| 18232d8 | tokens/base/motion 与四个 P0 primitives 落地 |
| e3a7d5f | 移除全局 heading 强制文字颜色 |

工作区原有：round-15 至 round-22 及“未决问题”的文档删除/迁移、部分已暂存的文档删除、`docs/codex-review-learning/` 未跟踪目录，以及多处 Companion 后端修改（含 CompanionLife.java）。本轮绕开全部既存改动，不删除、不覆盖、不 reset、不 stash、不 stage、不提交它们。

### 1.2 结构与认证

依据：`src/App.vue`、`src/components/NavBar.vue`、`src/router/index.js`、`src/stores/user.js`、`src/utils/authBootstrap.js`（本报告源码路径均相对 `li-picture-cloud-frontend/`）。

- App.vue 固定挂载 NavBar → main/RouterView/page transition → 备案 Footer；未区分 Public/App Layout。
- NavBar 使用 sticky，桌面把 navigationGroups 的 items 用 flatMap 展平；移动端才显示组标题。
- user store 提供 currentUser、authReady、authBootstrapError、isLoggedIn、isAdmin。App 与需要登录的路由共享 ensureCurrentUser 的单飞加载。
- authReady=false 既可能是尚未完成初始化，也可能是网络/5xx 失败；不能直接等同访客。401/403 按现有工具语义终结为访客。
- 现有守卫仅处理 requiresAuth；网络异常时放行，注释预期由页面显示可重试状态。并非每个旧页面都实现了这套状态。
- 部分旧页面在 setup 里直接读 isLoggedIn/isAdmin 并跳登录，例如 MySpace、Upload、SpaceCreate、PictureManage、AdminUser；刷新后的认证时序需要纳入后续 Shell 接入设计。
- 登录成功默认跳到 `/`，有 query.redirect 时优先使用它。当前首页始终是品牌 Landing。
- 移动导航已有打开后聚焦关闭按钮、ESC、关闭后恢复焦点、body 滚动锁和断点切换关闭；**没有完整 Tab/Shift+Tab 焦点圈禁，也没有对背景 inert 的实现**。不得把现有行为当成已完整满足模态可访问性。

### 1.3 R01 修复状态

当前 `src/styles/base.css` 的 `:where(h1,h2,h3)` 只设置字体、字重、text-wrap，已无 color。默认正文色由 body 提供，深色/语义 Surface 下的 heading 保留继承。

`src/styles/tokens.css`、base.css、motion.css 及 LpButton/LpInput/LpSurface/LpStateBlock 均存在。R02 无需顺手修改 R01。未来 Shell 的文字默认值应落在容器，而非所有 heading 上；不增加跨页面强制标题色。

## 2. 当前导航盘点

### 2.1 统计口径与实际数量

直接运行 `buildNavigationGroups`，枚举角色与 companionEnabled=true/false。入口数包含“退出登录”动作，不含 Logo 的重复首页链接、不含纯展示用户名；括号内是可跳转链接数。

| 构建期开关 | 访客 | 普通登录用户 | 管理员 |
| --- | ---: | ---: | ---: |
| 关闭 | 4（4） | 7（6） | 9（8） |
| 开启 | 4（4） | 10（9） | 13（12） |

若把 Logo 也计作一个可点击导航控件，以上各数再加 1。桌面不是固定 13 项：取决于角色和实际构建配置。

`src/config/features.js` 定义为 `import.meta.env.DEV || VITE_COMPANION_ENABLED === 'true'`。开发环境开启；生产默认关闭、显式配置才开启。未读取真实环境密钥，也未推断部署站点实际开关。此开关同时控制伙伴、模型连接、配方和喂养日志路由，不可擅自拆开或让被移除的入口残留。

### 2.2 全部当前导航项

| 当前分组 | 当前名称 | 路径/动作 | 可见条件 | 建议归属 |
| --- | --- | --- | --- | --- |
| 浏览 | 首页 | / | 全部 | Public 官网，不占 App 一级 |
| 浏览 | 探索图库 | /gallery | 全部 | 图库（公共图片浏览） |
| 工作空间 | 上传图片 | /upload | 已登录 | 顶栏动作，不是导航域 |
| 工作空间 | 我的空间 | /space/my | 已登录 | 空间主入口 |
| 工作空间 | 我的伙伴 | /companion | 已登录 + flag | 独立 Presence 位及伙伴入口 |
| 工作空间 | 模型控制中心 | /model-gateway | 已登录 + flag | 工具 → 模型连接 |
| 工作空间 | 配方工坊 | /recipes | 已登录 + flag | 工具 → 配方 |
| 工作空间 | 空间管理 | /spaces | 已登录 | 空间二级，不改成管理员专属 |
| 工作空间 | 图库分析 | /space/analyze | 已登录 | 空间 → 分析 |
| 管理 | 图片审核 | /admin/pictures | 管理员 | 独立管理区 → 图片管理 |
| 管理 | 用户管理 | /admin/users | 管理员 | 独立管理区 → 用户 |
| 管理 | 喂养日志 | /admin/companion-feed-runs | 管理员 + flag | 独立管理区 → 喂养日志 |
| 账户 | 登录、注册 | /login、/register | 访客 | Public 导航 |
| 账户 | 退出登录 | logout | 已登录 | 用户菜单 |

完整可用集合含 14 个不同导航链接与 1 个动作；互斥条件导致不会同时全部出现。旧“图片审核”页实为图片管理，含状态筛选、抓取等管理操作，因此建议入口称“图片管理”，不改变其功能。

### 2.3 当前路由与建议 Layout / 工作区映射

当前 13 个基础路由、5 个 flag 路由，共 18 个路由记录；这与导航项数量不同。仅 5 个 flag 路由声明 requiresAuth，当前没有统一 layout/workspace/navKey/breadcrumb meta。

| 路由 | 当前认证/权限事实 | 建议 Layout | 目标 workspace / 导航归属 |
| --- | --- | --- | --- |
| / | 无路由认证限制，HomeView 是 Landing | public（即使已登录也可访问官网） | 官网自有宽度 |
| /login、/register | 公开认证表单 | public | text，保留表单更窄的内层 |
| /gallery | 公开路由 | adaptive | fluid / 图库 |
| /picture/:id | 公开路由；具体图片访问和操作权限仍由现有请求/页面判定 | adaptive | fluid / 图库默认归属 |
| /upload | 无 requiresAuth；页面自行登录判断 | app | text / 上传动作 |
| /spaces | 无 requiresAuth；按 currentUser.id 查询自己创建的空间，管理员有扩展编辑项 | app | wide / 空间 |
| /space/create | 页面自行登录判断 | app | text / 空间 |
| /space/my | 页面自行登录判断，汇总私有、创建的团队、加入的团队 | app | wide / 空间（建议默认落点） |
| /space/:id | 加载空间及现有成员/图片权限 | app | fluid / 空间 |
| /space/analyze | 无 requiresAuth；页面区分 scope，管理员可见全部空间/排行 | app | wide / 空间 |
| /admin/pictures、/admin/users | 页面自行 isAdmin 检查 | app + admin | wide / 管理 |
| /companion | flag + requiresAuth；页面处理认证/不可用状态 | app | text / 伙伴 |
| /model-gateway | flag + requiresAuth；MCP 管理区域再按 isAdmin | app | wide / 工具 |
| /recipes | flag + requiresAuth；模板能力取现有服务端可用性 | app | text / 工具 |
| /admin/companion-feed-runs | flag + requiresAuth，页面等待认证后检查 admin | app + admin | wide / 管理 |
| /admin/companion-feed-runs/:runId | 同上 | app + admin | wide / 管理 |

adaptive：已确认登录用 App Layout，已确认访客用 Public Layout。不是因为页面换壳就将原本公开的图库改成私有；私有图片直链也不因此变成公开可见。后端权限保持权威。

`/space/analyze` 是静态路径，Vue Router 会优先于 `/space/:id` 匹配。导航归属需显式 navKey，不能靠粗略 `startsWith('/space')` 推断所有状态。

## 3. 当前问题

1. 桌面丢失分组，浏览、上传动作、空间、凭据配置、日志与退出混在同一级；功能增加等于不断加链接。
2. 首页仍是 Landing；登录后回到营销页面，没有自己的稳定工作入口。
3. 我的空间与空间管理含义相近、覆盖集合不同：前者含加入的团队，后者偏自己创建空间的容量/编辑。平级摆放增加决策负担。
4. 模型凭据、MCP、配方配置是支撑能力，却与图片工作争夺注意力；“低频”是本轮基于任务角色的设计判断，尚无行为埋点证明。
5. 管理员多出三个平台治理入口，使管理员视角的产品更像后台目录。
6. 伙伴当前只是普通链接，无固定栖居结构；不能通过增加右下角悬浮球弥补。
7. 每页都拥有页头、padding 和容器上限，新 Shell 若再无条件套一层会导致双倍 gutter、重复标题和双滚动。
8. 认证状态与页面级跳转不一致，单靠 `isLoggedIn ? App : Public` 会产生闪烁、误导性访客界面或误跳转。

所谓“后台功能目录”，具体就是以实现模块命名并平铺，而不是先让人明确“我的图片和协作空间在哪里”。导航应按用户去处组织，动作留在动作位置。

## 4. Public Layout / App Layout 边界

### 4.1 Public

- `/` 保留现有 Landing，R03 再做内容和视觉改版。
- Login/Register 保留原业务表单与认证 API。
- 访客访问图库、图片详情，继续使用 Public Layout。
- Public 导航建议只含品牌首页、图库、登录/注册；已登录访问官网时提供“进入空间”和用户入口。
- 备案链接保留在 Public Footer；App 中放低权重页脚/关于位置，不能因工作台化消失。

### 4.2 App

- 以任务导航、轻量顶栏、工作区、Presence slot 组成稳定结构；子页面切换不销毁整个 Shell。
- 默认登录目标建议 `/space/my`。保留受支持、经过本地路径校验的 redirect 优先级；不得将网络错误当作登录失败，也不增加后端登录能力。
- App 品牌入口回 `/space/my`；官网放用户菜单/关于区域。无需新建 dashboard、最近访问数据接口或自动创建私有空间。
- 新用户无空间时复用当前 MySpace 空状态与创建入口。原型图片工作区代表进入某个空间后的状态，**不承诺 /space/my 首屏已经是图片墙**。
- 用户菜单仅放现有账户身份、返回官网、退出；不存在通用账号设置路由，先不画成可用功能。管理入口仅管理员可见，管理子项不提升至主导航。

### 4.3 认证状态边界（建议的实现契约）

| 状态 | Shell 行为 |
| --- | --- |
| 初始化中，authReady=false 且无错误 | App 专属路由先显示安静的认证加载态，不挂载有同步登录判断的旧页 |
| 已确认访客 | Public；App 专属目标转登录并保留安全本地 redirect |
| 已登录普通用户 | App 导航按角色与 flag 生成 |
| 已登录管理员 | 同一 App 骨架，附低权重管理入口 |
| 网络/5xx，authReady=false 且有错误 | App 目标显示“暂时无法确认登录状态 / 重试”；公共内容可保持 Public，不静默跳登录 |
| 已登录但非 admin 访问管理直链 | 显示无访问权限及返回空间入口，不伪装成未登录 |
| flag 关闭 | 同时移除相应路由、入口、Presence；旧深链显示通用不可用/未找到，不触发创建伙伴 |

实现时需协调路由守卫与渲染 gate：旧 requiresAuth=false 的工作区也应在认证完成后挂载。网络异常可维持现有守卫放行语义，但 App 渲染 gate 不得继续挂载依赖未就绪用户数据的页面。后台权限不由隐藏入口替代；本轮没有安全授权实现变更。

## 5. 用户核心任务与导航优先级

| 优先级 | 用户要做什么 | 位置 |
| --- | --- | --- |
| 高频核心（设计假设） | 找到自己的图片所在空间、进入团队工作区 | 空间 → /space/my → 具体空间 |
| 高频核心（设计假设） | 浏览公开图片 | 图库 → /gallery |
| 高频动作 | 上传图片 | 顶栏“上传”；空间内上传继续按已有权限与上下文 |
| 稳定陪伴入口 | 看伙伴 | 独立 Presence/伙伴导航位 → /companion |
| 空间二级 | 查看容量、管理自己创建的空间、分析 | 空间 → 空间管理 / 分析 |
| 支撑工具 | 配方、模型连接 | 工具分组；flag 关闭时整组隐藏 |
| 低频平台管理 | 图片管理、用户、喂养日志 | 独立“管理”入口，仅 admin |
| 账户动作 | 退出、返回官网 | 用户菜单 |

“创作”目前没有独立聚合路由。AI 面板与伙伴创作模块仍归现有页面；不把 /recipes 冒充完整创作工作台。本轮优先使用诚实的“工具 / 配方”命名，将“创作”一级入口留待真实功能承接。

### 顶部工作栏的取舍

- 长期存在：当前位置（简短 breadcrumb 或 route title）、上传、用户入口。
- 搜索：不新增全域搜索框。桌面可放“图库搜索”跳转到既有 /gallery，让用户在原页面输入；手机收入“更多”。本轮原型展示的是入口位置。
- 已有 Gallery 的 q 参数只在初始化读入，没有 watcher 负责同路由 query 更新；不能贸然添加 Shell 搜索框然后假定 `router.push('/gallery?q=...')` 在所有情况下都工作。
- 当前空间的筛选、批量、成员、审核、保存配方、API Key 提交全部留在原页面；Shell 无权代理这些业务动作。
- 顶栏上传默认进入现有 /upload，保持原有公共图库/空间选择语义。SpaceDetail 的局部上传继续使用 canUploadPictures。上下文上传若未来需要传 spaceId，应以现有权限为前提，不悄悄改变全局上传目标。
- 不添加通知铃、未读数或主动提案入口，没有现成 Shell 级聚合事实。伙伴不以红点和催促占据顶栏。
- 页内保留原 H1；Shell breadcrumb 是 nav/普通文本，避免两个同等大小的页标题。

## 6. 三套 Shell 方案

> A/B/C 是 R02 结构选项编号，与 R01 的配色方向编号无对应关系。三套全部使用 R01“雾屿纸境”。

| 维度 | A：窄侧栏 + 顶部工作栏 | B：资源 Dock + 内容画布 | C：顶部分区 + 左侧上下文 |
| --- | --- | --- | --- |
| Desktop | 224px 常驻文字侧栏；其余为顶栏与工作区 | 88px 资源 Dock；顶栏选择当前空间；横向上下文条 | 顶部空间/图库/工具三分区；当前域在左侧显示二级 |
| Tablet | 收到 88px 图形+文字导航；二级进入菜单 | 保留 Dock，空间选择器更紧凑 | 保留三分区；上下文侧栏改为分区菜单 |
| Mobile | 空间/图库/伙伴/更多底导；顶栏菜单可访问二级 | Dock 转底导；空间选择器保留在顶部 | 顶部三分区保留，二级用菜单；底部独立 Presence 行 |
| 一级导航 | 日常空间、图库；独立伙伴位；工具降权分组 | 空间、图库、工具三个资源入口 + 独立伙伴位 | 空间、图库、工具三个任务域 |
| 二级方式 | 空间下展开我的空间/空间管理/分析；工具默认折叠 | 空间选择器与横向上下文；工具进入面板选择 | 随顶部所选域更换侧栏，图库域不强留空侧栏 |
| 搜索 | 顶栏“图库搜索”入口；手机更多 | 顶栏入口，与画布局部搜索分开 | 工作栏入口；顶部主导航不塞搜索框 |
| 上传 | 顶栏单一动作，局部上传仍属页面 | 工作栏右侧；空间操作在横向上下文 | 工作栏右侧，不塞入主导航 |
| 用户 | 顶栏右侧用户菜单 | 顶栏右侧 | 全局顶部右侧 |
| Presence | 侧栏底部固定栖居行，不覆盖工作区 | Dock 独立生命位，保留“伙伴”文字 | 顶部靠用户区但保持独立空间，不作为消息按钮 |
| 宽度 | 工作区按 text/wide/fluid 分配 | 画布 fluid，工具页套自身 text/wide | 上下文栏存在时扣除栏宽，再按 workspace 分配 |
| 低频入口 | 工具 → 模型连接/配方；侧栏低权重管理 | 工具打开二级；管理位于 Dock 末端/账户菜单 | 工具域内模型连接/配方；管理独立入口打开管理上下文 |
| 优点 | 路径稳定、中文可读、迁移成本可控、伙伴有可辨识位置 | 留给图片的宽度最多，适合长期在同一空间工作 | 一次只看一个任务域，管理/工具扩展空间清晰 |
| 风险 | 若不限制展开项会退化成长目录；消耗 224px 宽度 | 选择器与工具面板增加点击；新用户难发现能力 | 顶部+上下文两套导航占空间；换域有位置记忆成本 |

A 的二级展开并不意味着将当前 13 个入口全部复制到侧栏：移除官网首页，上传提升为动作，退出进入用户菜单，空间相关三项收拢，模型/配方降低为工具子项，管理仅显示一个低权重入口。

B 也不意味着做自由拖拽画布：只是 Shell 把更多宽度让给图片。C 不让“模型/配方/日志”回到顶部主导航。

## 7. Companion Presence 的位置与边界

R02 只设计 T2。T1 仍为 /companion，T3 仍由未来具体场景承接。

已选方案 A 的 Presence 结构：

- Desktop：侧栏下端、关于区域之后有一行稳定栖居位。约 56–72px 高，静态占位 + “伙伴”；侧栏过高时允许导航区滚动，Presence 不挤占工作区。
- Tablet：88px 导航栏中的独立槽位，保留“伙伴”文字，不用仅有图标的客服按钮。
- Mobile：四项底导里的伙伴槽位；不凸出成 FAB，不遮挡上传、图片或页面底部操作。开关关闭时移除此槽并重排为三项。
- 正式实现的 slot 可先为空或显示无业务状态的静态 placeholder；不请求伙伴状态、不自动唤醒、不添加饥饿/喜爱/图片取色情绪等领域事实。
- Slot 的尺寸与容器由 Shell 负责；未来身体/眼睛/姿态资产由 R05 负责，状态协议/动画分别属于 R06/R07。
- 导航到 /companion 可以保留普通链接语义；复杂拖拽、聊天、popover 交互留给 R08。
- 使用 R01 z 标尺：150 < drawer 200 < overlay 300 < modal 350 < popover 400。z-index 只有在对应 stacking context 内才可比较；浮层应在一致的 overlay root/Teleport 宿主中管理。
- 模态开启时 Shell 背景 inert，Presence 同样不可交互，并可隐藏；仅降低透明度不能代替焦点隔离。
- 避免对整个 App 根添加 transform/filter/isolation 等不必要的 stacking context，防止旧弹层被困住。
- 为避免 scrim 层级 300 盖住 drawer 200：导航 drawer 的遮罩和面板放在同一个 z=200 宿主内部，局部排序遮罩在下；z=300 用于更高层模态遮罩，不直接作为该 drawer 的同级盖板。

## 8. Desktop / Tablet / Mobile 骨架

已选方案 A 的区间无重叠：Desktop ≥1024px，Tablet 768–1023px，Mobile 480–767px，Compact <480px。CSS 使用 480/768/1024 字面量；原型使用 range media query，不使用 var() 断点。

| 宽度 | 导航与顶栏 | 内容与 Presence |
| --- | --- | --- |
| ≥1024 | 224px 侧栏；breadcrumb、图库搜索入口、上传、用户 | 按 workspace 宽度；Presence 侧栏固定槽 |
| 768–1023 | 88px 导航，短标签；二级通过菜单；搜索可移入菜单 | 主内容 min-width:0；Presence 缩小且保留文字 |
| 480–767 | 左栏隐藏；顶栏菜单/位置/上传/用户；底部空间/图库/伙伴/更多 | 两侧 gutter；底导与页面操作分别预留高度 |
| <480 | 同手机骨架，breadcrumb 收短，搜索在更多 | 44px 交互目标、safe-area；Presence 不外凸 |

滚动建议：首轮保留文档为主要滚动容器，以兼容已有 window.scrollTo 和图片页面。顶栏与底导 sticky 前需明确父容器；底导预留实际高度，不通过无 padding 的 fixed 覆盖工作区。侧栏过长允许自身导航列表滚动，不再为每个页面制造一个滚动容器。

Drawer 后续必须具备 ESC、Tab/Shift+Tab 圈禁、初始焦点、焦点还原、滚动锁、背景 inert 和断点切换关闭。优先实现一个可靠导航 Drawer，不在 R02 同时创建通用浮层系统。软键盘出现时底导不遮挡输入，R02 骨架需保证基本可用；完整页面触控/性能/Avatar 表现降级仍属于 R13。

原型只表达上述折叠结构，没有真正的 Drawer 或底导 sticky 行为，也不构成已通过移动端业务验收。

原型静态预览检查：index 与 A/B/C 在 390、480、768、1024、1440px 共 20 个组合中无水平溢出；本地 R01 令牌引用生效，无外部网络请求与浏览器脚本错误。已目检三套桌面和 A 手机截图。该检查仅验证隔离原型的可打开性与结构，不替代正式应用验证。

## 9. 工作区宽度策略与接口

### 9.1 建议职责

- App.vue：布局分发与路由出口，业务数据不搬进根。
- PublicLayout：公开导航、公开内容、Footer。
- AppLayout/AppShell：稳定导航、工作栏、AppMain、Presence slot。
- AppMain：工作区尺寸/标题上下文/兼容 frame，接受 route metadata。
- 页面：数据、H1、业务操作、状态、图片布局。
- 不必为了命名再创建一层空 WorkspaceLayout；先让 AppMain 承担唯一宽度边界。

建议元数据（仅接口提案）：

```js
meta: {
  layout: 'app',        // public | app | adaptive
  requiresAuth: true,
  requiresAdmin: false,
  navKey: 'spaces',      // gallery | spaces | companion | tools | admin
  title: '空间管理',
  workspace: 'wide',    // text | wide | fluid
  frame: 'legacy'       // legacy | managed
}
```

静态 breadcrumb 由路由配置建立；动态空间名称/图片名称只复用页面已加载信息，未就绪时显示“空间详情 / 图片详情”。Shell 不为显示面包屑再请求一次空间/图片 API。直接打开图片详情时默认归图库；从空间进入的返回语义继续由现有页面决定，不在 R02 发明资源所属权限规则。

### 9.2 宽度所有权与渐进接入

| 策略 | 目标 | 内层边界 |
| --- | --- | --- |
| text | ≤ --lp-content-text（1200px） | 短表单仍可保持 560/680px 的已有上限 |
| wide | ≤ --lp-content-wide（1440px） | 表格/分析/空间列表 |
| fluid | 占据扣除导航后的全部可用宽度 | 不设置统一 hard max-width；gutter 仍存在 |

推荐在实现阶段以 frame=legacy 接入所有旧业务页：AppMain 不额外加 max-width 和水平 padding，页面已有 .container、自定义 1400px/64rem 等照常负责。managed 是新 Shell 规范入口，真正迁移的页才让 AppMain 接管宽度并显式移除该页重复容器。

这样 Shell 从一开始支持三种模式，但**不声称旧 Gallery 已解除 1440px 上限**。Gallery 的流式切换属于 R04，Companion 页面布局属于 R09。禁止 `.app-shell .container { max-width:none }` 这类跨所有旧页的强行覆盖；也禁止将所有页面套进 1200px。

R02 验收应验证 managed 示例下三种宽度都可用、legacy 页面无新增双 gutter；真实图库流式布局验收归 R04。

## 10. R16 文案规范如何用于 Shell

| 旧名称/候选表述 | Shell 建议 | 理由 |
| --- | --- | --- |
| 探索图库 | 图库 | 简短明确；说明上下文为公共图库 |
| 我的空间（一级） | 空间 | 二级仍可叫我的空间，区分空间管理 |
| 我的伙伴 | 伙伴 | 独立常驻位置已表达归属 |
| 上传图片 | 上传 | 图片产品内无需重复宾语 |
| 模型控制中心 | 模型连接 | 与实际用户凭据/连接/路由任务贴近 |
| 配方工坊 | 配方 | 不伪装成更大范围创作中心 |
| 图片审核 | 图片管理 | 与现有页面实际功能一致 |
| 你的模型连接，由你决定 | Shell breadcrumb：模型连接 | 页面原 Hero 文案本轮不改 |
| 进入属于你的视觉世界 | 不新增 | 无额外信息 |

导航和工作栏用名词或明确动词，不加品牌口号、英文字母 kicker、任务协议、MCP 技术说明。技术设置页面可保留必要技术术语，但不把它们铺到 Shell。伙伴可拟人化，R02 placeholder 只写“伙伴”，不虚构它的状态。

本轮仅约束 Shell 新文案，不批量清理 Landing 或业务页面文案。

## 11. 已确认方案与登录后第一眼

**用户已明确选择 A：窄侧栏 + 顶部工作栏。**

确认的结构包含：桌面常驻文字侧栏、平板紧凑导航、手机底导骨架；日常空间/图库入口、降权工具/管理入口；Shell 内嵌的 Companion Presence 栖居位。R01 视觉语言及本报告的阶段边界继续有效。

登录后第一眼应当是：**“我的图片空间在这里，我知道去哪里继续整理图片或进入团队工作区，伙伴将长期住在这个空间的一角。”**

以既有 /space/my 为默认落点，用户先找到自己的个人/团队空间；进入具体空间后，图片是主体，Shell 退为稳定坐标。公开图库是另一个明确去处，配置与管理放在次级路径。这比新增一个充满数字卡片的 dashboard 更符合当前已有能力，也无需新增数据接口。

A 的取舍：保留中文文字导航，接受 224px 宽度成本来换取明确的信息层级。平板收窄，图库在剩余宽度内流式发展。未来可以吸收 B 的空间选择器，但此时不承诺新选择器数据与状态模型。

原型里“窗边的日常”和六张构图只用于评估图片与导航的面积关系；不是登录落点的实现，不表示真实账户已有这些图片，也不是 R04 设计交付。

## 12. 后续实现文件范围（基于已选方案 A）

| 功能单元 | 预计文件 | 边界 |
| --- | --- | --- |
| Public/App 分发与认证就绪 gate | src/App.vue；新 src/layouts/PublicLayout.vue、AppLayout.vue | 保留 authBootstrap 会话语义，不改登录 API |
| 稳定导航/工作栏 | src/components/NavBar.vue；新 shell/AppNavigation、AppToolbar | 公共导航可复用 NavBar；不删除旧组件直到迁移完成 |
| 信息架构配置 | src/constants/navigation.js；src/router/index.js | 增加展示 meta 与对应守卫/渲染策略；保留 URL、name、flag 和权限 |
| 宽度与兼容接入 | 新 shell/AppMain.vue，必要时 styles/shell.css | 独立 Shell 样式；legacy frame 不覆盖旧页容器 |
| Presence 槽 | 新 shell/CompanionPresenceSlot.vue | 静态结构、feature flag；无伙伴请求/动画 |
| 移动导航 | Shell 专用 drawer（必要时复用后再抽为 LpDrawer） | 焦点与遮罩完整验证后才称通用组件 |
| 登录落点 | LoginView.vue 的成功跳转一处 | 安全本地 redirect 优先，否则 /space/my；不改表单/认证后端 |
| 验证 | 导航配置/路由 meta 测试及 Shell 定向浏览器检查 | 下一阶段写实施计划时再决定，不在本轮改 tests |

不要求以上每个名字都成为文件；相邻的小职责可以合并。最小功能单元顺序：布局与认证边界 → 导航/工作栏/宽度接入 → 响应式导航与 Presence 槽。每一单元可独立验证，禁止先把所有旧页面重写。

## 13. 明确不修改范围

本轮不修改任何 src、tests、e2e、依赖、构建配置或后端。仅新增本报告与 docs/design-exploration/r02-shell/ 隔离原型。

后续 R02 实现也不启动 R03 Landing、R04 Gallery、R05 Avatar、R06 状态协议、R07 动画、R08 全局伙伴交互、R09 Room、R10 Feeding、R11 可视化、R12 Chat/Proposal、R13 全站响应式、R15 登录后端、R16 全站文案清理。API contract、数据库、Pinia 业务状态、图片/空间权限、领域字段和依赖版本不因 Shell 设计改变。

## 14. 风险

| 风险 | 证据/原因 | 设计控制 |
| --- | --- | --- |
| 认证未就绪误跳登录 | 多个旧页 setup 直接读用户状态 | App 路由挂载前就绪 gate；网络错误可重试 |
| 导航隐藏误当权限控制 | 当前 requiresAuth 不覆盖所有工作区/管理页 | 展示、路由限制、后端授权分别验证 |
| 开关关闭仍有死入口 | 单 flag 同时控制伙伴/模型/配方/日志 | 路由、导航、Presence 共用同一 flag |
| 双 gutter/上限 | 大量旧 .container 与自定义宽度 | legacy frame 保留原所有权；迁移页才 managed |
| 旧 Modal 被新 Shell 遮挡 | 旧页有局部 z=200 等值 | 避免根 stacking context；模态宿主与层级定向验收 |
| 新标题色再破坏深色容器 | R01 已发生 | Shell 不使用全局 heading color；按 Surface 继承 |
| 管理员再次看到长目录 | 角色入口增加 | 管理仅一个低权重分组入口 |
| 伙伴变成客服按钮 | 位于角落、红点、聊天首动作 | 内嵌栖居槽、有文字，无新状态/无弹窗聊天 |
| 宣称不存在的搜索/创作能力 | 当前只有 Gallery 局部搜索和分散创作 | 图库搜索是导航入口；创作一级入口暂不设 |
| 移动骨架挤压空间 | 侧栏、底导、虚拟键盘同时出现 | 宽度分段、底导占位、44px、safe-area 基础验证 |
| 改变公共图库的权限印象 | adaptive layout 容易被误认为“我的图库” | 标明公共浏览，保持原 URL 与访问规则 |
| 起始工作区不干净 | 后端和文档存在外部改动 | 所有产物限定 docs；不暂存其他内容 |

## 15. 验收标准

### 本轮设计验收

1. 已记录真实分支/HEAD、R01 三个关键提交与既有 dirty 状态。
2. 入口数量可由 buildNavigationGroups 复现，区分角色、flag、链接/动作/Logo。
3. 18 条路由被覆盖；公开、App、管理员、feature-gated 与详情入口没有遗漏。
4. Public/App 边界包含认证加载、网络错误、访客、普通用户、管理员与 flag 关闭。
5. 三套结构差异在导航层级和布局，而非配色；每套覆盖 13 个要求维度。
6. 原始推荐与用户决定分开记录；当前已有用户明确选择方案 A 的确认记录。
7. Presence 的桌面/平板/手机槽位、宽度所有权、叠层和模态语义已写明。
8. 原型静态、无 API、无真实数据、无新增依赖或字体，直接本地打开。
9. 正式源码/配置/业务未修改；R01 兼容性修复只核实、不改动。

### 后续实现 / 验证阶段的标准（本轮未执行这些业务验收）

- 新登录无 redirect 进入 /space/my；深链登录返回既有目标；不新增自动创建空间/伙伴副作用。
- 认证未就绪时旧页不误跳；网络/5xx 不伪装成访客；非 admin 直链管理页显示合适反馈。
- 开关开/关、普通/admin/访客导航矩阵与配置一致。
- 空间、图库、上传、用户的主要路径清晰；空间详情/图片详情的父级导航高亮准确。
- text/wide/fluid 容器在 1920px 下可辨识；legacy 页面无双 gutter，图库旧上限只记录待 R04。
- 320/390/480/768/1024/1440px 与边界前后不发生 Shell 水平溢出；业务表格原有滚动独立评估。
- Drawer 焦点圈禁/ESC/还原/滚动锁/inert；退出与断点切换关闭行为可靠。
- Presence 恒低于关键浮层，模态时不可被 Tab 访问，不覆盖图片和页面主动作。
- ModelGateway、Companion hero、Home CTA、Gallery/PictureList 图片标题保持正确颜色继承。
- 保留页面原操作与公共访问；布局不新发业务 API 请求；备案信息仍可访问。
- 构建及相关导航测试、页面 smoke 通过，完整 R02 验收按第 3 阶段执行。

## 16. 方向确认与其余建议默认值

| 设计项 | 当前结论 | 为什么影响实现 |
| --- | --- | --- |
| Shell 主结构 | 已确认 A 窄侧栏 + 工作栏 | 决定导航布局与移动折叠骨架 |
| 登录默认目的地 | /space/my | 决定登录返回策略；复用已有个人/团队聚合 |
| “创作”是否现在上一级 | 暂不；工具内保留配方 | 当前没有完整创作主页，避免虚假承诺 |
| 手机骨架 | 沿用已选 A 的空间/图库/伙伴/更多底导 | 伙伴获得 Shell 内固定位置；flag 关时三项 |

结构方向已经确认。登录默认目的地与工具命名继续保留本报告的建议默认值，作为下一阶段实施计划的输入，不将其另行表述为用户逐项确认，也不重新发散 Shell 方向。账号设置、全域搜索、通知聚合、Avatar 造型、暗房模式不是本轮待实现项。

尚无用户行为频次数据；“高频/低频”是待实际使用验证的设计假设，不写成量化产品事实。后端部署开关和生产实际启用状态未核验，不影响本轮对现有前端入口条件的设计。

## 17. 本阶段结论与原型说明

R02 需求确认 / 设计的交付已形成：真实导航盘点、Public/App 边界、三套 Shell、已确认的方案 A、可验证的后续接口与范围。**用户已选择方案 A，设计方向到此收口；R02 实现 / 迭代未开始。**

原型目录 `docs/design-exploration/r02-shell/`：

- index.html：结构比较与说明。
- scheme-a.html：窄侧栏 + 工作栏。
- scheme-b.html：资源 Dock + 内容画布。
- scheme-c.html：顶部分区 + 上下文导航。
- prototype.css：仅供原型使用的布局样式，引用仓库现有 tokens.css；不导入旧 style.css，也不覆盖正式样式。

打开方式：保留仓库相对目录，双击 index.html；点击 A/B/C 查看，再调整浏览器宽度。无需 Vite、npm 安装或服务端。只复制 HTML 到别处会缺少 CSS/令牌，故不要脱离仓库单文件分发。

图中所有业务控件只表达位置，只有原型切换链接有效。绘制的是中性几何图片占位，不加载远程媒体，不表现正式 Avatar。移动端是结构示意而非功能验收。

本轮停止于设计确认。下一阶段为 R02「实现 / 迭代」，以已确认的方案 A 为基线，不重新比较方向。
