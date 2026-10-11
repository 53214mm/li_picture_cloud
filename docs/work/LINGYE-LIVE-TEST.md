# 绫页真实模型测试准备（2026-10-10）

## 当前结论

**尚未发送真实模型请求，尚未完成人格验收。** 本轮恢复独立工作目录到远端 `nexus/mainline-r11` HEAD `c5050a5405a1c57c2e0608a793542b44159cb5a1`，有效代码基线为 `c6856a6854beb7bbb046acd2acb1d4cb97453aea`。不修改其他分支，不部署、合并、创建 PR，不使用新的立绘草案；继续使用原运行时立绘。

准备的是服务层真实模型探针，不是完整 HTTP、浏览器或生产验收。默认 dry-run 不读取任何真实 key、不建立网络客户端或发送请求；用户在受支持的云桌面终端完成安全交接之前，live 保持未执行。

## 为什么不直接给现有应用 export key

现有普通聊天链路：`CompanionChatService.chat` → `CompanionChatModeService` → `LanguageRouter` → `ByokConnectionResolver` → 来源权限/相关记忆/人格/历史装配 → `LanguageModelInvoker.stream`。

- `ByokConnectionResolver` 从所属用户的凭据库解密，`DEEPSEEK_API_KEY` 不是现有用户 BYOK 路由的自动配置入口
- 普通 BYOK `stream` 不传 `max_tokens`；只有 `streamBounded` 有生成上限，因此原 HTTP fixture 不提供本轮要求的普通聊天硬输出预算
- 无用户路由时可能选择平台；本轮固定用户 BYOK，禁止平台回退
- 现有快速连接包含真实连接探测和凭据持久化，不能把它说成纯临时配置
- 原 API E2E 固定 DEMO/stub；它可测业务，但不能证明真实人格自然度

本轮测试专用装配保留实际聊天服务、实际记忆守卫与相关筛选、实际上下文组装、实际语言路由和原 OpenAI-compatible SSE 客户端；仅用内存合成仓储/权限 fixture 替代外部业务系统、以匿名 stdin 管道中的一次性凭据替代持久凭据解密，并通过测试专用适配器启用有界调用。不会修改生产普通聊天行为。

## 安全交接与执行环境

昨晚 `LINGYE-CLOUD-BROWSER.md` 的证据依然成立：同一 shell 编排可达自己的 localhost，但另一 exec/管理式浏览器无法访问；没有通过安全参数、隧道、端口转发或代理绕过。

本轮另外经真实 GUI 核实：dot 云桌面的原生 Xfce Terminal 能列出本次恢复的同一仓库路径。这只证明共享文件可见，不证明 localhost 共享、已有环境变量继承或 DeepSeek 网络可达。

受支持的最小路径是从该用户可操作的原生云终端启动 `scripts/lingye-live-test.py --live`：

1. 检查已编译测试类与本地依赖，并先完成无凭据 dry-run
2. 在同一个目标 JVM 运行配置下，显式执行无认证网络 preflight；不向生成接口发请求
3. 成功后才展示供应商、模型、请求和 token 上限，以及计费/取消说明
4. 用户亲自确认并在隐藏输入提示中输入临时 key，之后由同一父进程通过匿名 stdin 管道交给精确的测试 JVM；不使用环境变量、命令参数、文件或网络 relay，不让用户在另一个 shell 先 export，不将 key 发在聊天中
5. 只报告是否配置，不回显 key；不写凭据库、文件、repo、命令历史或报告。测试完成后用户撤销临时 key

当前不能把“终端可见”当作用户已经配置或 live 已成功；最终同终端网络预检为 CONNECT_FAILURE，live 仍被阻止。若 dry-run、网络或隐藏输入不支持，入口在 key 输入前失败关闭；不改安全/网络配置。

## 固定供应商契约

2026-10-10 按 DeepSeek 官方文档核对：

- `https://api.deepseek.com/chat/completions`
- `deepseek-flash`，当前文档对应 DeepSeek-V4.1-Flash
- `thinking: {type: "disabled"}`，显式关闭默认思考模式
- `max_tokens: 256`，流式输出，最多 4 个固定合成测试请求，串行、无自动重试、无模型工具循环、无失败修复调用、无平台回退
- token 上限不是精确货币保证；已发出的请求可能在本地取消后继续计费。实际用量只记供应商返回的安全数字，不据空白用量推算零费用

来源：[官方首次调用](https://api-docs.deepseek.com/)、[Chat Completions API](https://api-docs.deepseek.com/api/create-chat-completion/)、[模型与价格](https://api-docs.deepseek.com/quick_start/pricing/)。不能沿用旧 `deepseek-chat` 名称假定当前兼容。

## 预备测试范围

合成对话覆盖吐槽接梗、用户指出 AI 味后的历史连续性、有记忆时无关问候不强行提图、明确对象的相关来源召回。每个场景记录合成输入/回复、人格版本、固定供应商/模型、请求数、首字与完成时延，以及供应商确实返回的用量/完成原因。只有非空回复和显式 `finish_reason=stop` 才算完整传输；token 截断、异常结束、缺完成标记或空白均为 incomplete，不算人格通过。安全 JSON 记录原子写入独立 `target/lingye-live-report-<UUID>.json`，不保存原始 HTTP 帧、错误正文、headers、凭据或完整日志。首次请求异常中止整批，不发送剩余场景。

记忆记录全为合成，测试前没有读取用户私有图库、聊天、生产记忆或旧凭据。两条记忆的来源权限也是受控 fixture，不等价于真实图库授权集成。历史仅在指定连续场景保留，跨场景隔离，避免先前提图污染无关问候。

人工人格验收标准仍使用 `LINGYE-ACCEPTANCE.md`，不靠关键词或某句模板宣称通过。未覆盖全量多供应商质量、视觉理解、来源化长期记忆全治理、纠正/忘记跨历史重置、Spring 事务、HTTP 登录、凭据库、浏览器布局或生产费用准确性。

站内主动保持原 DEMO 与默认关闭，运行时原立绘不变。

## 本轮验证记录

- 恢复 clone：首次完整 clone 遇 HTTP 502；一次 depth-1 同分支恢复成功，精确 HEAD 校验、初始工作区干净
- Python 启动器语法编译通过；准备早期依赖尚未就绪时实测退出码 2 / `PREPARE_COMPILED_TEST_RUNTIME_FIRST`，没有请求 key
- 官方 SHA 校验的完整 Temurin JDK21.0.12.1+1、Maven3.9.15 已恢复到独立 sibling tool-cache；依赖从空缓存恢复完成，最终编译和干跑结果见下节
- 独立安全 review：端点/模型/token/思考关闭/管道/默认无秘密读取静态检查通过；Python AST 检查，以及模拟和真实无秘密睡眠子进程的 SIGTERM 清理验证通过。最终编译后已独立复跑，未发现剩余阻断代码问题；同终端网络预检后来确认 CONNECT_FAILURE，真实调用保持阻止
- 后端 Java 干跑、最终定向回归和独立安全 review 已完成；不套用昨晚 849 项/40 HTTP 等旧结果
- 真实模型、用户终端隐藏输入、实际 GUI/HTTP：未执行

### 最终离线准备结果（2026-10-10 00:57 UTC）

- 最新生产/测试编译成功：544 个 main 源文件、164 个 test 源文件；测试 classpath 403 项，路径全部可读
- 最终定向 JUnit **70/70 通过，0 失败/错误/跳过**（7 个套件，含本轮新增探针 11 项）：`LingyeLiveChatProbeTest,BoundedDeepSeekProbeClientTest,CompanionChatServiceTest,CompanionChatContextAssemblerTest,CompanionMemoryServiceTest,RelevantCompanionMemoriesTest,OpenAiCompatibleLanguageClientTest`
- 最终真实启动器 dry-run 退出码 0：4 场景完成、历史条数 2/4/2/2、`providerRequests=0`；报告 `target/lingye-live-report-92a7a30c-2b45-4019-b7b9-a82e07cb9038.json`
- 无 TTY 的 `--live` 实测拒绝，未知参数拒绝；没有读取 key 或发送模型请求
- 独立 reviewer 另外复跑编译后 dry-run 成功。静态安全 review 加强了 standalone main 的迟到 Reactor 异常/后台线程异常处理，只输出固定安全代码、不输出原始 cause；最后一次复跑覆盖最终源码
- 原有客户端取消测试会在 JUnit 日志中产生 fixture 异常栈，但测试通过；standalone main 安装独立脱敏处理，不把测试日志误作真实 provider 错误
- 没有重跑全量 `clean verify`、前端/HTTP/浏览器矩阵或真实供应商，70 项只代表上述定向范围
- 验证日志位于 sibling `nexus-tool-cache/logs/lingye-final-focused-tests.log`、`lingye-final-dryrun.log`；无秘密的离线工具入口 `../nexus-tool-cache/mvn-offline.sh`

### 最小启动命令（只从已核实的云桌面原生终端运行）

先由操作者运行无认证网络预检，不会提示凭据：

```bash
/usr/bin/python3 -I /workspace/scratch/058c18b9b398/nexus-mainline-r11/scripts/lingye-live-test.py --preflight-network
```

实际网络请求是同一 Java HttpClient/trust 配置下的 `GET https://api.deepseek.com/models`，不含 Authorization，body 丢弃。只接受 HTTP 200/401；所有其他状态在 key 提示前阻止流程。`modelAuthVerified=false`，不能把无认证 401 解释成 key 有效或模型完成过调用。不换 Python 网络栈测试后冒充 JVM 可连通。

网络预检与最终 review 通过、用户准备亲自操作后，唯一交接入口：

```bash
/usr/bin/python3 -I /workspace/scratch/058c18b9b398/nexus-mainline-r11/scripts/lingye-live-test.py --live
```

`-I` 隔离 Python 环境注入；脚本清空子 JVM 非必要环境，不继承 JVM debug/agent/proxy覆盖。先无凭据 dry-run + 预检，再要求用户输入 `RUN` 并亲自隐藏输入临时 key；输入不会进入 argv/env，匿名 pipe 仅交给精确子 JVM。收到 SIGINT/SIGTERM/SIGHUP 或本地超时会结束并回收自有子进程；已发送供应商请求仍可能计费。

独立、唯一报告文件在终端显示，其 JSON 只含白名单元数据、合成场景结果、安全错误码及计数。`invocationAttempts` 与 `providerRequests` 分开；dry-run 的 providerRequests 始终为零。真实成功必须另行阅读 live=true 的报告与用户体验反馈，不能复用这里的离线输出。

截至本记录写入时：**用户尚未完成隐藏输入，真实模型请求仍为零；云桌面公网预检结果由操作者在同一终端核验后另记。**


## 最终诊断与交付状态（2026-10-10 01:02 UTC）

1. 原云桌面终端首次同传输预检失败，仅记录通用 `PROBE_FAILED`，无凭据输入；其信息不足以判断原因
2. 在明确授权下添加固定异常类型诊断，**最终 72/72 定向测试通过，0 失败/错误/跳过**，其中13个探针用例；不读取/输出异常 message、原始 cause 或 stacktrace
3. 最终编译后启动器干跑再次4场景通过，`providerRequests=0`；报告 `target/lingye-live-report-d04ddebf-225f-4c5b-8d50-ae106ca1186f.json`。最终日志为 sibling `nexus-tool-cache/logs/lingye-diagnostic-final-tests.log` 和 `lingye-diagnostic-final-dryrun.log`
4. 同一原GUI终端仅做一次无认证诊断复核，结果 `preflight=failed`、`code=CONNECT_FAILURE`、`modelAuthVerified=false`、`allowedToContinue=false`，**没有收到HTTP状态，没有提示或读取 key**；报告 `target/lingye-live-report-dce52101-f549-4ebb-940c-443c4918b3c9.json`
5. 因此当前最小阻塞是此 JVM 到官方模型服务的网络连接；不能据 `ConnectException` 推断是凭据无效、TLS证书错误、DNS失败、明确权限禁止或供应商账户故障。没有进一步探测、换代理、修改信任/TLS、安全配置、隧道或网络 relay；没有擅自迁移到用户电脑

独立 reviewer 已通过最终脚手架与固定分类的静态/离线安全审核。脚手架可复用并按授权在唯一工作分支保留；**真实模型人格仍未测，用户秘密尚未交接，生成请求为零**。后续若用户选择另一已授权环境，需要先在那个实际执行环境重新检查工具链、无凭据干跑和同 JVM 网络预检，再由用户亲自输入新的临时 key。不能把当前云端的共享文件可见性当作另一环境的连接或凭据证据。

提交范围仅为启动器、4个测试源文件、本验收记录及STATE/DECISIONS；生成 target 报告、依赖、日志均不提交。原版立绘、生产普通聊天代码、站内主动默认开关、其他分支不变；未 merge、PR 或部署。
