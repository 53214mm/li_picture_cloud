# 绫页试演台：复用当前模型连接

## 范围与默认行为

本轮只补试演台手动真实模型入口，不重做主动行为。普通聊天设置保持原样；站内悬浮伙伴仍用 DEMO。进入试演台、读取或刷新状态、切换模式均不发起模型请求。默认仍为本地确定性模拟，所有试演默认关闭。

真实试演复用当前账号已有的 `LANGUAGE_AGENT` BYOK 路由。页面没有密钥输入，也不会调用模型连接页的 quick-use（后者会探测供应商并修改聊天路由）。只有用户明确选择真实模式、开启本页试演、再手动点场景，才可能产生一次收费调用。

## 本地启用步骤（由用户操作）

1. 使用原有已配置、已启用、具备文本能力的当前语言模型连接。没有连接或当前走平台时，真实试演不可用；页面只提供前往模型连接设置的入口，不自动替换路由。
2. 在本地后端进程环境设置 `COMPANION_PRESENCE_REAL_ENABLED=true`，然后由用户重启该本地后端。也可在本地配置文件写 `app.companion.presence.real-enabled: true`。仅修改 `.env` 不保证直接启动的 Java 进程会加载它；请确保变量实际传入后端。总功能开关 `app.companion.enabled`、`app.companion.presence.enabled` 也必须开启，前端需 `VITE_COMPANION_ENABLED=true`。
   - 本地 PowerShell 示例：`$env:COMPANION_PRESENCE_REAL_ENABLED="true"`，再在同一终端用原启动方式启动后端。POSIX 示例：在原后端启动命令前加 `COMPANION_PRESENCE_REAL_ENABLED=true`。不需要提供或重新输入模型密钥。
   - 本地 Docker Compose：此变量已显式传入 backend，默认 `false`。用户可在本地 `.env` 改为 `true`，再自行运行 `docker compose up -d --build --no-deps backend` 应用本地后端变化；普通 `restart` 不会更新已有容器的环境。不要对生产项目执行本地验收命令。前端也需使用这轮新版构建。
3. 打开试演台并刷新连接状态，确认显示当前供应商/模型。状态只检查本地配置和归属、启停、凭据绑定、端点白名单、文本能力，不解密密钥、不联系供应商，因此不等同于网络、凭据有效性或模型质量验收。
4. 选择真实模型，阅读费用提示，开启本页试演，再手动点一个场景。切换模式会关闭本页试演，需重新开启；离页、隐藏、暂停、账号切换会废弃迟到结果。
5. 验收完关闭试演；需要撤销服务端准入时，将环境变量改回 `false` 并由用户重启本地后端。

本轮未启用任何实际环境开关，未读写真实凭据，未调用收费模型、重启日常服务或部署。

## 接口与安全边界

`GET /companion/presence/status` 从登录会话取得账号，返回 `enabled`、`realEnabled`、`available`、`reason` 与安全 `connection` 摘要（仅 `provider`、`modelCode`）。原因码：`READY`、`DISABLED`、`REAL_DISABLED`、`BYOK_REQUIRED`、`UNAVAILABLE`。不返回端点、凭据引用、密文、密钥、原始异常或其他账号数据。

状态是瞬时配置快照。真正事件派发仍重新解析当前路由；连接被删/停用/改成平台会安全失败，绝不平台回退。沿用每日最多 24 次 BYOK 尝试、本会话 1–12 次、15 分钟过期、低/普通 45/15 秒服务端间隔、本地 5 秒间隔、单次 160 token 和 8 秒超时。失败可能消耗尝试预算；取消或关闭只能停止接收/阻止尚未派发的请求，不能保证撤回已经发送给供应商的收费调用。无自动重试、无自动连续调用。

## 验证

- 后端定向 32/32 通过，覆盖安全摘要/鉴权、门控、缺失/异账号/停用/无凭据/端点失效/无文本能力连接、状态后切换路由，以及原有配额/冷却/取消/过期逻辑。
- 完整 offline `clean verify`：920 项，916 通过、0 失败/错误、4 既有可选跳过（3 Redis opt-in、1 真实视觉 smoke）；原 JaCoCo 门和 JAR package 通过。使用隔离 H2/test 配置，没有生产或用户数据库操作。
- 前端最终 Node 22.23.3：466/466，严格 lint 通过；包含实际编译 Vue SFC 交互、状态/账号/隐藏页面/卸载竞态、初始普通聊天正在响应、模式切换/相关性/迟到结果。功能开/关两组 production build 与 bundle budget 通过，最大 chunk 390,035 bytes。
- 独立 review 首次发现初始聊天进行中被账号初始化重置覆盖；已恢复聊天优先和页面可见性守卫，并补回归，复核无剩余阻断。

首次定向执行因当前云环境不支持 Mockito 运行时自附加而全部初始化失败；改用缓存中官方 Mockito 启动 agent 后，定向与完整验证通过。没有修改系统安全配置或项目测试门槛。完整复跑命令（缓存工具路径按实际环境调整）：

```sh
JAVA_TOOL_OPTIONS=-javaagent:/workspace/scratch/058c18b9b398/nexus-tool-cache/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar \
../nexus-tool-cache/mvn-offline.sh \
-Dspring.config.location=classpath:/application-test.yaml \
-Dmybatis-plus.configuration.map-underscore-to-camel-case=false \
-Dmybatis-plus.global-config.db-config.logic-delete-field=isDeleted \
-Dmybatis-plus.global-config.db-config.logic-delete-value=1 \
-Dmybatis-plus.global-config.db-config.logic-not-delete-value=0 clean verify
```

- Chromium 专项 9 项已收集，但浏览器进程启动前即被环境拒绝 IPC socket（`Operation not permitted`），标准工具批准的执行重试仍失败；没有运行到网页断言，也没有可验证截图。此前 Playwright 管理浏览器下载也返回不完整 ZIP。未增加关闭沙箱/放宽安全参数，停止浏览器尝试。实际编译 SFC 的 466 项 Node 结果不冒称浏览器验收。
- 最终检查日志保留在工作区之外 `nexus-tool-cache/logs/rehearsal-byok/`，不提交构建缓存或日志。

真实模型、实际生产/用户数据库、MySQL/pgvector 实库与人工人格验收不属于离线测试的通过范围。本轮未跑独立真实 HTTP 全故事线；控制器通过 MockMvc 合约验证。
