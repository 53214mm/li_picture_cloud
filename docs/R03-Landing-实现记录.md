# R03 — 首页 / Landing Page 实现记录

日期：2026-09-22
阶段：实现 / 迭代（第一版）

## 基线与方向

- 分支：`kimi`。
- 起始提交：`99a6e834db64ad0dc2227396ecf98b5eb866cae2`。
- 唯一实施方向：方案 B「图片与伙伴同处一室」。A / C 仅保留为设计探索对照。
- 复用 R01「雾屿纸境」tokens 与 R02 Public Layout，不重新设计官网导航或 App Shell。
- 原有后端源码、已暂存文档删除及其他本地文件未操作。

## 第一版实现

### Hero 与图片画布

- `HeroSection.vue` 改为短定位、身份感知 CTA 与横向图片画布。
- Hero 不再放搜索输入框和固定热门标签；搜索保留为图库入口。
- 使用仓库已有 nature、travel、city 图片，首图声明尺寸并优先加载，避免布局跳动。
- 栖居位是无眼睛、无身体细节的几何占位，明确标注“概念示意 · 非正式角色”，不构成 R05 Avatar。

### 首页信息架构

- `HomeView.vue` 按“图片与伙伴 → 空间 → 浏览图片 → 实际 CTA”排列。
- 伙伴段只解释现有流程：从私有空间选图、成长记录与候选记忆确认、后续查看与聊天。
- 生产默认关闭伙伴功能时保留概念说明，但不提供不可用跳转；开放态才展示伙伴入口。
- 空间段只说明个人 / 团队归属和现有权限，不伪造真实相册或团队数据。
- 公共图库展示使用本地样片，并明确不是用户数据；没有复制 R04 图库交互。

### 状态与 CTA

- 新增 `getLandingPresentation` 纯函数，集中处理认证状态和伙伴开关。
- 认证未就绪：保持中性的“浏览图库”，不提前显示注册再切换。
- 访客：注册 + 登录；已登录：进入空间 + 图库。
- 伙伴开放时：访客前往带安全本地 redirect 的登录页，成员进入 `/companion`。
- 只读取现有 Pinia store 和 `COMPANION_UI_ENABLED`，未改认证规则、feature flag、API 或业务状态。

## 响应式与可访问性

- 使用 480 / 768 / 1024 既有断点。
- 桌面为标题 / 说明并列，下方大图、两张小图和栖居位组成共享画布。
- 手机为文案 → 主图 → 小图 / 栖居位；正文段落回落为单列，图片浏览为主图加双列。
- 主要操作高度不低于 48px；使用语义链接、连续 heading、alt、固定图片比例和全局 focus-visible。
- 未增加装饰动画；现有 reduced-motion 基础继续生效。

## 新增与修改文件

- 修改 `src/components/HeroSection.vue`：B 方案 Hero 与图片画布。
- 修改 `src/views/HomeView.vue`：正式 Landing 信息架构与响应式样式。
- 新增 `src/utils/landingPresentation.js`：CTA / 可用性映射。
- 新增 `tests/landingPresentation.test.mjs`：四种关键状态映射测试。
- 新增 `e2e/landing.spec.js`：Landing 浏览器 smoke。
- 新增 `playwright.landing.config.js`：开发与默认生产双环境前端测试配置。

## 本轮验证

本阶段只做实现所需的 smoke，不把 R03 标记为最终验收完成。

- `npm test`：61 / 61 通过。
- `npm run lint`：通过。
- `npm run build`：通过。
- `npm run check:bundle`：通过。
- `npx playwright test --config playwright.landing.config.js`：14 / 14 通过。
- 浏览器覆盖开发 / 默认生产、访客 / 成员、伙伴开放 / 关闭，以及 1440×900、768×1024、390×844、320×700、844×390。
- 全部展示图片成功加载；检查视口无横向溢出；主要 CTA 触控高度不少于 44px。
- 已人工查看默认生产的桌面与手机完整截图。

## 边界与下一步

- 未实现正式 Avatar、伙伴状态协议、动画状态机、拖拽喂养或 Gallery 重构。
- 未修改 NavBar、PublicLayout、App Shell、router、Pinia store、API、数据库、后端或依赖。
- 本地样片沿用仓库已有文件；正式发布前仍需核对素材授权或换成项目自有素材。
- 当前是 R03 实现 / 迭代第一版，等待用户查看与反馈；不自行进入最终验证 / 验收，也不启动 R04。
