# 绫页下一步记忆治理设计（提议，尚未实现）

日期：2026-10-09。本文是对当前代码的只读设计审查结论，不是已实现功能、测试通过声明或新的既定产品决策。先完成当前人格/客观视觉/试演台增量的测试与提交，再决定并实施本增量；本文不授权扩大到自动抽取、真实模型调用、部署或线上数据操作。

## 建议结论与授权边界

下一片建议做**用户手动保存的持久对话记忆基础**：明确来源、归属、纠正与忘记，同时阻断被忘记内容经旧聊天再次进入模型。不是完整的自动长期记忆系统，也不是人格自学习。

- 已批准：`DECISIONS.md` L-01 保留普通聊天和历史；L-04 要求来源化记忆、纠正/忘记/撤权、派生内容一致处理及防止从旧来源重新提取；L-07 要求隔离测试和费用边界；L-10 保持现有 Java/Spring/Vue 技术栈。
- 本文提出、尚未成为 DECISION：纠正/忘记后递增账号上下文代数，保留历史显示，但不再把此前的任何原始聊天作为后续模型工作上下文。它是缺少依赖谱系时的保守隐私方案，代价是连无关的短期聊天连续性也会重置。实施前须把这个取舍明确写入本轮实现说明和界面，不能声称用户已逐项确认这个技术方案。
- 本片不做历史回填、自动抽取、摘要、向量索引、额外模型调用、人格意见自动持久化。共同经历也只保存为“用户讲述”，不自动视为独立验证的事实。

## 当前代码证据

1. `src/main/java/com/li/lipicturecloud/domain/companion/CompanionMemory.java` 要求正数 `growthRecordId`；`src/main/resources/db/changelog/changes/2026-08-14-companion-mood-relationship-memory.xml` 将其设为非空且唯一。当前来源枚举只有 VISUAL/DEMO。不能伪造成长记录来接入聊天。
2. `application/companion/CompanionChatService.java` 的 `assembleTurns` 在记忆筛选之后独立读取原始历史，逐条加入 USER/COMPANION 内容。删除记忆不会删除或屏蔽此前的用户陈述和模型复述。此处及以下省略的 Java 路径均位于 `src/main/java/com/li/lipicturecloud/`。
3. `domain/companion/CompanionChatMessage.java` 没有上下文代数或回复依赖信息。`CompanionChatService.persistReply` 异步保存回复；仅用忘记时间或最大消息 ID 作为截点，不能可靠处理晚到的旧上下文回复。
4. `application/companion/CompanionMemoryService.confirmedMemoriesUsableBy` 先 `findRecent(limit)`，再过滤 CONFIRMED。`infrastructure/persistence/companion/MybatisCompanionMemoryRepository.findRecent` 的 SQL 仅排除 DELETED，先排序、LIMIT。大量新的待确认/忽略/失效记录会挤掉较旧的已确认记录。
5. 撤权检查独立使用最多 100 条 `findActive`。若只把 CONFIRMED 过滤移进 SQL，新的结果可能超出原 active 扫描覆盖范围。必须检查真正准备召回的每条来源，不能把两个不同的截断窗口当成同一批数据。
6. `CompanionMemory.delete` 只改状态，原文仍存储；`CompanionViewAssembler.memory` 对终态隐藏内容。这不能被描述为数据库原文已擦除。
7. `application/companion/CompanionFeedingCoordinator.java` 的候选创建以成长记录为来源。唯一成长记录约束不能阻止同一图片下一次喂养产生新的候选。
8. `li-picture-cloud-frontend/src/components/companion/CompanionMemoryPanel.vue` 目前是图片记忆界面，异步局部状态没有共享聊天 Store 已有的完整账号/生命周期保护。

现阶段边界还见 `LINGYE-PERSONA-VISION.md`、`LINGYE-PRESENCE-BACKEND.md`、`LINGYE-REHEARSAL-UI.md`；研究建议见 `LINGYE-TECH-RESEARCH.md`。本文不把这些文档中的待测/待实现内容提升为验收事实。

## 1. 独立对话记忆聚合

建议新增 `CompanionConversationMemory`、仓储端口、应用服务、MyBatis entity/mapper，以及追加式 Liquibase 迁移。保留现有图片记忆聚合的不变量。

建议字段：

- `id`、`companionId`、`subjectId`
- `sourceMessageId`、`sourceCreatedTime`
- `content`、`provenance`（USER_STATEMENT / USER_EDITED_STATEMENT）
- `status`（CONFIRMED / FORGOTTEN）、`revision`、`createdTime`、`updatedTime`
- 唯一键覆盖归属和 `sourceMessageId`，且包括 FORGOTTEN 行

只允许从已持久化、归当前登录账号及其伙伴所有的 USER 消息保存。前端先展示有界摘录或用户编辑的陈述，由用户保存；不让模型回复或视觉模型独白变成用户事实。内容校验复用安全纯文本及码点预算思路，不要因聊天最长 500 字而静默截断用户选定记忆。

纠正替换当前内容并更新修订来源/时间；不为了方便再存一个永久的旧正文副本。忘记保留无正文的来源 tombstone，禁止普通更新恢复。数据库留有历史聊天不等于已删除原始资料，界面说明必须区分这两件事。

## 2. 提议的隐私重置边界

建议增加 owner/companion 唯一的 `CompanionContextPolicy` 持久行，保存 `generation`；聊天行增加 `contextGeneration`，存量默认 0。

- 纠正/忘记和 generation 递增同事务提交；失败时一起回滚。
- 普通历史查看仍可读取旧代数；用于模型的历史查询必须先限制 owner/companion/current generation，再 LIMIT。
- 新用户消息在与治理变更一致的账号级事务边界下捕获 generation。迟到回复永远继承原始请求的 generation，不能在保存时改贴最新代数。
- 已准备的上下文在实际发起供应商请求前检查 generation；流式输出与完成保存也需要旧代数失效处理。原子捕获/变更依靠数据库并发约束，不只依靠单 JVM 锁。
- 成功治理变更后，前端使当前接收流及旧响应失效；服务器仍需独立守卫，不能用客户端 abort 代替。
- 不能撤回已经发给供应商的输入或已经显示的字符。需要明确请求发起与治理提交的竞争边界，不承诺网络层绝对撤回。保留 BYOK/platform 原有账本、额度和终态结算语义，不因新增取消路径漏结算或重复结算。

同一边界须接入既有图片记忆纠正、删除、来源失效；否则原始历史中的图片相关 COMPANION 回复仍可把旧内容带回来。若“忽略”表示停止使用，也需覆盖曾经召回该记录的旧上下文。相关但未被撤回的持久记忆仍可按话题召回；重置的是原始工作聊天窗口，而不是清空全部记忆。

这是隐私优先的连续性取舍。以后若实现精确的回复/摘要依赖谱系，可另行评估更小范围的失效，当前不要假装已具备该能力。

## 3. 来源阻断与保存时回读

保存端点在同一变更事务内回读真实消息、归属、USER 角色、generation，以及已有记忆/来源阻断记录；不能信任界面携带的旧文本与消息 ID。

- 活跃来源的重复保存返回当前记录，不用旧消息正文覆盖已纠正内容。
- FORGOTTEN 来源不能因重试或 upsert 自动恢复。
- 本保守方案不允许从已重置前的消息重新保存，界面标记该来源不可再直接保存。
- 用户明确重新添加是另一个有意操作，不能混入普通重复 POST。第一片可暂不开放这一能力。
- 没有自动抽取器并不免除来源检查；这是以后接入任何抽取/摘要/索引任务的强制入口契约。

### 图片来源的范围选择

若本轮声称统一支持图片来源级忘记，还需 owner-scoped 通用来源阻断记录（source kind / source ID / 状态与时间），并在 `CompanionFeedingCoordinator` 生成候选前检查。图片阻断按图片来源而非只按一次 growthRecordId，否则重新喂养会再次生成候选；新的喂养动作不应默默解除已有忘记要求。纠正后也不能自动用同一旧来源恢复被改掉的说法。

若为了保持首片小而暂不接图片生成端阻断，必须明确交付仅为“手动对话记忆来源治理 + 旧聊天重放保护”，图片再提取治理仍未完成。不能把这个范围选择写成完整 L-04 已兑现。

本方案的保证是有来源依据的阻断，不是任意同义事实的全库语义擦除。以后用户在新消息中主动再次提供同一信息，与系统擅自从旧来源恢复不同；相关产品措辞需保留这个区别。

## 4. 召回与权限

新增 owner-scoped confirmed 查询，在 SQL LIMIT 前过滤状态。对实际返回的图片候选逐项授权，真实撤权排除、授权基础设施故障 fail-closed，然后做相关性与最终预算裁剪。不要依赖独立 100 条 active 扫描证明候选已经安全。

将图片和对话记忆投影成只读上下文 DTO，携带 kind、owner、source、时间、当前内容和修订标记，避免放宽图片领域模型。初版沿用有界词法召回并允许为空；当前 `RelevantCompanionMemories` 的词表偏图片对象，对普通中文事实会有漏召回，需明确限制并补充固定测试，不宣称语义理解已完成。

用户修订仅让当前内容参与匹配；旧正文、tombstone、来源文本不能成为隐蔽的召回通道。检索结果始终是数据，不得改变人格和权限。

## 5. API 与 UI 最小范围

建议新增（接口名仍属实现提议）：

- `POST /companion/conversation-memories`：`sourceMessageId`、用户审阅的 `content`
- `GET /companion/conversation-memories`
- `POST /companion/conversation-memories/{id}/correct`：`content`、`expectedRevision`
- `DELETE /companion/conversation-memories/{id}`：携带明确的 `expectedRevision` 契约（推荐查询参数，避免依赖 DELETE body 支持）

身份只取认证态，拒绝 body 身份字段；查询和 CAS 写入均带归属条件；对外保持精确十进制字符串 ID。新表记得进入现有 ShardingSphere single-table 配置及迁移注册，不部署数据库。

在 `CompanionChatPanel.vue` 对已持久化且仍符合来源条件的 USER 消息增加“记住这句话”，未取得服务器 ID 的乐观消息不能点击。持久化读取后的服务端资格为准，不由本地消息文本猜测。

记忆面板增加对话记忆区，展示来源时间、用户讲述/修订标记、纠正与忘记。界面提示建议：“纠正或忘记后，会重新开始当前对话上下文；历史仍可查看。”旧来源禁用保存，账号切换、离页及较新请求使旧回调失效。不要把模型自己说过的话渲染为已确认用户事实。

既有图片忘记还有一个相邻缺陷：`CompanionMemoryService.transition` 在删除之前也要求图片访问权，但前端给 INVALIDATED 记录提供删除按钮。无正文的忘记操作应验证记忆归属而不要求重新获得已撤回图片的权限；其余读取/确认/纠正不能因此放宽权限。

## 6. 必要测试与停止条件

- 跨用户 source/message/memory ID、伪造主体、模型消息保存均被拒绝。
- 保存时源回读：旧 generation、FORGOTTEN tombstone、并发纠正、重复请求不能恢复旧内容。
- 纠正/忘记 CAS 与 generation 原子回滚，双标签页/并发请求和进程重启后仍有效。
- 捕获 BYOK 与 platform 的真实待发送 turns：重置后无旧 USER 原文，无旧图片相关 COMPANION 复述，只召回当前获准内容。
- 旧流在重置前准备、重置后完成，不能进入新代数上下文；覆盖发起前、流中、保存前的竞争及账本终态。
- 100 条以上新非确认记录不挤掉应查到的已确认记录；每个被召回图片候选均经过来源授权，异常 fail-closed。
- 纠正后仅当前文字匹配；无关问候为空；没有后台抽取、模型额外调用或历史回填。
- 若本片包含图片来源阻断：同一图片产生新的 growthRecordId 也不能绕过阻断。
- 追加迁移 rollback/reapply、唯一约束、索引、ShardingSphere 配置与字符串 ID 回归。
- Vue 真实组件覆盖双击、失败后重试、未持久化消息、账号切换、离页、旧响应及共享聊天流失效。

完成标准是上述契约有实现和隔离自动验证，并如实保留浏览器/真实模型/人工体验验收边界。当前只完成设计；没有执行这些测试，也没有开始本设计的实现。先完成第一增量的测试与提交，再安排这一片。
