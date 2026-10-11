# 绫页站内事件与静态形象独立审查

审查时间：2026-10-09 16:32 UTC。范围为本轮共享工作树的站内 DEMO 协调器、传输层、AppLayout、PictureDetail 生命周期及静态名称/形象修正。对照 DECISIONS L-01 至 L-10；不代表浏览器、真实模型、生产或新成长美术验收。

## 结论

当前前端审查范围没有遗留阻断性发现。审查期间发现的问题已由实现者修正，审查者未修改产品代码。最终聚合测试、构建与发布由主任务另行确认。

## 已发现并复验的修正

1. 旧导航的失败回调会过早解除更新导航的暂停。独立 Node 探针复现：两个导航意图重叠，旧导航失败后 pictureOpened 仍能请求。修正后使用早期路由广播及 exact target object 归属，旧 afterEach/onError 不结算新意图；真实 Vue Router/SFC 回归覆盖两个延迟 guard。
2. 图片详情的组件级 beforeEach / update guard 晚于早期异步鉴权，不能独自保护同组件 ID 切换。详情现订阅最早全局导航广播；定向测试覆盖早期鉴权等待、旧成功/失败、重叠取消、账号切换、卸载及写入完成后的 UI 回调。
3. 新站内 widget 增加一个 portrait，使旧浏览器全局 portrait 选择器不再唯一。相关既有浏览器用例现限定原 chat-card 范围；本轮未运行浏览器，不把选择器审查或 collection 当执行通过。
4. 图片打开去重标记随账号变更/退出一起重置，避免继承前一账号的观察记录。

## 检查到的边界

- 默认关闭、仅内存状态，真实模型不可选；前端不会自动继承普通聊天 MODEL 模式
- 真实站内来源仅已确认进入伙伴页/图库，以及当前授权详情读取成功；没有图片载荷、ID、URL、DOM 文本、输入框或聊天正文进入事件
- decision 请求字段仅 sessionId/eventId/sequence/observedAt/eventType/page；事件 ID 最长 64；响应严格核对会话、事件、规范序号和 DEMO 模式
- 本地提交上限 1–12、跨路由保留计数/45 秒冷却；失败、抑制和恢复不会补发旧事件
- 隐藏、遮罩、聊天草稿/阶段、导航意图、账号和退出使待收响应失效；未把客户端中止描述为取消已发服务端操作
- 打开会话不以 abort 假装撤销；共享传输队列先排空/清理迟到会话再允许另一个适配器打开，覆盖站内/试演台单 owner 会话交接
- 回复按钮只打开既有普通聊天入口；没有插入演示历史、自动填词或自动发送
- 生产视觉所有阶段固定静态；旧播放器控制器测试以明确合成 capability 保留。真实编译 Body/Artwork/Stats 检查互动、键盘语义标签、拖放、失败降级和业务数值不变
- 对外名称绫页与阶段文字独立；现有成人立绘仍是唯一素材，不声称幼年形象或视觉成长已完成

## 审查者独立执行

Node 22.23.3，前端目录：

- `node --test tests/companionStaticIdentity.test.mjs tests/companionAnimation.test.mjs tests/companionRenderIntegration.test.mjs tests/companionDispositionPanels.test.mjs`：36/36 通过
- 最终补充详情回归后 `node --test tests/sitePresence.test.mjs tests/sitePresenceShell.test.mjs tests/pictureDetailLifecycle.test.mjs tests/companionPresence.test.mjs`：75/75 通过，0 失败/跳过
- `git diff --check`：通过

以上为两组定向验证，不冒充全量前端、后端 Maven、浏览器或真实 HTTP。审查者没有运行 Maven、浏览器、收费供应商，也没有提交、推送、部署或操作凭据。

## 附加只读审查：既有图片记忆的窄范围修正

同日 16:34 UTC 审查 `CompanionMemoryService`、`CompanionMemoryRepository`、`MybatisCompanionMemoryRepository` 及其 service/persistence 回归，没有发现遗留产品阻断项：

- 新 `findConfirmed` 在 SQL LIMIT 前限定 companion、subject 和 CONFIRMED，保留创建时间/ID 倒序及 1–100 上限；不会让新待确认记录挤掉较旧已确认候选
- 召回入口验证 companion owner，防御性剔除错归属/错状态行；对真正返回的候选逐来源授权，不再借用另一个截断 active 窗口来推断权限
- 撤权/消失从本次候选排除，即使失效 CAS 失败也不重新放行；授权系统故障仍 fail-closed，且不误把故障永久记成撤权
- delete 仍先检查当前伙伴与记忆 subject 归属；仅删除允许绕过来源读取权限，返回 DELETED 状态且 content/originalContent 都为空。确认、纠正、忽略仍检查来源读取权限
- 审查建议已落实为真实数据库回归：156 条较新记录中含 104 条 active，明确证明旧候选在 active(100) 窗口外，再撤权并断言召回为空/状态持久化为 INVALIDATED；另覆盖错归属、SQL 范围/排序/limit、候选授权异常，以及不依赖 active 查询的撤权 CAS 失败。此处描述测试内容，运行结果以 Java 报告为准

这是既有图片记忆查询/归属/逻辑删除修复，不能描述为数据库原文擦除、来源重提取阻止、原始聊天清理、摘要/索引治理或整套长期记忆完成。Java 运行结果由实施者及主任务的独立报告提供，审查者未运行 Maven。
