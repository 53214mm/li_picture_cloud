# 多模型 Provider 兼容性与 Adapter 设计调研

> 调研日期：2026-08-11
>
> 范围：OpenAI、Anthropic Claude、Google Gemini、DeepSeek、Moonshot/Kimi 的第一方开发文档。本文不记录具体价格；价格、限流和模型清单均应在运行期或后台任务中从官方来源动态采集，并记录 `sourceUrl / fetchedAt / version`。
> 说明：“厂商事实”均附官方链接；“本项目建议”是面向 LiPictureCloud（Java 21、Spring AI 1.1.2）的设计推论，不代表厂商承诺。模型能力随时间和账号权限变化，生产调用前仍需探测与回归测试。

## 结论先行

1. **不能把“OpenAI-compatible”当成完整能力契约。** 它通常只表示某一条线协议（多数是 Chat Completions）的请求/响应形状相近；结构化输出、严格工具参数、推理字段、图像输入、会话状态和 SSE 终止语义仍有实质差异。
2. **采用“共享传输实现 + 独立 Provider Adapter”，不要做一个万能 OpenAI Adapter。** DeepSeek、Kimi 可复用 `OpenAiChatCompletionsTransport`；但必须各有薄 Adapter 负责能力校验、扩展字段、降级与响应规范化。OpenAI Responses、Anthropic Messages、Gemini Interactions 应分别使用原生 Adapter。
3. **默认拒绝未声明能力。** 每个模型在调用前按 `text / imageInput / stream / tools / strictTools / jsonObject / jsonSchema / reasoning` 做预检；`GET /models` 只在返回能力字段时作为证据，否则还需官方能力表和本项目验证结果。
4. **DeepSeek 作为首个预留 Provider，第一阶段应走 OpenAI Chat Completions 传输，定位为文本 Provider。** 不应因它支持 OpenAI Responses 格式就默认启用图像：其官方 Responses 兼容表明确写明 `input_image` 不支持，只会被替换为占位文本；Responses 兼容层也仍是无状态、部分字段静默忽略的子集。[DeepSeek Responses 兼容表](https://api-docs.deepseek.com/guides/responses_api/)

## 1. 当前接口与差异矩阵

| Provider | 当前主接口与流式形态 | OpenAI 兼容声明与边界 | 多模态、工具、结构化输出 | 认证、Base URL、模型发现 |
|---|---|---|---|---|
| **OpenAI** | 新项目推荐 `POST /v1/responses`；Chat Completions 仍受支持。Responses 原生统一文本、图像、内置/自定义工具和多轮状态。HTTP 流式为 `stream=true` 的语义化 SSE 事件。[迁移指南](https://developers.openai.com/api/docs/guides/migrate-to-responses) [流式指南](https://developers.openai.com/api/docs/guides/streaming-responses) | 基准实现，不涉及兼容层。需要注意 Responses 与 Chat Completions 本身就是两套不同的输入、输出和事件模型。 | 图像使用 `input_image`，可传 URL、Data URL 或 `file_id`。[图像输入](https://developers.openai.com/api/docs/guides/images-vision) 函数工具建议 `strict:true`；Responses 与 Chat 的 schema 所在层级不同。[Function Calling](https://developers.openai.com/api/docs/guides/function-calling) 结构化输出使用 JSON Schema；Responses 为 `text.format`，Chat 为 `response_format`。[Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs) | `Authorization: Bearer`，Base URL `https://api.openai.com/v1`。[Quickstart](https://developers.openai.com/api/docs/quickstart) `GET /v1/models` 只返回 ID、owner、created 等基础信息，不能单凭该响应判断图像/工具/结构化输出能力。[Models API](https://platform.openai.com/docs/api-reference/models) |
| **Anthropic Claude** | 原生主接口是 `POST /v1/messages`；`stream:true` 返回 SSE，事件可包含文本、工具输入和 thinking delta。[API 概览](https://platform.claude.com/docs/en/api/overview) [Streaming Messages](https://platform.claude.com/docs/en/build-with-claude/streaming) | 官方提供 `https://api.anthropic.com/v1/` 的 OpenAI SDK 兼容层，但明确说主要用于测试/比较，多数生产场景不视为长期方案。兼容层会忽略 Function Calling 的 `strict` 和 `response_format`，不支持 prompt caching，system/developer 消息会被提升并拼接，许多不支持字段静默忽略；完整能力应走原生 API。[OpenAI SDK compatibility](https://platform.claude.com/docs/en/cli-sdks-libraries/libraries/openai-sdk) | 原生工具调用返回 `tool_use` content block，客户端执行后以 user 消息中的 `tool_result` 回传，不是 OpenAI 的独立 `tool` 角色形态。[Tool use](https://platform.claude.com/docs/en/agents-and-tools/tool-use/how-tool-use-works) 原生结构化输出为 `output_config.format`，严格工具为工具顶层 `strict:true`。[Structured Outputs](https://platform.claude.com/docs/en/build-with-claude/structured-outputs) 图像是 `image` block，支持 base64、URL、Files API 的 `file_id`。[Vision](https://platform.claude.com/docs/en/build-with-claude/vision) | 原生 Base URL `https://api.anthropic.com`；API Key 使用 `x-api-key`，同时必须有 `anthropic-version`；也可用 Workload Identity 的 Bearer token。[API 概览](https://platform.claude.com/docs/en/api/overview) `GET /v1/models` 当前会返回 `capabilities`，包括 `image_input`、`structured_outputs`、thinking 等，是五家中较强的能力声明之一。[Models API](https://platform.claude.com/docs/en/api/models/list) |
| **Google Gemini** | 当前官方文本、图像、工具和结构化输出示例以 `POST /v1beta/interactions` 为主；流式在同一接口设置 `stream:true` 并使用 `?alt=sse`，多轮可传 `previous_interaction_id`，也可 `store:false` 后由客户端完整回放 steps。[文本与流式](https://ai.google.dev/gemini-api/docs/text-generation) | OpenAI 兼容 Base URL 为 `https://generativelanguage.googleapis.com/v1beta/openai/`，覆盖 Chat Completions、流式、函数调用、图像输入和 models 等常见表面；官方同时建议未在使用 OpenAI SDK 的项目直接调用 Gemini API。Google 专属能力需 `extra_body`，部分未列参数会静默忽略，Batch 上传/下载也不能完全走兼容层，因此不能当成全量复制。[OpenAI compatibility](https://ai.google.dev/gemini-api/docs/openai) | 原生图像是 `type:image`，通过 `uri` 或 base64 `data` 传入；较大或复用文件推荐 Files API。[Image understanding](https://ai.google.dev/gemini-api/docs/image-understanding) 原生函数工具直接放在 `tools`，模型输出 `function_call` step。[Function calling](https://ai.google.dev/gemini-api/docs/function-calling) 结构化输出为 `response_format:{type:"text", mime_type:"application/json", schema:...}`；JSON Schema 只支持子集，结构化输出与工具组合也有模型范围限制。[Structured outputs](https://ai.google.dev/gemini-api/docs/structured-output) | 原生服务根为 `https://generativelanguage.googleapis.com`，REST 使用 `x-goog-api-key`；Google 正从 standard key 迁移到绑定服务账号的 auth key，官方写明 2026-09 起拒绝 standard key，故新接入不应固化旧认证假设。[API keys](https://ai.google.dev/gemini-api/docs/api-key) `GET /v1beta/models` 返回 token 上限、`supportedGenerationMethods`、thinking 和采样参数等，但仍不是完整的统一能力清单。[Models API](https://ai.google.dev/api/models) |
| **DeepSeek** | 官方 Quickstart 首选 OpenAI 风格 `POST /chat/completions`，`stream:true` 为 data-only SSE，以 `[DONE]` 结束。[Quickstart](https://api-docs.deepseek.com/) 另有 Responses 格式：语义 SSE 不含 `[DONE]`，以 `response.completed / incomplete / failed` 结束，但兼容字段明显少于 OpenAI。[Responses API](https://api-docs.deepseek.com/guides/responses_api/) | 官方声明 API 同时兼容 OpenAI/Anthropic 格式。OpenAI Chat 路径最完整；Responses 层是部分兼容、无状态，`previous_response_id / conversation / store / background` 等不支持，部分不支持字段静默忽略。Anthropic 格式另用 `/anthropic`，版本头会被忽略，image/document block 不支持。[Quickstart](https://api-docs.deepseek.com/) [Anthropic compatibility](https://api-docs.deepseek.com/guides/anthropic_api/) | Chat 工具只支持 `function`；严格工具 schema 仍是 Beta，需切换到 `https://api.deepseek.com/beta`。[Tool Calls](https://api-docs.deepseek.com/guides/tool_calls) Chat 结构化输出是 `response_format:{type:"json_object"}`，还必须在 prompt 明示 JSON；不是 JSON Schema 约束，官方还提示偶发空内容。[JSON Output](https://api-docs.deepseek.com/guides/json_mode/) 当前公开 Chat 请求的 user content 是字符串；Responses 文档明确图像/file 输入不支持。Thinking + tool call 时必须把 `reasoning_content` 原样带回后续请求，否则会 400。[Thinking Mode](https://api-docs.deepseek.com/guides/thinking_mode) | `Authorization: Bearer`；OpenAI Base URL `https://api.deepseek.com`，Anthropic Base URL `https://api.deepseek.com/anthropic`。[Quickstart](https://api-docs.deepseek.com/) `GET /models` 仅有 `id / object / owned_by`，不声明图像、工具或 schema 能力。[Models API](https://api-docs.deepseek.com/api/list-models) |
| **Moonshot/Kimi** | 主接口为 OpenAI 风格 `POST /v1/chat/completions`；会话无状态，应用自行回传 messages。流式为 Chat Completion chunk SSE，以 `[DONE]` 结束。[Chat API](https://platform.kimi.ai/docs/api/chat) | 官方明确兼容 **OpenAI Chat Completions** 的请求/响应格式，可直接使用 OpenAI SDK；没有据此确认 OpenAI Responses API 兼容。Kimi 仍有 `thinking`、assistant message 的 `partial` 等扩展，且不同模型参数不同。[API Overview](https://platform.kimi.ai/docs/api/overview) | 支持 `image_url` / `video_url`，可传 Data URL 或 `ms://file_id`，但必须以模型能力为准。[Chat API](https://platform.kimi.ai/docs/api/chat) 工具沿用 `tools[].function`，Kimi 文档中 strict 默认开启且 schema 是其支持的 JSON Schema 子集。[Tool Use](https://platform.kimi.ai/docs/api/tool-use) `response_format` 支持 `json_object`，当前 Chat reference 还给出推荐的 `json_schema` Structured Output。[Chat API](https://platform.kimi.ai/docs/api/chat) | `Authorization: Bearer`，已核验的国际平台 Base URL 为 `https://api.moonshot.ai/v1`；中国区 Base URL 本次未从已读取页面确认，部署时须从对应区域控制台核验。[API Overview](https://platform.kimi.ai/docs/api/overview) `GET /v1/models` 除 OpenAI 基础字段外，还返回 `context_length`、`supports_image_in`、`supports_video_in`、`supports_reasoning`，可作为部分能力声明。[List Models](https://platform.kimi.ai/docs/api/list-models) |

## 2. 兼容性不能被压平的关键差异

| 语义 | 不能统一假设的原因 | 内部统一方式 |
|---|---|---|
| **协议世代** | OpenAI Responses、OpenAI Chat、Anthropic Messages、Gemini Interactions 的 item/block/step 与会话状态不同。 | 内部 SPI 与 wire protocol 分离；分别实现四种 transport。 |
| **流式结束与增量** | Chat 兼容流通常以 `[DONE]` 结束；Responses 使用具名语义事件；DeepSeek 还可能发送 SSE keep-alive comment。[DeepSeek Rate Limit & Isolation](https://api-docs.deepseek.com/quick_start/rate_limit) | 统一成内部 `Start / TextDelta / ReasoningDelta / ToolCallDelta / Usage / Completed / Failed` 事件，并保留原始事件供审计。 |
| **工具回合** | OpenAI/Kimi/DeepSeek 用 assistant `tool_calls` + role=`tool`；Claude 用 content block `tool_use / tool_result`；Gemini 用 interaction steps。 | 内部使用稳定的 `ToolCall{id,name,args}` 与 `ToolResult{callId,content,error}`；由 Adapter 负责回写厂商历史。 |
| **严格结构化** | “有效 JSON”“匹配 JSON Schema”“严格工具参数”是三档能力。DeepSeek Chat 只有 JSON object；Anthropic 的 OpenAI 兼容层甚至忽略 `response_format` 和 tool `strict`。 | `JSON_OBJECT`、`JSON_SCHEMA`、`STRICT_TOOL_SCHEMA` 分别建 capability；不自动互相升级。 |
| **图像输入** | 相同的 `image_url` 表面可能接收 URL、Data URL、file id，也可能被静默替换成文本占位；能力还取决于模型。 | 图片请求前强制检查 model capability；不支持时明确失败或路由到视觉 Provider，禁止静默丢图。 |
| **推理状态** | DeepSeek thinking + tools 需要回传 `reasoning_content`；Gemini 无状态回放时必须保留带签名的 model steps；OpenAI Responses 可用 response ID 续接。 | `ProviderTurnState` 保存厂商 opaque state，业务层不得解析或丢弃。 |
| **模型发现** | OpenAI/DeepSeek 只给基础 ID；Claude、Kimi 返回较丰富 capability；Gemini 返回 generation methods 但并不覆盖全部产品语义。 | `discovered metadata + 官方静态 profile + 本项目验证 override` 合并，默认 false，并记录来源与时间。 |

## 3. 面向 Java / Spring AI 的 Adapter 方案

### 3.1 推荐分层

```text
业务/Agent（只认识内部 Message、Tool、Schema、Media）
        ↓
ModelProvider SPI + CapabilityGuard
        ↓
Provider Adapter
  ├─ OpenAIResponsesAdapter  ─ OpenAiResponsesTransport
  ├─ AnthropicNativeAdapter ─ AnthropicMessagesTransport
  ├─ GeminiNativeAdapter    ─ GeminiInteractionsTransport
  ├─ DeepSeekAdapter        ─ OpenAiChatCompletionsTransport
  └─ KimiAdapter            ─ OpenAiChatCompletionsTransport
```

建议的最小内部接口职责：

- `complete(request)`、`stream(request)`、`listModels()`；
- `capabilities(model)` 与 `validate(request, capabilities)`；
- 将厂商错误规范化为 `AUTH / RATE_LIMIT / INVALID_REQUEST / UNSUPPORTED_CAPABILITY / TRANSIENT`，同时保留原始状态码、request ID 和 error body 的脱敏副本；
- 将 usage、finish reason、tool call、reasoning 和流式终态映射到内部类型，未知字段放入受控 `providerMetadata`，不要直接泄漏到领域模型。

### 3.2 为什么是“共享 Transport、独立 Adapter”

- **可共享：** DeepSeek 与 Kimi 的文本 Chat、普通 SSE、OpenAI 风格函数调用可复用同一 HTTP codec 和 Spring WebClient 连接池。
- **不可共享：** DeepSeek 的 `reasoning_content` 回放、Beta strict base URL、无图像能力；Kimi 的 `partial`、`ms://file_id`、视觉/视频能力字段和 strict 语义；两者都不能由一个 `baseUrl` 开关安全表达。
- **Anthropic、Gemini优先原生：** 两家的官方兼容文档都展示了功能丢失或专属字段；若为了短期迁移启用兼容端点，也应作为单独的兼容模式，而不是替代原生 Adapter。
- **Spring AI 边界：** 可以让各 Adapter 最终实现/包装 Spring AI 的 `ChatModel` 与流式接口，但不要让 Spring AI 的某个 OpenAI options 类成为领域配置模型；否则新增 Responses、Messages 或 Interactions 特性时会被最低公分母锁死。

## 4. DeepSeek 首个预留 Provider 的最小配置

第一阶段最小字段如下；这里只定义配置契约，不修改现有配置文件：

```yaml
providerId: deepseek
enabled: false
protocol: OPENAI_CHAT_COMPLETIONS
apiKey: ${DEEPSEEK_API_KEY}
baseUrl: https://api.deepseek.com
model: <显式模型 ID，不在代码中写死长期默认值>
```

字段理由：

- `providerId`：稳定路由与指标标签，不能拿 host 名代替；
- `enabled`：预留 Provider 默认关闭，避免仅配置了占位 key 就进入路由；
- `protocol`：明确选择 Chat Completions；未来若试用 DeepSeek Responses 或 Anthropic 格式，不通过修改 URL 偷换协议；
- `apiKey`：只接受 Secret/环境变量引用，不记录到日志；
- `baseUrl`：保留可配置性以支持官方区域/企业端点，但应做 allowlist 与 HTTPS 校验；
- `model`：必须显式配置并通过 `GET /models` 校验。模型别名和可用性会变化，不把模型清单固化为枚举。

以下是**Provider profile，而不是要求用户填写的最小配置**：

```text
stream=true
toolCalls=true
imageInput=false
jsonObject=true
jsonSchema=false          # 指 Chat Completions 主路径
strictToolSchema=false    # 普通 base URL；Beta /beta 需显式功能开关
reasoningStateReplay=true
```

连接/读取超时、重试、代理、并发限制和熔断应沿用全局 HTTP/Provider 运维配置，不必复制为 DeepSeek 专属最小字段。若以后启用 strict tools，增加明确的 `betaFeatures.strictTools` 并由 Adapter 选择官方 `/beta` 路径；不要让业务代码拼 URL。

## 5. 动态价格与能力元数据

- 不在源码、Provider 配置或本文中固定价格；建立独立的 `ModelCatalog`，按厂商官方页面/API 定期刷新并保存原币种、计量单位、输入/输出/缓存/媒体维度、抓取时间和来源 URL。
- 模型能力与价格分开版本化：价格变化不应使 Adapter 代码发布，能力变化也不应由价格表隐式推断。
- 路由前使用最后一次已验证 capability snapshot；过期或未知能力按不支持处理。对于图片场景尤其如此，避免把站内图片发给只支持文本却会静默忽略图片的兼容端点。

## 最终取舍

本项目应把 **DeepSeek/Kimi 视为“共享 OpenAI Chat 传输的独立 Provider”**，把 **Anthropic/Gemini 视为原生协议 Provider**，把 **OpenAI Responses** 作为独立协议世代。这样既能复用 Spring WebClient、SSE parser、鉴权注入和基础 Chat codec，又不会把兼容层的静默忽略、结构化输出等级、图像能力和推理状态差异泄漏到图片业务与 Agent 编排中。
