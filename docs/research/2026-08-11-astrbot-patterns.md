# AstrBot 对“图像生命体 / 虚拟伙伴”的设计启示

> 调研日期：2026-08-11
>
> 范围：仅使用 AstrBot 官方文档、官方 GitHub 仓库源码和官方发布说明；调研时最新公开版本为 [v4.27.2](https://github.com/AstrBotDevs/AstrBot/releases/tag/v4.27.2)。源码结论固定到提交 [`939bdce`](https://github.com/AstrBotDevs/AstrBot/tree/939bdce0dfa4a025fed6ed7677cf6c914aa3442c)，避免后续代码变化造成引用漂移。
> 说明：下文“事实”均附第一方链接；“本项目建议”是基于这些事实、面向 LiPictureCloud 的设计推论，不代表 AstrBot 官方立场。

## 结论先行

AstrBot 最值得借鉴的不是聊天界面，而是一组清晰的分层：**平台事件进入统一事件模型，确定性规则先筛选，Agent 再按需调用受控工具，最后通过可寻址的会话适配器回送结果**。它还把模型 Provider、Agent Runner、人格、会话、工具和平台适配器拆成不同概念，这对“会主动行动的图像生命体”很有价值。[Agent Runner 文档](https://docs.astrbot.app/use/agent-runner.html)明确区分单次模型推理与可多步感知、行动、观察的 Agent 循环；[平台适配器文档](https://docs.astrbot.app/dev/plugin-platform-adapter.html)则说明原生消息如何规范化为统一消息事件，再进入事件队列。

但不应把 AstrBot 的“主动”直接等同于成熟的数字生命自治。当前公开实现主要有两种：一是持久化的一次性 / Cron 定时任务到点唤醒主 Agent，二是群聊消息到来后按概率触发回复。前者适合用户明确创建的提醒和周期回顾，后者只是事件到来时的随机介入，不是长期动机系统。[v4.14.0 发布说明](https://github.com/AstrBotDevs/AstrBot/releases/tag/v4.14.0)将 Future Tasks 和 SubAgent 标为实验能力；[主动 Agent 文档](https://docs.astrbot.app/use/proactive-agent.html)也将该能力标为实验性。

面向本项目，推荐把生命体做成一条可审计的闭环：

```text
图库领域事件 → 资格/频率/隐私/预算策略 → 最小化上下文 → Companion Agent
            → 受权能力工具 → 行动闸门 → 站内消息/通知 → 反馈与结构化成长
```

核心原则是：**模型负责理解、创意与提议，后端负责身份、授权、配额、资源状态和最终执行**。图片必须以站内 `pictureId / spaceId` 及其权限为根，而不是把任意 URL、文件路径或目标会话交给模型自由决定。

## 1. AstrBot 的主动消息、定时与事件驱动能力

### 1.1 定时唤醒：Future Task / Cron Job

AstrBot v4.14.0 引入实验性的 Future Tasks：Agent 可以创建一次性任务或 Cron 周期任务，到点后系统唤醒主 Agent，并可主动向原会话报告结果；任务可在 WebUI 中查看、修改和删除。[主动 Agent 文档](https://docs.astrbot.app/use/proactive-agent.html)与[官方 v4.14.0 发布说明](https://github.com/AstrBotDevs/AstrBot/releases/tag/v4.14.0)共同描述了这条路径。

当前源码中的运行过程更具体：

1. `CronJobManager` 启动时从数据库同步已启用、需要持久化的任务到 `AsyncIOScheduler`，说明调度状态不是只保存在进程内存中。[`manager.py` L97-L143](https://github.com/AstrBotDevs/AstrBot/blob/939bdce0dfa4a025fed6ed7677cf6c914aa3442c/astrbot/core/cron/manager.py#L97-L143)
2. 新任务携带 payload；一次性任务使用指定时刻，周期任务使用五段式 crontab 和时区，调度器设置了 30 秒 misfire 宽限。[`manager.py` L172-L200](https://github.com/AstrBotDevs/AstrBot/blob/939bdce0dfa4a025fed6ed7677cf6c914aa3442c/astrbot/core/cron/manager.py#L172-L200) [`manager.py` L226-L273](https://github.com/AstrBotDevs/AstrBot/blob/939bdce0dfa4a025fed6ed7677cf6c914aa3442c/astrbot/core/cron/manager.py#L226-L273)
3. 到点后任务先记录运行、完成或失败状态与错误信息；一次性任务执行后会被移除。[`manager.py` L307-L348](https://github.com/AstrBotDevs/AstrBot/blob/939bdce0dfa4a025fed6ed7677cf6c914aa3442c/astrbot/core/cron/manager.py#L307-L348)
4. payload 保存投递会话和任务说明，执行时构造一个合成的 `CronMessageEvent`，恢复该会话的配置、角色和历史，再让主 Agent 最多执行 30 步；如果存在投递会话，还会注入主动发消息工具。[`manager.py` L359-L393](https://github.com/AstrBotDevs/AstrBot/blob/939bdce0dfa4a025fed6ed7677cf6c914aa3442c/astrbot/core/cron/manager.py#L359-L393) [`manager.py` L395-L510](https://github.com/AstrBotDevs/AstrBot/blob/939bdce0dfa4a025fed6ed7677cf6c914aa3442c/astrbot/core/cron/manager.py#L395-L510)
5. `future_task` 工具支持创建、编辑、删除和列出任务。创建时会把当前事件的 UMO（统一消息来源）和发送者绑定到任务；编辑、删除和查询会校验当前 UMO 与发送者是否与任务一致。[`cron_tools.py` L52-L99](https://github.com/AstrBotDevs/AstrBot/blob/939bdce0dfa4a025fed6ed7677cf6c914aa3442c/astrbot/core/tools/cron_tools.py#L52-L99) [`cron_tools.py` L101-L209](https://github.com/AstrBotDevs/AstrBot/blob/939bdce0dfa4a025fed6ed7677cf6c914aa3442c/astrbot/core/tools/cron_tools.py#L101-L209)

主动投递依赖 UMO 定位平台和会话。官方开发文档建议保存 `event.unified_msg_origin`，之后通过 `context.send_message(umo, chain)` 回送；消息链可包含图片、文件、语音和视频。[主动消息开发文档](https://docs.astrbot.app/dev/star/guides/send-message.html) 当前用户文档列出的主动投递平台包括 Telegram、OneBot v11、Slack、Lark、Discord、Misskey 和 Satori，因而“能接收消息”并不自动等于“能主动投递”。[主动 Agent 文档](https://docs.astrbot.app/use/proactive-agent.html)

**本项目建议：**将这套模式用于用户明确创建的“明天提醒我整理这组照片”“每周生成一次影像周报”等任务；任务记录至少绑定 `userId、companionId、spaceId / pictureIds、trigger、timezone、policyVersion、createdBy`。到点时必须重新检查当前用户权限、空间成员关系、图片状态、频率和预算，而不能只相信创建时保存的上下文快照。

### 1.2 事件触发：插件生命周期钩子

AstrBot 把平台消息转换成 `AstrMessageEvent`，其消息链统一表示文本、@、图片、语音、视频和文件；插件可按命令、消息类型、平台和权限过滤，多个过滤器为 AND 关系。[消息事件文档](https://docs.astrbot.app/dev/star/guides/listen-message-event.html) 除接收消息外，官方还提供 LLM 请求前后、Agent 开始/结束、工具调用前后、消息发送前后的生命周期钩子；插件可设置优先级，并通过 `stop_event()` 阻止后续处理。[消息事件文档](https://docs.astrbot.app/dev/star/guides/listen-message-event.html) 新的 Agent/工具钩子在 [v4.23.2 发布说明](https://github.com/AstrBotDevs/AstrBot/releases/tag/v4.23.2)中也有记录。

**本项目建议：**把消息事件换成图库领域事件，如 `PictureUploaded、PictureFavorited、AlbumUpdated、GeneratedPictureSaved、UserInactive、ScheduleDue`。事件只表示“有事情发生”，不能直接等同于“应该通知用户”。先由确定性策略判断用户是否开启该触发器、是否在静默时间、是否达到新颖度阈值、是否已去重及资源是否仍可访问；只有通过后才构造 Agent 上下文。

### 1.3 概率主动回复：可借鉴触发点，不照搬决策方式

AstrBot 的群聊主动回复配置当前只提供 `possibility_reply`：按 UMO / 群白名单和概率控制，默认概率为 0.1。[配置文档的 `provider_ltm_settings.active_reply`](https://docs.astrbot.app/dev/astrbot-config.html) 源码进一步表明，它只在群消息、未被显式 @ / 唤醒时参与判断，并以 `random.random() < probability` 决定是否主动回复。[`group_chat_context.py` L56-L128](https://github.com/AstrBotDevs/AstrBot/blob/939bdce0dfa4a025fed6ed7677cf6c914aa3442c/astrbot/builtin_stars/astrbot/group_chat_context.py#L56-L128) 若启用图片描述，系统还会先额外调用一次文本模型生成图片说明。[`group_chat_context.py` L88-L108](https://github.com/AstrBotDevs/AstrBot/blob/939bdce0dfa4a025fed6ed7677cf6c914aa3442c/astrbot/builtin_stars/astrbot/group_chat_context.py#L88-L108)

**本项目判断：**随机概率适合给群聊机器人制造“偶尔插话”的感觉，不适合决定一个长期伙伴何时打扰用户。应替换为可解释的资格分数，例如：`用户意愿 × 事件价值 × 新颖度 × 关系阶段 − 打扰成本 − 预算压力`，再叠加硬性的冷却时间、日/周上限、静默时段和连续未响应衰减。

## 2. 插件、工具、Agent、人格、会话与平台的机制

### 2.1 插件与工具调用

AstrBot 的插件称为 Star，由元数据、运行时代码及可选 Skills 等组成，支持热重载；官方要求持久化数据放在 `data` 目录，而不是插件目录，并强调异步 I/O 和异常处理。[插件开发文档](https://docs.astrbot.app/dev/star/plugin-new.html) 官方 WebUI 文档同时提醒，插件市场内容可能更新，团队无法完全保证第三方插件安全。[WebUI 文档](https://docs.astrbot.app/use/webui.html)

工具可以继承 `FunctionTool`，也可以用 `@filter.llm_tool` 从函数与 docstring 生成参数 schema，再通过 `context.add_llm_tools` 注册。[LLM 与工具开发文档](https://docs.astrbot.app/dev/star/guides/ai.html) 用户可以在 WebUI 启停工具；若模型不支持 Function Calling，AstrBot 会自动移除工具定义。[函数调用文档](https://docs.astrbot.app/use/function-calling.html)

当前权限源码值得特别注意：非内置工具会在每次调用时经过权限包装；但如果开发者未显式声明权限，默认角色是 `member`，即普通成员可调用，只有声明为 `admin` 才会拒绝非管理员。[`func_tool_manager.py` L214-L265](https://github.com/AstrBotDevs/AstrBot/blob/939bdce0dfa4a025fed6ed7677cf6c914aa3442c/astrbot/core/provider/func_tool_manager.py#L214-L265) [`func_tool_manager.py` L453-L513](https://github.com/AstrBotDevs/AstrBot/blob/939bdce0dfa4a025fed6ed7677cf6c914aa3442c/astrbot/core/provider/func_tool_manager.py#L453-L513)

**可借鉴：**工具注册、结构化 schema、运行时工具集、调用前后钩子，以及“模型可见工具”和“实际授权”分离。
**不照搬：**第三方代码与主进程共享信任边界、未声明权限即普通成员可用。图库工具应默认拒绝，明确声明资源范围和副作用，并在服务端按当前主体重新做对象级授权；插件/扩展应有签名、能力清单、隔离执行和审计。

建议给伙伴开放的是窄工具，而不是任意文件或网络能力，例如：

- `search_my_pictures(query, allowedSpaceIds)`：仅返回当前用户可见图片的 ID 与必要元数据；
- `describe_picture(pictureId)`：读取单张已授权图片的派生描述，不接受任意 URL / 本地路径；
- `create_story_draft(pictureIds)`：只产生草稿；
- `generate_image_draft(sourcePictureIds, intent)`：记录来源图、模型、提示版本和费用；
- `save_generated_picture_to_my_space(draftId)`：由后端确定所有者与空间；
- `schedule_companion_action(trigger, scope)`：创建受策略约束的任务；
- `send_companion_message(content, reasonCode)`：只能发送给当前伙伴所属用户，不能由模型传任意目标会话。

### 2.2 Provider 与 Agent Runner

AstrBot 将 Chat Provider 描述为“说话能力”：输入提示、历史和工具定义，输出文本或工具调用；Agent Runner 则负责“思考与行动”，在感知、规划、行动、观察的循环中反复调用 Provider 与工具，直到完成或超时。[Agent Runner 文档](https://docs.astrbot.app/use/agent-runner.html) 这种拆分也避免把 Dify、Coze 等自带循环的服务误当成普通单轮模型 Provider。[Agent Runner 文档](https://docs.astrbot.app/use/agent-runner.html) 源码层还分别定义了聊天、语音识别、语音合成、Embedding 和 Rerank 等 Provider 类型，并通过注册器记录 Provider 的类型和元数据。[`provider.py` L27-L104](https://github.com/AstrBotDevs/AstrBot/blob/939bdce0dfa4a025fed6ed7677cf6c914aa3442c/astrbot/core/provider/provider.py#L27-L104) [`register.py` L14-L50](https://github.com/AstrBotDevs/AstrBot/blob/939bdce0dfa4a025fed6ed7677cf6c914aa3442c/astrbot/core/provider/register.py#L14-L50)

**本项目建议：**保持四层边界：`ModelGateway / Provider` 只封装模型差异；`CompanionOrchestrator / Agent` 决定是否及如何多步执行；`Capability Tools` 承载站内能力；`Policy & Authorization` 独立于提示词和模型。标签、向量、标题、压缩摘要等工作优先路由到便宜模型或确定性算法，真正需要跨图片叙事、创意生成时再使用强模型。

AstrBot 的 SubAgent 允许主 Agent 只看见 `transfer_to_*` 工具，把特定工具集和可选 Provider 交给专门子 Agent；但官方文档也明确指出该能力仍属实验性，子 Agent 的人格/Skills 未隔离且历史不保存。[SubAgent 文档](https://docs.astrbot.app/use/subagent.html) 因此本项目初期不必为“生命感”引入多 Agent；只有当“影像检索、故事创作、生成编辑”等职责和工具集确实复杂时，才把它作为内部编排优化，不能把它当作长期成长机制。

### 2.3 人格、会话与记忆

AstrBot 的 Persona Manager 管理人格记录；人格含系统提示、开场对话和工具配置，其中工具字段为 `None` 表示全部工具、空列表表示不启用工具。Conversation Manager 管理会话的创建、查询、删除、历史、标题和人格绑定。[人格与会话管理文档](https://docs.astrbot.app/dev/star/guides/ai.html) 会话对象按平台与用户/会话来源建立关联，并可更新 history、title 和 persona。[`conversation_mgr.py` L95-L120](https://github.com/AstrBotDevs/AstrBot/blob/939bdce0dfa4a025fed6ed7677cf6c914aa3442c/astrbot/core/conversation_mgr.py#L95-L120) [`conversation_mgr.py` L279-L305](https://github.com/AstrBotDevs/AstrBot/blob/939bdce0dfa4a025fed6ed7677cf6c914aa3442c/astrbot/core/conversation_mgr.py#L279-L305) 自定义规则还可按 UMO 选择人格、Provider、是否启用 LLM / TTS 等。[自定义规则文档](https://docs.astrbot.app/use/custom-rules.html)

上下文方面，AstrBot 支持限制最大上下文、群聊上下文注入和独立会话；上下文压缩在达到模型窗口约 82% 时触发，可选择丢弃较早轮次或用 LLM 总结并保留最近对话。[配置文档](https://docs.astrbot.app/dev/astrbot-config.html) [上下文压缩文档](https://docs.astrbot.app/use/context-compress.html) 知识库则通过 Embedding / 检索 / Rerank 提供按需相关内容。[知识库文档](https://docs.astrbot.app/use/knowledge-base.html)

**机制边界判断：**人格、会话历史、压缩摘要、群聊上下文和知识库各解决不同问题，不能直接等同于一个可解释、可纠正、可遗忘的长期关系记忆。图像生命体应拆出以下模型：

| 模型 | 内容 | 更新规则 |
|---|---|---|
| `CompanionIdentity` | 名字、形象来源图、语气、边界 | 用户显式配置；低频变更 |
| `RelationshipState` | 关系阶段、信任、共同经历里程碑 | 只由可解释事件推进，可回滚 |
| `UserPreference` | 主题偏好、静默时间、主动频率、允许图库范围 | 用户可查看、修改、关闭 |
| `EpisodeMemory` | “一起完成了某本相册”等事件 | 带日期、来源图片与置信度 |
| `ImageFact` | 标签、描述、向量、人物/地点等派生信息 | 绑定图片 ID、版本、来源模型与同意范围 |
| `CurrentState` | 当下情绪、精力、好奇主题 | 短期衰减，不伪装成永久事实 |
| `ActionPolicy` | 触发器、冷却、预算、授权层级 | 确定性策略，不能由 Agent 自行放宽 |

不要在每一轮把成长状态直接改写进 system prompt。AstrBot 官方开发文档提醒，频繁变化的 system prompt 会破坏提示缓存，并可能显著抬高费用；动态内容应放入当前请求的额外用户内容，长期大块记忆应通过工具查询或只检索相关摘要。[消息事件文档的上下文注入建议](https://docs.astrbot.app/dev/star/guides/listen-message-event.html) v4.24.2 还加入了只在当前请求生效、不写入历史的临时额外用户内容能力。[v4.24.2 发布说明](https://github.com/AstrBotDevs/AstrBot/releases/tag/v4.24.2)

### 2.4 平台适配器

AstrBot 的平台适配器负责把平台原生消息转为 `AstrBotMessage`，再构造 `AstrMessageEvent` 放入事件队列；发送时则把统一消息链转换回平台 SDK 支持的格式。适配器至少实现运行、按会话发送和元信息接口，核心层还处理媒体引用规范化与临时文件清理。[平台适配器开发文档](https://docs.astrbot.app/dev/plugin-platform-adapter.html) 平台抽象还显式暴露 `support_proactive_message`，进一步表明主动推送是适配器能力，而不是所有渠道天然具备的共同能力。[`platform.py` L92-L185](https://github.com/AstrBotDevs/AstrBot/blob/939bdce0dfa4a025fed6ed7677cf6c914aa3442c/astrbot/core/platform/platform.py#L92-L185)

**本项目建议：**定义与渠道无关的 `CompanionAction`，例如文本、图片卡片、相册回顾、确认请求；每个渠道声明 `supportsImage、supportsActionButton、supportsProactivePush、maxPayload` 等能力。第一阶段以站内动态/通知为主，未来接入邮件或 IM 时由适配器降级，不让 Agent 假定所有渠道都能主动推图、带按钮或承载相同隐私级别。

## 3. 借鉴与不照搬清单

| AstrBot 模式 | 本项目取舍 | 理由与落地方式 |
|---|---|---|
| 持久化一次性 / Cron 任务，到点唤醒 Agent | **借鉴** | 适合提醒、纪念日、周报；增加用户/伙伴/空间绑定、重授权、配额、幂等与过期策略。[调度源码](https://github.com/AstrBotDevs/AstrBot/blob/939bdce0dfa4a025fed6ed7677cf6c914aa3442c/astrbot/core/cron/manager.py#L172-L273) |
| UMO 统一定位平台和会话 | **借鉴概念** | 改成站内 `deliveryTarget`，目标只能由认证上下文派生，不能由模型任意填写。[主动消息开发文档](https://docs.astrbot.app/dev/star/guides/send-message.html) |
| 统一事件、过滤器、生命周期钩子 | **强烈借鉴** | 用领域事件 + 确定性资格引擎，再调用 LLM；工具前后统一做策略、审计和计费。[消息事件文档](https://docs.astrbot.app/dev/star/guides/listen-message-event.html) |
| Provider / Agent Runner / Tool / Adapter 分层 | **强烈借鉴** | 让模型可替换、工具可审计、渠道可降级，业务授权不落在提示词中。[Agent Runner 文档](https://docs.astrbot.app/use/agent-runner.html) |
| 人格绑定会话并可配置工具 | **部分借鉴** | 人格配置与会话绑定可用；但人格、长期关系状态和工具权限必须拆开。[人格与会话文档](https://docs.astrbot.app/dev/star/guides/ai.html) |
| 群聊按固定随机概率主动回复 | **不照搬** | 无法解释为何打扰，也不感知疲劳、价值、预算；改用资格评分 + 硬上限。[主动回复源码](https://github.com/AstrBotDevs/AstrBot/blob/939bdce0dfa4a025fed6ed7677cf6c914aa3442c/astrbot/builtin_stars/astrbot/group_chat_context.py#L110-L128) |
| 会话历史 / 压缩摘要充当“成长” | **不照搬** | 它们服务上下文窗口，不是可编辑、可溯源的长期记忆；另建结构化成长与记忆层。[上下文压缩文档](https://docs.astrbot.app/use/context-compress.html) |
| 默认普通成员可调用未声明权限的扩展工具 | **不照搬** | 图片空间涉及对象级权限；工具必须默认拒绝、显式声明能力并逐次授权。[工具权限源码](https://github.com/AstrBotDevs/AstrBot/blob/939bdce0dfa4a025fed6ed7677cf6c914aa3442c/astrbot/core/provider/func_tool_manager.py#L453-L513) |
| 实验性 SubAgent 作为系统核心 | **暂不采用** | 它的历史不保存、人格/Skills 未隔离；先用单 Orchestrator + 窄工具。[SubAgent 文档](https://docs.astrbot.app/use/subagent.html) |
| 第三方插件与主系统同等信任 | **不照搬** | 官方也提示无法完全保证市场插件安全；扩展应隔离、签名并最小授权。[WebUI 文档](https://docs.astrbot.app/use/webui.html) |

还有两个源码级风险信号尤其不应复制：当前 Cron 执行路径会把 `origin == "api"` 的任务视作管理员，并显式构造 `llm_safety_mode=False` 的 Agent 配置。[`manager.py` L420-L452](https://github.com/AstrBotDevs/AstrBot/blob/939bdce0dfa4a025fed6ed7677cf6c914aa3442c/astrbot/core/cron/manager.py#L420-L452) 这不是对 AstrBot 安全性的总体评价，而是说明本项目不能因为任务来自内部 API 或后台调度就提升权限，也不能因为是“伙伴主动行动”就关闭安全策略。

## 4. 面向图像生命体的具体设计蓝图

### 4.1 从“图片是附件”升级为“图片是有权属的生产资料”

伙伴的一切观察、创作和记忆都应引用站内资产：

```text
SourcePicture(s)
  └─ DerivedFact（标签/描述/向量，含版本与来源）
       └─ CompanionIntent（为什么行动）
            └─ DraftArtifact（故事/拼图/生成图草稿）
                 └─ User-approved Asset（保存位置、所有者、可见性）
```

每个派生物记录父图片 ID、原图版本、使用的 Provider / 模型、提示模板版本、生成成本、伙伴 ID、创建原因、可见性与审核状态。原图被删除、撤权或移出允许空间后，相关未来任务应失效，派生事实应按策略删除或匿名化。

### 4.2 主动行动的三档权限

| 档位 | 伙伴可做什么 | 示例 |
|---|---|---|
| 观察 `Observe` | 读取用户明确允许空间的低敏元数据，更新短期候选状态 | 发现一批新上传的旅行图片，但不打扰用户 |
| 提议 `Propose` | 生成低成本文案/方案草稿并询问 | “要不要把这 12 张做成周末故事？” |
| 执行 `Execute` | 只有低风险、用户预授权动作可自动完成；其余二次确认 | 自动生成站内周报；公开分享、删除、改变权限、付费生成永远需确认 |

这比把所有动作都交给一个“万能 Agent”更符合 AstrBot 工具与 Agent 分层带来的启示：Agent 可以选择能力，但最终动作仍应经过系统策略和工具权限。[函数调用文档](https://docs.astrbot.app/use/function-calling.html) [`func_tool_manager.py` 权限包装](https://github.com/AstrBotDevs/AstrBot/blob/939bdce0dfa4a025fed6ed7677cf6c914aa3442c/astrbot/core/provider/func_tool_manager.py#L214-L265)

### 4.3 有生命感但不打扰的触发器

| 触发器 | 伙伴行为 | 约束 |
|---|---|---|
| 新图片上传 | 异步生成/复用标签与描述，积累“好奇候选” | 默认不立即推送；批处理；敏感图片不外发 |
| 相册达到里程碑 | 提议生成回忆故事、封面或拼图 | 同一相册去重，优先草稿 |
| 一年前的今天 | 以原图为证据回顾共同记忆 | 用户可关闭；人物/地点敏感策略 |
| 连续一段时间无互动 | 最多一次低压力问候，随后指数衰减并暂停 | 不用焦虑或情感勒索话术 |
| 用户明确设定时间 | 准时提醒或执行已授权的站内动作 | 时区、过期、重授权、失败可见 |
| 每周固定时间 | 汇总本周图库变化与创作建议 | 周预算、无变化则静默 |

“持续成长”不应表现为 Agent 不受控地自我修改，而应表现为用户可见的能力与关系变化：它记住了哪些经用户确认的偏好、基于哪些图片形成了何种记忆、解锁了什么创作能力、为什么今天选择沉默或行动。所有成长记录都应可查看、纠正、删除和重置。

## 5. 安全、频率、隐私与成本控制

### 5.1 身份、授权与消息目标

AstrBot v4.24.2 曾修复普通用户可借 `send_message_to_user` 向任意会话发送消息的问题；当前源码仅允许管理员跨会话，普通用户只能发送到当前会话。[v4.24.2 发布说明](https://github.com/AstrBotDevs/AstrBot/releases/tag/v4.24.2) [`message_tools.py` L207-L219](https://github.com/AstrBotDevs/AstrBot/blob/939bdce0dfa4a025fed6ed7677cf6c914aa3442c/astrbot/core/tools/message_tools.py#L207-L219) 这说明“主动消息的目标寻址”本身就是安全边界。

本项目应做到：

- 调度任务创建时绑定主体与资源，执行时使用同一主体重新授权；内部 API、队列消费者和定时器都不自动获得管理员权限。
- `send_companion_message` 的收件人由伙伴所有权派生，不接受模型传入任意 `userId/sessionId`。
- 所有读图、写图、生成、保存、分享工具在服务端检查当前用户、空间成员关系、图片状态和操作权限；提示词中的“允许”不算授权。
- 高风险动作（删除、公开、跨空间移动、改变权限、外发原图、付费生成）必须二次确认；支持全局停止按钮和撤销/补偿。
- 每个任务有幂等键、最大重试、过期时间和失败记录，避免重复推送或重复生成。

### 5.2 频率与反骚扰

AstrBot 平台设置中的通用消息处理限流默认是 60 秒 30 条、超限等待，也可配置为丢弃；配置还提供忽略自身消息以避免循环、Agent 最大步数和工具超时等边界。[默认配置源码 L55-L99](https://github.com/AstrBotDevs/AstrBot/blob/939bdce0dfa4a025fed6ed7677cf6c914aa3442c/astrbot/core/config/default.py#L55-L99) [配置文档](https://docs.astrbot.app/dev/astrbot-config.html) 本次查阅的官方资料未定义跨平台统一的主动消息出站配额、FutureTask 最小周期、逐任务预算、幂等和失败告警；这是本项目必须自行补齐的治理层，而不能把通用消息限流当作完整答案。

本项目至少需要：用户主动总开关、每类触发器开关、时区与静默时段、单次冷却、每天/每周上限、未读/未响应衰减、同事件去重、伙伴自发消息不再触发自身、紧急熔断。建议初始默认采用保守档：新用户必须主动开启；低风险提议每日不超过 1 次、每周不超过 3 次；连续两次未响应后暂停非用户设定型主动消息。具体数值应通过产品实验调整，而不是固化为模型判断。

### 5.3 隐私与记忆治理

- 默认只访问用户明确授权的个人空间或相册；团队空间同时检查成员关系与团队策略，公共图库也只使用已批准的图片。
- 在把原图、可识别人脸/地点的信息发送给外部模型前取得明确同意；能用站内缓存的标签、向量或缩略信息完成时，不重复外发原图。
- 不在未明确选择的情况下推断健康、身份、宗教、政治倾向、亲密关系等敏感属性。
- 每条长期记忆保留来源图片、产生时间、提取模型、置信度和用途；用户可查看、纠正、删除、导出，也可一键清空伙伴记忆而不删除原图。
- 删除或撤权图片后触发派生事实、向量、任务和草稿的级联治理；日志与备份遵守独立保留期。
- 密钥加密保存，审计日志避免记录完整原图 URL、提示中的隐私正文和 Provider 密钥。

### 5.4 模型与成本预算

AstrBot 的群聊图片描述会为图片额外调用模型；官方上下文注入指南也警告动态 system prompt 会破坏缓存，并建议大块记忆按需检索。[群聊上下文源码](https://github.com/AstrBotDevs/AstrBot/blob/939bdce0dfa4a025fed6ed7677cf6c914aa3442c/astrbot/builtin_stars/astrbot/group_chat_context.py#L88-L108) [上下文注入建议](https://docs.astrbot.app/dev/star/guides/listen-message-event.html) 这些成本在“以图库为生产资料”的场景会被图片数量放大。

建议采用以下控制：

- 图片描述、标签、Embedding 按图片内容哈希与版本缓存；批量处理，避免每次对话重新看图。
- 稳定人格保持为可缓存前缀；当前情绪、事件和少量检索记忆放在临时请求上下文。
- 先以元数据/向量做候选召回，再只给模型最少的缩略图或描述；强模型只处理最终少量素材。
- 每用户、每伙伴、每任务分别设置 token、图片理解、图片生成和 Agent 步数预算；超预算时降级为规则模板、便宜模型、草稿或静默。
- 设置 Agent 最大步数、每工具超时、并发数、生成尺寸/张数上限；检测重复工具调用并终止循环。
- 审计每次行动的触发原因、图片来源、策略决策、Provider/模型、工具、token/生成费用、投递结果和错误；向用户提供可理解的“为什么它主动联系我”。

## 6. 建议实施顺序

1. **MVP：可控主动。** 只做用户显式创建的提醒、每周影像回顾和新相册提议；站内投递；建立任务、策略、审计和停用入口。
2. **成长层：结构化记忆。** 上线 `CompanionIdentity、Preference、EpisodeMemory、ImageFact`，提供记忆查看/纠正/删除和图片来源追溯。
3. **创作层：图片生产闭环。** 提供检索、故事草稿、拼图/生成图草稿、受控保存工具；先提议后执行，记录派生链和成本。
4. **多渠道与更强自治。** 在站内机制验证后才增加平台适配器和有限的预授权执行；SubAgent 仅在工具复杂度确实需要时引入。

最终衡量标准不应只是“伙伴发了多少消息”，而应包括：用户主动开启率、建议采纳率、主动消息忽略/关闭率、每次有效互动成本、越权/误投递数、记忆纠正/删除成功率，以及由伙伴创作后被用户保留的图片资产比例。
