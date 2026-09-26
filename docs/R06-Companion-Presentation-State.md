# R06 — Companion Presentation State Protocol

## 范围与基线

2026-09-26。功能分支 `feat/r06-companion-presentation-state` 从最新 `origin/main`（`c1670d8`）创建；main 尚无 R05，先 fast-forward 接入 R05 已通过的三个提交，至 `f9357e5`。原工作区及其暂存内容保持不动。

R06 只添加前端表现协议与已有请求生命周期的观察适配，不修改数据库、Java 领域、HTTP DTO、成长算法、Feed 幂等语义或资产。R07 的动画状态机、动作过渡与新素材不在本次范围。

## 已核实的事实

- `CompanionHomeView`：`companion.lifeStage` 为 LIGHT / SEEDLING / COMPANION，`mood`、`relationship` 可缺失；ID / revision 是业务身份，不是角色种族。R05 将三阶段统一绘制为成年绫页，未知阶段静态降级。
- `CompanionMood`：五轴范围 0–100，全部随时间向 0 衰减；0 是中性，不能把低 energy 解释成疲惫。`CompanionViewAssembler.moodSummary` 用最大轴、阈值 5 和 energy → joy → loneliness → inspiration → irritation 的并列顺序生成摘要。协议使用相同数值规则，完全不读摘要文本，也不在浏览器模拟衰减。
- `CompanionRelationship`：familiarity / trust / closeness / tacit 为 0–100，recentFeedback 为 -100–100；尚未互动可以没有记录。不能从 absence / 0 推断厌恶或亲密。
- Feed 的 `GROWN` / `FAMILIARITY` 回执只含成长与伙伴快照，旧幂等 key 可以回放旧结果。成功后已有主页刷新才提供最新 Mood / Relationship。
- Chat SSE 只有 message / done / error，没有情绪、动作标签或音频播放状态。waiting / streaming 是请求事实，不能宣称正在说话，更不能解析文字猜测情绪。当前 EOF 也会结束请求，不把它当作新的业务成功事实。
- Proposal GET `/proposals/active` 可能惰性生成或过期提案；合法状态 PENDING / DONE / IGNORED / SUPPRESSED / EXPIRED。敲打只抑制当前提案，不能直接变成“生气”。Shell 不追加此 GET。

## 设计选择

选择 **前端纯 Mapper + 一个应用实例内的观察源**。服务器 DTO 无法同时知道浏览器的加载、请求中和流式接收状态；分别在三个表现组件推导又会产生分歧。Domain Truth 保持原位置，观察源只发布已有真实信号，Mapper 是唯一推导入口。

协议 v1 的正交字段：

| 字段 | 含义 |
| --- | --- |
| availability | disabled / unobserved / loading / unavailable / error / absent / ready |
| companionId / lifeStage | 已确认实例的身份与原始阶段；不改写为角色阶段 |
| freshness | unknown / fresh / stale；喂养完成到权威刷新成功前、或喂养结果不确定时，情绪与关系中性降级 |
| activity | idle / feeding / thinking / responding / acknowledging；仅表示当前请求活动 |
| attention | none / proposal；已取得的 PENDING 提案，不因提案内容改变 |
| affect | neutral / energetic / cheerful / lonely / inspired / irritated；已知完整 Mood 快照的确定性投影 |
| rapport | neutral / familiar / close；表现熟悉程度，不是新领域等级 |
| issues | 加载或操作失败的有限代码，不包含错误消息或私人文本 |
| appearance | R05 assetKey / visualStage / allowIdle；目前只绑定绫页成年形态 |

关系投影是明确的展示策略：完整合法关系快照中，trust 与 closeness 均 ≥60 为 close；否则 familiarity ≥20 为 familiar；其余 neutral。负反馈不变成愤怒，不覆盖情绪。未知数值、缺轴、非有限值和越界均中性降级。数字字符串兼容 JSON 序列化，但拒绝空串、布尔和隐式类型转换。

优先级：可用性先于所有角色信号；ready 时 streaming → chat waiting → feed pending → proposal action pending → idle。情绪和关系不覆盖活动；PENDING 提案用独立 attention 保留。操作失败只记录 issue，不等于角色情绪，也不遮掉另一条仍进行的请求。不存在成功庆祝计时器，不重复播放 Feed 回放，也不保留永久“忙碌”。

观察源由 Companion 页面持有有代数的租约。Shell、Home、Portrait 消费同一个只读映射结果。页面退出即释放、账户变化即作废；旧页面和延迟响应不能污染新页面/账号。Shell 在还未观察 Home 时是 unobserved、中性导航插画，不声称该用户已拥有伙伴。页面退出后回到此状态，不把缓存当成实时事实。无需轮询或额外 API。

`fresh` 指本页面最近一次成功的权威主页观察，且没有已知 Feed 失效信号，不代表跨标签页或服务端实时订阅。MODEL Chat 组装上下文也可能触发后端 Mood 惰性衰减，但不会回传 Mood；本轮不补额外请求，也不把消息内容替代快照。没有新权威快照时，只保留最后已知事实，不在前端计时推演领域变化。

Shell / Portrait 继续使用 R05 静态资产，Home 继续 R05 眨眼；三处标记相同的协议字段供验证和后续 R07 消费。未知阶段、错误和缺失不启动 Idle。资源失败、可见性、用户暂停、减少动态仍由 R05 渲染器负责，与 Domain Truth 分离。

代码入口：

- `src/presentation/companionPresentation.js`：纯 Mapper，协议 v1 的唯一派生入口，返回冻结结果。
- `src/presentation/companionPresentationChannel.js`：页面观察租约，阻断旧发布者。
- `src/stores/companionPresentation.js`：每个 Pinia 应用实例的一份观察源及只读计算结果；无 API、持久化或定时器。
- `CompanionView.vue`：主页、Feed 信号；两个子面板通过 `presentation-change` 发布已观察到的请求事实。
- `CompanionPresenceSlot`、`CompanionBody`、`CompanionPortrait`：消费同一个结果，不自行判断 Mood、Feed、Chat 或 Proposal。R05 的纯阶段资产兼容函数只由 Mapper 调用。

未来增加 Displayed Companion 时，应先由上游选择并确认实例，再取得该实例的观察租约；增加 Species 时替换 appearance 的绑定策略即可。当前协议不虚构服务器尚无的 speciesId 或 displayedCompanionId，不增加多实例选择器。

## 实施与验证单元

1. 测试先定义 Mapper 的组合、优先级、边界、缺失和异常；测试租约替换、退出及晚到回调。
2. 实现纯协议与观察源；接入 Home / Feed、Chat 生命周期、Proposal 生命周期以及三类消费者。
3. 浏览器验证同源同步、并发活动、请求失败/恢复、Feed 幂等、路由清理、已有资产降级与功能关闭；运行全前端测试、lint、生产构建及相关真实服务回归。
4. 独立检查状态架构与竞态，记录验证证据后提交推送；停在 R06。

## 验证结果

2026-09-26，Node 22.23.3 / Chromium，独立工作区：

- Node 全前端测试 **78/78**，其中新增协议/观察源测试 **11 组**，包括全部 12 种活动冲突组合、Mood 五轴边界/并列、关系阈值、空/错类型/越界、缺字段、未知阶段、不可变性、无文本推断、租约替换和账户重置后的晚到发布。
- 浏览器协议用例 **8/8**：三入口同源且无额外请求；加载/错误/无伙伴/404；Chat 与 Feed 并发及权威刷新；Feed 结果不确定、同键重试、刷新失败；SSE/网络失败恢复；提案抑制；跨路由晚到流事件；Feed/敲打并发竞态。
- R05 身体回归 **11/11**（四种阶段、暂停/遮罩/离屏/减少动态、素材失败、移动宽度及 Idle 节奏），沿用原资产和播放器。
- 真实后端 Companion 回归 **4/4**：H2 + 本机临时 Redis 6380，既有唤醒/喂养同键重试/Mood/Relationship/聊天/记忆/Proposal 全闭环通过；身份、成长、历史和契约快照保持。测试 Redis 不持久化，结束后已关闭，未修改 `G:\Redis` 配置。
- 生产 feature-off **1/1**：无伙伴路由、素材、播放器或伙伴 API 请求。
- lint、开启伙伴功能的 production build、bundle budget（最大 chunk 390,035 bytes）、`git diff --check` 通过。Java、Schema、API DTO、依赖锁文件及 R05 资产无修改。

浏览器合计 **24 个不同用例通过**。最终 19 条协议+身体组合运行中，18 条通过，1 条在 `page.goto`、尚未执行功能断言前遇到本机 `ERR_NETWORK_ACCESS_DENIED`；随后不改产品代码重跑完整 8 条协议用例全部通过。此环境异常如实保留，不用增加自动重试掩盖。

独立架构审查发现并复现了一处现有竞态：Feed 触发的旧 Proposal GET 可以在 scold 的 SUPPRESSED 回执后重新写回 PENDING。浏览器回归先确认失败，再以读取代数和动作期间不追加读取修正，通过后保留测试。该局部顺序保护不改变提案领域规则。

可重跑命令（在 `li-picture-cloud-frontend`，Node 22）：

```text
node --test tests/*.test.mjs
node node_modules/eslint/bin/eslint.js . --max-warnings 0
node node_modules/@playwright/test/cli.js test --config e2e/config/companion-presentation.config.js
node node_modules/@playwright/test/cli.js test e2e/companion.spec.js
node node_modules/@playwright/test/cli.js test --config e2e/config/companion-disabled.config.js
# 开启 VITE_COMPANION_ENABLED=true 后：
node node_modules/vite/bin/vite.js build
node scripts/check-bundle-size.mjs
```

真实后端用例依赖 JDK 21 与 6380 测试 Redis；其余协议/身体用例用显式 API fixture。移动端结果是浏览器尺寸模拟，不表示真机性能认证。R06 到此结束，未实现 R07。
