# 绫页流式回复终态修复（2026-10-10）

## 范围与复现

- 唯一分支 `nexus/mainline-r11`，起点 `7a559450786b1bc8231e5cd1ebbe1d1666dc955c`；开工核实工作区干净、origin 为 `53214mm/li_picture_cloud`、远端 HEAD 相同。
- 本地 loopback HTTP 探针对当前编译实现复现：上游只发一段 `partial` 后正常断开，被当成成功；发送 `complete` + `[DONE]` + `unexpected tail`，终态之后的文字仍进入回复。前端也把未收到服务端 `event: done` 的 EOF 当完成。
- 前者会触发 BYOK 聊天的成功记录/回复落库，并可能影响同客户端的经历总结；后者可能把不完整的临时回复留作已完成显示、允许用户直接再发。不是根据旧报告假设问题仍存在。

## 修复契约

1. OpenAI-compatible BYOK 解析器识别 `[DONE]`，也接受非空字符串 `choices[0].finish_reason` 为供应商声明的终态；保留 `stop`、`length` 等厂商原因，`length` 不等于传输断开。终态帧自带的最后一段正文保留，随后内容不读取、不追加。
2. EOF 前没有以上标记，返回既有安全错误码 `UPSTREAM_ERROR`；已流出的局部正文不冒充完整成功。状态码/错误帧/超时的既有安全映射保留，无重试、平台回退或真实凭据访问。`streamBounded` 仍保留原 `max_tokens` 上限。
3. 网页端仅由服务端 `event: done` 确认完成。提前 EOF 拒绝；共享会话保留实际收到的局部文字和明确错误，要求用户显式刷新权威历史之后再发送，不自动重复这个非幂等请求。空回复不伪造伙伴台词。
4. 取消/账号重置/消费者退出继续忽略迟到回调并释放 reader；服务端取消不被补成成功。后端共享客户端使用每个响应自己的终态状态。

## 兼容性与边界

- 普通聊天、人设、原版立绘、主动 DEMO、手动试演 BYOK 门控/配额和平台试用语义不改变。
- DeepSeek 和千问 OpenAI-compatible 文档均提供 `[DONE]`；额外接受 `finish_reason`，兼容仅发送明确结束原因后关闭的实现。
  - https://api-docs.deepseek.com/api/create-chat-completion/
  - https://help.aliyun.com/en/model-studio/stream
- 平台模型走 Spring AI Alibaba 1.1.2.2 的 `DashScopeChatModel` / `DashScopeApi`，并非本次 BYOK 解析器。检查现有依赖可见其已有 `[DONE]` 的 `takeUntil`；本次没有替换/改写该依赖，不能声称覆盖平台上游提前 EOF 的完整验收。
- 已有真实 DeepSeek 普通聊天和千问识图的用户验收不自动代表本补丁已经通过真实供应商回归。本轮只用合成凭据/本地 fixture，不调用收费模型、不操作用户/生产数据；真实模型重新试玩、平台千问普通聊天/试演与人工人格效果仍分别待验收。
- 不重试已经确认的云端 Chromium IPC 限制；本轮 Node/SFC 验证不是浏览器通过。不改变 TLS、系统网络、安全参数、其他分支、main、PR 或部署。

## 验证记录

- 本地 HTTP 红证据：修复前 partial EOF 成功、DONE 后追加文字；修复后新增错误/终态/取消回归通过。
- 后端定向 49/49，0 失败/错误/跳过。解析器 15 项包括空 EOF、部分 EOF、DONE 后坏帧、finish_reason 最后正文、bounded length、取消、上游连接保持打开时两个明确终态及时完成；聊天服务验证部分错误仅保留用户消息、不落回复、不记成功、不动平台账本。
- 完整 offline `clean verify`：929 项，925 通过、4 既有可选跳过，0 失败/错误；JaCoCo 原门槛和 JAR package 通过。4 跳过为 3 Redis opt-in 与 1 真实视觉 smoke。合成 H2/test 数据，无生产/用户数据操作。
- 前端最终 Node 22.23.3：470/470；严格 lint 通过；Companion 开/关两组 production build 与 bundle budget 通过，最大 chunk 390,035 bytes；确认关闭时 CompanionView lazy chunk 不存在。
- 独立 review 无阻断：核对供应商终态兼容、取消/资源释放、持久化/配额、bounded 路径；独立复跑前端流/会话 31/31。
- 失败探索：首次新增服务测试缺少 Mockito 静态引用，编译失败后已修复；最终定向与完整测试均使用此前已验证的官方 Mockito 启动 agent。首次定向前端是默认 Node24，最终全套已重跑正确 Node22，不以首跑代替项目要求。
- 本轮未运行浏览器、独立完整 HTTP 故事线、真实供应商/平台模型、MySQL/pgvector 实库或人工人格验收。

复跑完整后端命令与上次 BYOK 相同，见 `LINGYE-REHEARSAL-BYOK.md`；使用 `JAVA_TOOL_OPTIONS` 官方 Mockito agent、隔离 `application-test.yaml` 与已有 MyBatis 测试覆盖。日志保留在工作区外 `nexus-tool-cache/logs/stream-termination/`，不提交缓存或产物。

## 发布检查点

- 原远端父提交：`7a559450786b1bc8231e5cd1ebbe1d1666dc955c`。
- 本地已测源提交：`f3f1c0d575ad4d14cbee29a623455c0b46e2bfa4`。
- 远端有效功能提交：`4e59e36b92ddd6cae94a4d1a111990691fac3815`。
- 完全相同的完整 tree：`98a90a0f4b3c5ae9d4c3de8b998b8154baffb4a4`。

10 文件，+238/−28。现有 GitHub connector 逐 blob SHA 校验、完整 tree 精确对照；提交以核实的基线为唯一父节点，更新分支前重读 HEAD，再以 `expected_sha` + `force=false` 更新唯一目标分支。随后 git fetch 回读 HEAD、tree、父节点均匹配，干净本地分支对齐发布身份。提交身份/时间造成源与发布 commit SHA 不同，源码相同。

本节和 STATE 后续只补发布记录，不改变已经验证的功能。工作流仅匹配 main push/PR，本分支推送不自动触发 CI；本轮本地验证与无远端 CI 要区分。不创建 PR、合并 main 或部署来触发它。恢复包包含最终目标分支完整祖先历史及同 HEAD 源码快照，精确最终身份另见包内 manifest。
