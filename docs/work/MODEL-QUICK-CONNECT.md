# 模型快速连接与伙伴聊天模式（2026-10-09）

## 要解决的问题

- 原模型连接要四步：保存 API Key → 手填端点和模型编码建连接 → 启用并测试 → 在「任务模型」里绑定路由。
- 语言路由要求能力画像标记「文本」，但平台能力表只认识少数模型，新模型即使连上也会让聊天报错。
- 聊天模式（演示 / 模型）是服务端全局配置，用户没有切换入口。

## 用户流程

1. 选厂商：DeepSeek、OpenAI、Anthropic、Google、Moonshot、阿里云百炼、硅基流动、智谱（端点不在白名单的厂商不显示）。
2. 填 API Key → 平台读取 `{endpoint}/models`，去掉嵌入、语音、绘图等明显不能对话的模型，默认选中推荐模型。厂商没有目录接口时改用平台推荐模型。
3. 选模型 → 「完成连接」。平台用这个模型发一次 1 token 的对话请求；成功后在一个事务里保存凭据（加密）、创建并启用连接、写入「已验证文本」能力画像，并把语言任务路由到该连接。伙伴聊天随之切到真实模型。

验证在事务外完成，失败时不留下凭据或连接。错误只用安全错误码映射成中文说明：Key 无效、余额不足或限流、超时、模型不能对话、厂商不可用。原来的手动管理（多连接、按任务分配、调用记录、MCP）折叠在「高级设置」里，展开后才加载，接口没有变化。

## 聊天模式

伙伴对话面板（主页和抽屉共用一个状态）有「演示回复 / 真实模型」切换：

| 情况 | 真实模型用什么 |
|---|---|
| 连接了自己的模型 | 自己的模型，费用由用户的厂商账户承担 |
| 没连接，服务端 chat-policy=MODEL | 平台模型，每条消耗 1 次试用额度 |
| 没连接，服务端仅演示 | 不可选，提示去连接模型；不会动用平台额度 |
| 自己的连接已失效 | 仍选中自己的模型，发送会大声失败并提示去修复，不会静默改用平台额度 |

用户没选过时，有可用模型就默认真实模型。回复流式输出期间不能切换。Home 响应里的 `chatPolicy` 保持为服务端策略（真实 HTTP 测试据此确认不外发），用户的模式在新增的 `chatMode` 字段里。

## 实现位置

- 后端：`ModelProviderPresets`、`ModelCatalogClient` / `OpenAiCompatibleModelCatalogClient`、`ModelQuickSetupService`、`ModelQuickSetupController`（`/model/quick/*`）；`CompanionChatModeService`、`companion_chat_preference` 表（迁移 `2026-10-09-companion-chat-preference.xml`）、`GET/PUT /companion/chat/mode`。
- 模型编码允许命名空间和标签形式（如 `deepseek-ai/DeepSeek-V3`）。
- 前端：`views/ModelGatewayView.vue`（外壳）、`components/model/ModelQuickConnect.vue`、`components/model/ModelGatewayAdvanced.vue`（原页面内容）；`CompanionChatPanel` 的模式切换与 `stores/companionChat.js` 的共享模式状态。
- E2E 后端的目录替身：以 `sk-rejected` 开头的 Key 被拒，名称含 `unavailable` 的模型不能对话。

## 验证

- 后端：新增 23 项（目录客户端 7、快速连接服务 8、聊天模式 6、偏好表 H2 集成 1、能力画像 1）。全量 `mvnw test` 769 项中 765 通过、4 项按开关跳过；首跑 2 项迁移契约失败（发布清单未列新 changelog、回滚测试未覆盖新 changeSet），补上后单独重跑通过。发布清单的 changeSet 总数由 73 改为 74。
- 前端：Node 339/339，lint 0 警告，伙伴功能开/关两种构建及包体预算通过；新增 9 条模拟 API 浏览器用例（`e2e/config/model-quick-connect.config.js`），新用例发现并修复了一处抽屉复用旧主页快照、覆盖刚切换的模式的问题。既有伙伴组合回归 51/51、70/70 通过。
- `e2e/model-gateway.spec.js` 已改为先展开高级设置，并新增真实后端的快速连接故事；需要测试 Redis 6380，本次环境没有 Redis，未运行。

## 已保存连接与重复连接修复（2026-10-09）

- 当前卡片展示伙伴聊天实际使用的路由；保存并启用连接不等于已绑定语言任务。快捷页面列出已保存且启用、绑定凭据的连接，提供“验证并用于聊天”，无需重填 Key。
- 新增 `POST /model/quick/connections/{id}/use`：复用 BYOK 归属、凭据和端点白名单校验，用已保存的 Key 和模型完成最小对话验证；验证成功后重新核对连接和 Key，再在事务内写能力画像、绑定语言路由。验证失败或验证期间配置变化，不改变原路由。
- 重新输入 Key / 更换模型时创建独立连接，按当前用户已有名称生成 `DeepSeek`、`DeepSeek 2` 等名称（忽略大小写避让）。旧连接及其他任务对它的引用保留；并发名称冲突若返回另一请求的旧连接，则回滚并提示重试，不能把本次验证结果套到旧 Key / 模型上。
- 持久化回归使用独立 H2、大小写不敏感唯一键、真实加密和路由，仅替换厂商 IO。旧实现 5 项中 4 项失败，修复后扩展为 9 项通过，包含已有连接选用、重复更换、跨用户拒绝、失败保持原路由、验证期间凭据轮换。
- 本轮验证：相关后端 72/72、前端 Node 339/339、快捷连接和聊天模式 Chromium 11/11；lint、Vite 构建通过。浏览器使用模拟 API，未调用用户的真实供应商 Key。
