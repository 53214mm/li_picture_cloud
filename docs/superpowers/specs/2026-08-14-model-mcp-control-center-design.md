# 模型与 MCP 控制中心设计

> 日期：2026-08-14
>
> 依据：三年规划第三季度（2027-02—2027-04）"模型与 MCP 控制中心"；第 19 轮 brainstorm 的
> AI 控制中心与 Provider/额度架构设计稿（`.superpowers/brainstorm/497-*/content/ai-control-center.html`、
> `provider-and-credit-architecture.html`）。
>
> 性质：**规格先行**。本阶段只定边界、领域模型与整合路径；实现排期在第三季度，需经设计评审与安全评审。

## 1. 目标

让用户自己决定"用谁的模型、谁来付费、哪些图片可以发送、伙伴能调用哪些工具"，同时让伙伴
代码不因供应商变化而重写：

1. **统一模型网关**：能力画像（文本/视觉/工具调用/结构化输出/推理/Embedding/图像生成）驱动路由，
   不按品牌名猜能力。
2. **三个任务路由**：语言/Agent、视觉理解、图片创作各自独立选择连接与降级顺序。
3. **用户级模型连接**：连接、连接测试、启停、轮换、删除；BYOK 凭据独立加密存储，只显示尾号。
4. **能力内核**：`discover / invoke / observe` 三入口，逐步迁移现有本地工具与 MCP 工具。
5. **使用记录**：任务、模型、费用来源、用量、安全错误码；不展示敏感原文。

## 2. 领域模型（草案）

### 2.1 模型连接 `ModelConnection`

- 字段：`id, subjectId, provider(OPENAI/ANTHROPIC/GOOGLE/DEEPSEEK/MOONSHOT/DASHSCOPE/...),
  displayName, endpointUri, credentialRef(凭据引用，非明文), enabled, revision, 时间戳`。
- 一主体多连接；同一 Provider 可有多个连接（不同账号/密钥）。
- 不变量：`endpointUri` 生产只允许白名单主机的 HTTPS；凭据引用不可回显明文。

### 2.2 能力画像 `ModelCapabilityProfile`

- 连接测试成功后生成的快照：`text, vision, toolCall, structuredOutput, reasoning,
  embedding, imageGeneration, maxContextTokens, sync/async, costHint`。
- 未测试/未知能力一律按"不支持"处理；画像可过期（Provider 变更后重新测试）。

### 2.3 任务路由 `TaskRouting`

- 三个固定任务：`LANGUAGE_AGENT / VISION_UNDERSTANDING / IMAGE_CREATION`。
- 每任务一个路由规则：默认连接、备用顺序、每主体的覆盖。
- 选择顺序：任务能力需求 → 伙伴偏好（未来）→ 用户默认 → 平台守门（能力/隐私/预算/健康度）。

### 2.4 凭据 `CredentialRef`

- 只存引用：`credentialId, kind(BYOK/PLATFORM), provider, tail4, algorithm, cipherTextRef`。
- 主密钥与数据库分离；保存后不回显；可测试、轮换、撤销、删除。
- BYOK 失败后**不**静默切换平台钱包；便宜模型失败不静默切贵模型。

### 2.5 使用记录 `ModelUsageRecord`

- `id, subjectId, task, modelConnectionId, provider, modelCode, costSource(BYOK/TRIAL/PLATFORM),
  estimatedTokens, success, safeErrorCode, correlationId, createdTime`。
- 只存安全字段；不存提示词、响应正文、图片 URL。

### 2.6 能力内核 `CapabilityRegistry`

- `discover(subject)` → 当前主体可用的能力目录（含权限/风险/费用/同步性）。
- `invoke(subject, capabilityId, input)` → 统一守门（权限、确认、额度、幂等）后执行。
- `observe(subject, capabilityId, invocationId)` → 异步进度与结果。
- 现有 `ToolRegistration`、`RefreshableMcpToolProvider` 迁移为本地/MCP Adapter，
  Spring AI 只接收当前主体可用的能力目录。

## 3. 数据模型方向

```sql
model_connection          -- 用户模型连接（含凭据引用、端点、启停、revision）
model_capability_profile  -- 连接测试快照（能力矩阵、上下文上限、费用提示）
task_routing_rule         -- 三任务的默认与备用连接（每主体覆盖）
credential_vault          -- 加密凭据（独立主密钥，只存密文与尾号）
model_usage_record        -- 追加式使用记录（任务/模型/费用来源/安全错误码）
mcp_connection            -- 平台审核的 MCP 服务 + 工具白名单 + 逐项启停（第二阶段）
```

全部按主体分片或单表 `!SINGLE`；凭据表与业务表分库/分区待安全评审。

## 4. 与现有伙伴能力的整合路径

1. **视觉理解**：现有 `VisualObservationProvider`（DashScopeVisionClient）成为
   `VISION_UNDERSTANDING` 路由的第一个 Adapter；伙伴视觉配置改为"引用一个模型连接"。
2. **站内对话**：`CompanionChatService` 的 ChatModel 改为经网关解析（语言路由），
   默认仍可指向平台 DashScope；`COMPANION_CHAT_POLICY=MODEL` 语义升级为"使用用户选择的语言连接"。
3. **图片创作**：新 `IMAGE_CREATION` 路由，首个 Adapter 目标 `gpt-image-2`（设计默认）；
   图片生成结果照旧按主体入库并保留血缘。
4. **伙伴上下文**：组装器只声明"需要语言能力"，不感知具体模型。

## 5. 安全与隐私边界

- BYOK 凭据：独立加密存储、主密钥与数据库分离、只显示尾号、日志永不记录 Token。
- SSRF 防护：端点白名单、网络出口限制、DNS 重绑定校验，之后才允许自定义 URL（当前阶段锁定官方地址）。
- MCP：只开放平台审核的服务与工具白名单，逐项启停；任意 MCP URL 属于"后续隔离后开放"。
- 分账：BYOK 失败不切平台钱包；平台试用永远硬上限；超限停止不自动扣费。
- 使用记录不含提示词/响应/图片 URL；主动任务只保存"连接引用"，执行时重新解析凭据与预算。

## 6. 分阶段实施顺序（第三季度）

1. 模型连接 + 凭据保险库 + 能力画像（只做 DeepSeek 语言 Adapter 与 Qwen 视觉 Adapter 归入网关）；
2. 任务路由与伙伴对话/视觉接入网关（无行为变化，只有连接抽象）；
3. 连接测试、启停、轮换、删除 + 使用记录 + AI 控制中心页面（模型服务/任务路由/使用记录）；
4. 图片创作路由与首个图片 Adapter（gpt-image-2）——与 Q4 图片炼金联动评审；
5. MCP 白名单连接与逐项启停；
6. 平台试用额度账本（报价/预留/结算）与 BYOK 分账验证。

## 7. 明确不做（本规格范围）

- 支付、订阅购买与额度包售卖（第二年商业化阶段）。
- 任意 OpenAI-compatible URL、任意 MCP URL、第三方能力包市场。
- 桌面宠物/IM 渠道的模型偏好（渠道各自独立）。
- 语义检索记忆（第七季度）。

## 8. 验收条件（第三季度）

- 伙伴对话与视觉理解可分别切换模型连接而不改业务代码；
- BYOK 与平台额度分账正确，余额并发下永不为负；
- 连接测试失败或撤销后，任务路由显式降级并记录安全错误码；
- 使用记录可回答"哪位用户、哪个任务、哪个模型、谁付费、多少用量"；
- 凭据明文不出现在任何日志、快照或接口响应中。
