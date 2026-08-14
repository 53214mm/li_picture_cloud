# Round 22：模型与 MCP 控制中心前两个垂直切片指南

> 分支 `deepseek/companion-mood-relationship-memory`，提交 d133f89 → … → 80487b0。
> 规格来源：`docs/superpowers/specs/2026-08-14-model-mcp-control-center-design.md`。

## 本切片交付了什么

1. **领域与持久化**（`domain/airuntime` + `infrastructure/persistence/airuntime`）
   - `model_connection` / `credential_vault` / `model_usage_record` / `task_routing_rule` 四张表（Liquibase changeSet 20260814-31..35，已注册 changelog-master 与 static/dynamic 两份 sharding `!SINGLE`）。
   - 领域记录全部带规范构造器与 CAS（revision 恰好 +1）；`model_usage_record` 只存安全字段，`credential_vault` 只存密文与尾号。
   - JaCoCo 门禁从 `domain.companion` 扩到 `domain.airuntime`（分支覆盖率 ≥ 0.85）。

2. **凭据加密与连接服务**
   - `AesGcmCredentialCipher`：AES-256-GCM，12 字节随机 IV，格式 `base64(iv):base64(密文+标签)`；认证失败一律解密失败。
   - 主密钥来自环境变量 `MODEL_CREDENTIAL_MASTER_KEY`（64 位十六进制或 Base64，须解码为 32 字节）。非 `local/test/e2e` 环境缺失时**启动失败**；本地/测试回退到固定开发密钥并打警告。
   - `PropertyEndpointAllowlist`：仅 HTTPS，主机须精确等于或为白名单后缀的子域（点边界，防旁系域名）。
   - `CredentialService`：存储即加密；轮换走 revision CAS；`reveal` 是明文唯一出口（仅供服务内部使用，无 HTTP 端点）。
   - `ModelConnectionService`：创建校验白名单与凭据归属；启用要求已绑凭据；启停/轮换/删除全部 CAS。

3. **连接探测与使用记录**
   - `OpenAiCompatibleConnectivityTester`：GET `{endpoint}/models`，只看状态码不看正文；401/403 → `CREDENTIAL_REJECTED`，超时 → `UPSTREAM_TIMEOUT`，其余 → `UPSTREAM_ERROR`。
   - `ModelConnectivityService`：解密 → 探测 → 写 `CONNECTIVITY_CHECK` 使用记录；使用记录写入失败不掩盖探测结果。
   - `ModelUsageService`：UUID correlationId，成功/失败两条追加路径，日志只记安全字段。

4. **语言任务路由接入伙伴对话**
   - `LanguageRouter`：无规则或规则为空 → 平台；有 BYOK 规则但连接缺失/停用/无凭据 → **大声失败**（`请修复或清除路由规则`），绝不静默改扣平台钱包。
   - `OpenAiCompatibleLanguageClient`：POST `{endpoint}/chat/completions`，`stream=true`，逐行解析 SSE `data:` 帧；失败只抛带安全码的 `LanguageInvocationException`。
   - `CompanionChatService` 的 MODEL 档重构为共享 `assembleTurns`（预算守卫不变）：平台路径仍走 Spring AI DashScope，BYOK 路径走网关客户端；两条路径都写 `LANGUAGE_AGENT` 使用记录（BYOK 记用户连接，平台记 dashscope/qwen-max）。
   - 控制器把 `LanguageInvocationException` 映射为可读错误文案（凭据被拒/超时/上游不可用）。

5. **控制中心 API 与前端**
   - `/api/model/credentials`（增删查）、`/api/model/connections`（增删查 + enable/disable/rotate-credential/test）、`/api/model/routing/{task}`（PUT/DELETE）、`/api/model/usage`。
   - 所有响应视图永不含明文或密文；明文 API Key 只出现在创建/轮换请求体中一次。
   - 前端 `/model-gateway`：保险库、连接、任务路由、使用记录四区；`connectivity-stub` 开关让 E2E 探测不发真实外网请求。

## 安全红线（务必保持）

- **BYOK 失败不静默回退**：路由决定在调用前做出，BYOK 路径失败只报安全码。
- **凭据三不**：不落明文、不回显（连密文也不回显）、不进日志。
- **端点白名单**：SSRF 第一道防线；新增供应商需同步扩白名单与枚举。
- **主密钥**：生产必须提供 `MODEL_CREDENTIAL_MASTER_KEY`；轮换主密钥需要全量重加密迁移（未做，Q3 后续）。

## 验收与已知边界

- 后端切片内测试全绿；完整 `mvn verify`（469 测试 + JaCoCo 门禁含 domain.airuntime）与 E2E 4/4 在切片收口时通过。
- 已知边界（Q3 后续）：凭据密文无轮换工具；使用记录无 Token 用量（当前只记成功/失败）；图像创作任务尚未接网关；MCP 工具注册与平台试用额度账本未开始；能力画像来自平台静态认知表，供应商变更需评审更新。
- 演示档（chatPolicy=DEMO_ONLY）不发起任何模型调用，路由规则只在 MODEL 档生效。

## 第二个切片：视觉接入网关 + 能力画像（提交 a4b3dc5 / 29de8c7 / 80487b0）

1. **能力画像**（a4b3dc5）：`model_capability_profile` 追加式快照表（Liquibase 20260814-36/37 + 两份 sharding + 迁移测试）；`StaticModelCapabilityRegistry` 平台静态认知表（DeepSeek 语言 / Qwen 视觉 / gpt-image-2 设计默认），未知组合一律 `unknown()`（全不支持，绝不猜能力）；连接测试成功时 `ModelConnectivityService` 写快照，快照失败不掩盖探测结果。
2. **视觉接入网关**（29de8c7）：`VisionRouter`（VISION_UNDERSTANDING 路由，BYOK 优先、平台回退、坏路由大声失败、能力画像视觉门禁）；`RoutedVisualObservationProvider`（平台默认走既有 DashScope 路径行为不变；BYOK 复用同一 OpenAI 兼容客户端按调用实例化）；`VisualObservationProvider` 接口升级为 `observe(content, subjectId)` 返回 `VisualObservationResult`（候选 + 真实来源），消除并发下"最后一次调用"状态错乱；语言/视觉共用 `ModelRouteDecision`；两条视觉路径都写 `VISION_UNDERSTANDING` 使用记录（BYOK/PLATFORM 正确分账）。
3. **控制中心展示**（80487b0）：`GET /api/model/connections/{id}/capability`（归属校验 + 安全视图）；前端探测成功后展示"支持：文本 · 工具调用 · 结构化输出 · 上下文 64000"能力徽章，未知能力不展示；E2E 断言 DeepSeek 语言连接不展示视觉能力。

## 第三个切片：图像创作路由 + MCP 白名单（提交 02dd225 / eb1d99b / 1526fbe）

1. **图像创作路由**（02dd225）：`ImageRouter`（imageGeneration 能力门禁，BYOK 优先；平台账本上线前平台路由大声失败）；`OpenAiCompatibleImageClient`（POST {endpoint}/images/generations，解析 url/b64_json，响应 1 MiB 上限，401/403→CREDENTIAL_REJECTED）；`ImageCreationService`（提示词 1-2000 字无控制字符、尺寸白名单 1024x1024/1536x1024/1024x1536/auto，成功/失败都写 IMAGE_CREATION 使用记录）；`ModelInvocationException` 通用化；`ByokConnectionResolver` 三个路由器共享同一套坏路由大声失败语义，语言路由补齐 text 能力门禁。
2. **MCP 白名单**（eb1d99b/1526fbe）：`mcp_connection`（code 唯一）+ `mcp_tool_whitelist`（(connectionId,toolName) 唯一 + revision CAS）；`DbMcpToolAccessDecider` fail-closed（服务缺失/停用、工具未入白名单或停用一律拒绝）；`RefreshableMcpToolProvider` 工具注册按白名单过滤（平台审核服务代码 mxai-mcp-server），任何白名单写操作后立即使工具缓存失效；`McpController` 全部端点 `@AuthCheck(mustRole=admin)`；前端管理区仅 `userStore.isAdmin` 可见；E2E 新增管理员种子用户与 mcp-admin 故事线。任意 MCP URL 仍不开放——端点只由平台管理员登记，且必须是纯净 HTTPS URL。

## 第四个切片：平台试用账本 + Q4 图片故事草稿（提交 a47ea9e / 7545917 / 66d2b96 / a4cee57）

1. **平台试用额度账本**（a47ea9e）：`platform_trial_ledger` 每主体一行（subjectId 唯一），不变量 balance≥reserved（可用额度永不为负）；reserve/settle/release CAS 重试（并发下永不透支），超限抛业务错误停止不自动扣费；伙伴对话平台路径预占→结算/释放闭环；`TrialController`（用户查自己 + 管理员授予）；默认试用额度 `app.model.credential.trial-default-balance`。
2. **图片故事草稿**（7545917）：`CreationTask` 状态机 + `creation_task`/`creation_lineage` 两表；`StoryDraftService`：创建前逐张图片 PICTURE_VIEW 授权校验、幂等键唯一去重；大纲/草稿由语言路由生成（平台走试用账本 2/3 单位，BYOK 免费且失败不静默回退）；提示词只含图片数量与伙伴生成的大纲，不含用户原文；30 分钟确认超时惰性转 EXPIRED；伙伴页新增「图片故事」面板；E2E 通过 language-stub（@Primary ChatModel + 调用桩）全离线跑通大纲→草稿→保存闭环。

## 第五个切片：表情草稿（提交 cf438e2 / 65c625f）

复用创作任务管线（授权复核/语言路由/试用账本/血缘），新增 `creation_candidate` 追加表（(taskId, seq) 唯一）与 `CreationTask.selectDraft` 转移；`EmojiDraftService` 从授权图片生成文字版表情候选（安全纯文本过滤：无控制字符/链接、≤200 字、上限 8 条，不依赖图像模型），用户选中其一保存为文本作品；`CreationServiceSupport` 抽取故事/表情共享支撑（执行前 PICTURE_VIEW 复核、分类落地线索、CAS 转移、30 分钟确认超时惰性过期）；伙伴页新增「表情草稿」面板（单选来源图片 + 候选单选组 + 选中保存）；列表按玩法种类过滤。

## 独立审查与修复（第五轮审查）

审查结论：P0 无；P1×1；P2×7，已全部修复并回归测试：

- **P1**：坏 BYOK 路由在事务内抛出会回滚用户消息与额度。修复：MODEL 档路由决定移到额度预占与消息落库之前（`CompanionChatService.chat`），坏路由大声失败但不吞消息不耗额度（新增回归测试）。
- **P2-1**：`LanguageRouteDecision` 隐式 toString 打印明文密钥。修复：显式遮蔽 apiKey 为 `***`。
- **P2-2**：白名单忽略端口、发送密钥前不复查白名单。修复：只放行 443/未声明端口；`LanguageRouter.decide` 与 `ModelConnectivityService.testConnection` 在出站前复查端点。
- **P2-3**：请求超时只覆盖到响应头，SSE 停滞会悬挂。修复：流级逐帧空闲超时（`Flux.timeout`）+ `TimeoutException` 映射 `UPSTREAM_TIMEOUT`（新增停滞桩测试）。
- **P2-4**：路由 upsert 并发首写输者目标被静默丢弃。修复：赢家行基础上再走一次 CAS 覆盖（新增测试）。
- **P2-5**：轮换 CAS 冲突产生孤儿凭据；绑定不校验供应商匹配。修复：冲突后基于最新版本重试一次；`requireOwnedCredential` 增加供应商匹配校验。
- **P2-6**：路由下拉无无障碍名称。修复：`aria-label`。
- **P2-7**：端点带 query/fragment 导致探测 URL 拼接错乱。修复：领域层拒绝带 query/fragment 的端点。

审查确认的安全面：AES-256-GCM（随机 IV、认证标签、防篡改）正确；生产缺失主密钥 fail-closed，绝不静默用开发密钥；重定向不跟随（无重定向 SSRF）；主机后缀白名单点边界正确；`/api/model/**` 全部鉴权 + 归属校验；Long→String 序列化无前端精度丢失。
