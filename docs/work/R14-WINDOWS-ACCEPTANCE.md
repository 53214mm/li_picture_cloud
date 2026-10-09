# R14 · Windows 全量浏览器与真实 API 验收

日期：2026-10-09（Asia/Tokyo）。基线：`nexus/mainline-r11` / `f3b3ff5` 加本轮工作区改动。Docker 恢复后继续此前暂停的验收；MCP 实现按用户要求暂缓。

后续同日用户报告的图库二次进入 Invalid Date 已另行修复，见[日期修复记录](R14-INVALID-DATE-FIX.md)。本文计数保留原验收时点，不视为后续改动的全量重跑；后端 8124 已由用户重新启动并在日期修复时可读。

## 结论与计数

仓库现有浏览器目录、真实后端故事线，以及新增图库分析与 R12 共享聊天联调已执行。首跑发现的失败已修复复验；这是完整执行加定向复验的矩阵，不是“一次全绿运行”。逐项来源见证据中的 `acceptance-matrix.json`。

| 检查 | 最终证据 |
|---|---|
| 前端 Node 22.23.3 | 329/329，0 跳过 |
| 后端 clean verify | 744 项：743 通过、0 失败/错误、1 项真实模型测试按开关跳过；146 份 Surefire XML |
| Redis 专项 | 3/3，包含在同一次 744 项全量执行中 |
| 原 JaCoCo 门 | airuntime 447/504 分支（88.69%），companion 541/626（86.42%）；均超过原 85% 门槛 |
| 默认 Chromium 目录 | 最终 149 项：148 项均有通过证据，1 项仅适用于关闭功能的生产构建，在独立配置验证 |
| 独立 feature-off | 1/1，路由、入口、伙伴图集/播放器/API 均关闭 |
| 生产默认关闭构建 | Shell 15/15、Gallery 19/19、Landing 7/7 |
| 真实 HTTP | 27 个业务阶段 + 4 项协议检查全过；Node TAP 35/35 另含 4 个父容器，不能理解为 35 个业务阶段 |
| lint | --max-warnings 0 通过 |
| production build / bundle | Companion 开、关两组均通过；最大 chunk 396,005 bytes，门槛 512,000；CompanionView 分块仅开启时存在 |

默认浏览器覆盖 R05–R13 的身体、状态、动画、互动、Home、Feeding、聊天、渲染降级，以及 Shell、Landing、Gallery、登录注册生命周期、空间读取恢复、R16 文案和实际后端创作故事线。核对了桌面与 320px 等窄屏截图、键盘焦点/遮罩、触摸模拟与 reduced-motion；图库分析新增实际 API 下的四画布、日/周/月、公共/私有范围和刷新回归。

真实后端使用 Spring Boot 的 test,e2e、新 H2、独占 Redis 7。实际执行认证、控制器、应用服务、数据库、幂等、权限和 SSE；外部模型、图像生成与作品对象存储使用既有替身。这不是线上环境或外部供应商验收。

## 本轮修整与实际缺陷

1. **Vite 缓存隔离。** 此前临时验收副本通过 node_modules junction 共用了开发缓存，造成开发页面加载两份 Vue runtime，并出现 ECharts invalid dom。用户停止 Vite 后已备份缓存、重建并复验。现在 Vite 接受 VITE_CACHE_DIR，各 Playwright 开发服务使用工作区内独立 `.vite/e2e*`；ESLint 排除生成缓存。不能让验收副本再次改写交互开发服务的缓存。
2. **测试契约与时钟。** Windows PATH 断言使用当前平台的 join / delimiter；spaceUser/list/my fixture 返回数组。无意写入检查只豁免两个明确的只读 POST，未知 POST/DELETE 仍失败，新增 Node 回归。浏览器时钟安装和暂停使用确定的先后时间，消除主机调度导致的“暂停时间已过去”。
3. **功能开关测试。** Shell / Landing 原用例在 production-default 项目误期待开启功能的入口。按真实构建开关分别断言关闭入口与不可用页面，开启分支仍完整验证；两个构建分支都已执行。
4. **聊天清理测试。** R06 原测试向正确取消后的原生 ReadableStream 强行 enqueue，测试自身抛错。现在明确验证 cancel 被调用、迟到 enqueue 被拒绝、旧文本不出现、当前页面与 Shell 保持正确状态。整个表现状态文件 9/9 通过。
5. **图库分析兼容缺陷。** 分类汇总的 totalSize 别名在 H2 小写标识符设置下返回键 totalsize，后端按 totalSize 取值导致空指针。内部别名及取值统一为 total_size，DTO、聚合含义和权限不变。真实 MyBatis + JDBC 回归中，旧代码保留大小写时通过、小写时失败；修复后两项通过，同时校验未分类、私有/已删除图片排除和实际总量。
6. **测试库日期兼容。** 仅在 src/test 增加 DATE_FORMAT、YEARWEEK 的 H2 别名，支持当前查询实际使用的日/月格式和单参数周规则。8 项测试先因函数缺失失败，修复后通过，包括跨年周边界。生产数据库、迁移、日期查询及领域规则不变。[MySQL 单参数 YEARWEEK 规则](https://dev.mysql.com/doc/refman/8.4/en/date-and-time-functions.html#function_yearweek)是周边界依据。
7. **R12 真实共享视图。** 新用例验证 Gallery 首次点击前无伙伴 IO、重复 submit 只有一次 POST、草稿及焦点交接、持久历史只有一条、刷新不重发。跨路由最后一个消费者退出后按 D-08 重读历史；Home 与抽屉同时存在时共享当前历史，不再发第二次读取。最初把跨页重读误判为重复请求的断言已更正，并增加同页共享断言，产品会话生命周期没有改动。

唯一生产后端改动是分类统计的内部别名；其余为测试、构建缓存配置和文档。没有修改 MCP、Companion 领域、数据库迁移、账号规则或前端业务流程。

## 浏览器执行链

- 完整首跑：148 项，145 通过、2 失败、1 个专用配置跳过。两个失败分别为已取消流的错误 fixture 和图库分析；不是完整通过。
- R06 表现状态独立复验：9/9。
- Shell / Landing 开启构建及新增真实故事线组合：24 通过、2 失败、2 因 serial 前置失败未运行。开启构建 22 项全过；两处失败分别是新聊天用例的跨页历史计数假设，以及分类别名兼容问题。
- 修正后真实 Companion 五项与图库分析一项：6/6。图库分析稳定截图与实际统计值断言另行复验 1/1 通过，四个图表完成入场动画后的截图已查看。
- 生产关闭构建：Shell 15、Gallery 19、Landing 7；专用 feature-off 1，全部通过。
- acceptance-matrix.mjs 按最终目录逐文件/标题核对首跑与复验，得到 148 passed + 1 专用配置 skipped。旧失败、未运行及红绿日志保留，不将重复复验相加成更多用例。

## 环境、命令与证据

归档：仓库 `output/r14-acceptance-20261009-1738/`（本地未跟踪产物），包含 Node/lint/build/bundle 日志、Surefire XML、JaCoCo HTML/XML、浏览器 JSON/trace/截图、HTTP 日志、逐项矩阵和源文件 SHA-256 对照。

临时目录：`C:\Users\MECHREVO\AppData\Local\Temp\lipc-acceptance-20261009-161308`。基线由 git archive 获取，本轮 27 个源码/测试/配置文件逐一同步核对；node_modules 是现有依赖的 junction，Vite 缓存及构建产物位于副本。实际安装 Vue 3.5.43、vue-echarts 8.3.1、ECharts 6.1.0；未更新 package-lock 或用户 pnpm 文件，不声称这是 npm 锁文件的全新安装验收。

工具链：Node 22.23.3、Java 21、Maven Wrapper 3.9.15、Docker Redis 7.4.10。Maven 使用已有可信镜像配置与本机仓库，没有修改 TLS、代理或全局配置。

后端命令（副本根目录，Java 21，独占 Redis 6380 启动后）：

```powershell
.\mvnw.cmd -s G:\Maven\apache-maven-3.9.4\conf\settings.xml '-Dmaven.repo.local=G:/Maven/apache-maven-3.9.4/mvn_repo' '-Dredis.integration.enabled=true' '-Dredis.host=127.0.0.1' '-Dredis.port=6380' clean verify
```

前端命令（Node 22，副本 frontend；每轮指定独立 output 防止覆盖旧证据）：

```powershell
node --test tests/*.test.mjs
node node_modules/eslint/bin/eslint.js . --max-warnings 0
$env:VITE_COMPANION_ENABLED = 'true' # 然后 false 重复 build / budget
node node_modules/vite/bin/vite.js build
node scripts/check-bundle-size.mjs
node node_modules/@playwright/test/cli.js test --config playwright.shell.config.js --project production-default
node node_modules/@playwright/test/cli.js test --config playwright.gallery.config.js --project production-default
node node_modules/@playwright/test/cli.js test --config playwright.landing.config.js --project production-default
node node_modules/@playwright/test/cli.js test --config e2e/config/companion-disabled.config.js
```

原 test:api:e2e 编排明确只支持 POSIX，没有删除保护后直接在 Windows 运行。证据 runner/ 保存本机一次性编排：调用现有 api-e2e-policy，核对专用容器完整 ID/端口，拒绝复用 18124/15173，固定 test,e2e 和唯一 H2，不继承后端凭证覆盖，等待自有 Java 就绪，在 finally 清理自有进程。长 classpath 用 Java 参数文件。它不是新增的通用 npm 入口；再次使用须重新设置自有容器 ID 与本机路径。

```powershell
node scripts/acceptance-local.mjs browser
node scripts/acceptance-local.mjs api
node scripts/acceptance-local.mjs browser companion.spec.js space-analyze.spec.js
```

完整首跑 evidence-browser-1791533013560；HTTP evidence-api-1791534027741；开启构建组合 evidence-browser-1791534440750；六项真实故事线最终复验 evidence-browser-1791535022848；分析图表稳定截图 evidence-browser-1791535240767。各 run.json 记录后端/检查子进程结束情况。

## 保留边界

- 本地 Chromium 桌面/窄屏/触摸模拟不等于 Safari、Firefox 或真机；已查看关键截图，不声称全站逐页视觉审核。
- 真实模型 smoke 仍需操作者显式开关；生产 AI/COS/外部 MCP 连通性、普通 COS 上传下载没有被替身故事线证明。MCP 管理 CRUD 现有回归通过，不代表恢复了外部 MCP。
- 严格 Accept: text/event-stream 的既有 406 限制、R03 样片授权/初访理解、R15 身份/API 决策继续保留，不在本轮扩展。
- 不合并 main、不部署、不启动 R15。变更留在 nexus/mainline-r11 工作区，未提交推送；用户已有文档、pnpm 文件和 output 保留，新证据只写入独立子目录。
- 测试服务和独占 Redis 已清理，没有停止日常服务。最终端口快照显示 5173、6379 仍在监听，8124 已未监听（此前日常 Java PID 7360 已不存在）；本轮没有停止或重启该后端，后续日常手测需要重新启动它。
