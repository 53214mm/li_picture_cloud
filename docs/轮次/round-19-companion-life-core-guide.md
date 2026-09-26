# 图像伙伴生命核心与演示喂养指南

## 这次交付了什么

本轮加入了第一版“图像生命体”闭环。每个登录主体最多拥有一个伙伴，可以在 `/companion` 唤醒它，再从本人最早创建的私有空间中选择一张有权查看的图片进行喂养。

一次成功喂养会由后端统一计算并保存生命经验、等级、生命阶段、五条人格倾向、技能熟练度和成长历史。页面只展示服务端结果，不自行计算成长。重复使用同一个请求键只会重放第一次结果；同一图片换新键再次喂养只获得少量熟悉感。

## 图片营养与内容理解边界

当前支持三条请求策略。策略只说明一次喂养<strong>允许</strong>使用什么路径；每一条成长记录都会保存实际来源，页面据此显示是否真的理解了图片内容。

- `METADATA_ONLY`：默认策略。读取图片尺寸、格式、大小，以及“是否填写简介/分类”这些状态；不保存或传播原始文字，不读取像素，不调用视觉模型。
- `DEMO_ONLY`：测试和 E2E 策略。只根据图片 ID 选择固定档案，不读取任何图片信息。
- `VISUAL_WITH_METADATA_FALLBACK`：生产可选策略。仅在用户明确喂养、图片仍有 `PICTURE_VIEW` 权限且通过 COS 受控读取后，才将 JPEG、PNG 或 WEBP 像素发送给配置的视觉 Provider。默认每位用户每天最多 10 次；视觉服务超时、限流、不可用或返回无效结构时会明确降级为元数据营养，仍占用已预留的当日次数。凭证错误、权限问题和额度耗尽不会伪装成降级。

视觉出站前会再次核对图片权限、资源版本和对象绑定。喂养不会移动、改名、删除或重标记来源图片，也不会存储图片字节、Data URL、原始模型回答或用户原始描述。

所有 feeding run 和成长历史都会持久化请求策略与实际来源：

- 视觉成功：显示 `Qwen 视觉营养 · 已分析图片内容`，并记录 Provider、模型、置信度和提示词/结构版本。
- 元数据或演示：明确显示“未读取图片像素”或“未进行内容理解”。
- 视觉降级：显示“视觉服务暂不可用，本次使用图片元数据营养”，不会把它宣传为内容理解。

## 本地体验

1. 启动 MySQL 8 和 Redis 7。
2. 配置数据库、Redis、COS 与已有 AI 环境变量。
3. 启动后端：`./mvnw spring-boot:run`。
4. 进入 `li-picture-cloud-frontend`，执行 `npm ci` 和 `npm run dev`。
5. 登录后打开 `/companion`，点击“唤醒我的伙伴”。
6. 如果没有私有空间或图片，先创建空间并上传图片。
7. 选择一张图片并点击“喂给伙伴”。发生断网、超时或 5xx 时，页面会保留同一幂等键并显示“重试这次喂养”；切换图片会创建新请求。

喂养只轻引用来源图片 ID，不移动、不改名、不重标记、不删除来源图片。

## 配置开关

| 变量 | 默认值 | 作用 |
|---|---:|---|
| `COMPANION_ENABLED` | `false`（生产） | 注册伙伴后端服务与接口 |
| `COMPANION_FEEDING_ENABLED` | `false`（生产） | 允许执行喂养 |
| `COMPANION_PROCESSING_TIMEOUT` | `5m` | PROCESSING run 可被安全接管前的超时 |
| `COMPANION_NUTRITION_POLICY` | `METADATA_ONLY` | 请求策略：`METADATA_ONLY`、`DEMO_ONLY` 或 `VISUAL_WITH_METADATA_FALLBACK` |
| `COMPANION_VISION_ENDPOINT` | DashScope compatible endpoint | 视觉 Provider 的 HTTPS Chat Completions endpoint |
| `COMPANION_VISION_PROVIDER` | `dashscope` | 审计中使用的视觉 Provider 标识 |
| `COMPANION_VISION_MODEL` | `qwen3.6-flash` | 视觉模型标识 |
| `COMPANION_VISION_DAILY_LIMIT` | `10` | 每个用户每天预占的视觉调用次数上限 |
| `COMPANION_VISION_TIMEOUT` | `20s` | 一次视觉请求的总超时 |
| `COMPANION_VISION_MAX_BYTES` | `8MB` | 单张可送模图片的大小上限 |
| `VITE_COMPANION_ENABLED` | `false` | 构建时加入前端路由、导航和懒加载页面 |

生产环境必须同时开启前端与后端开关。生产 profile 会要求明确提供请求策略、视觉 Provider/模型、日额度、超时、最大字节数和 `DASHSCOPE_API_KEY`；不要依赖本地默认值。`VITE_COMPANION_ENABLED` 是构建时变量，修改后必须重新构建 web 镜像；只重启容器不会改变前端产物。默认生产构建和 Compose 均关闭此功能。

轮换 `DASHSCOPE_API_KEY` 时：先在密钥管理系统中创建新 key，更新运行环境并滚动重启 backend，确认健康检查和一次受控喂养成功后，再撤销旧 key。不要把 key 写进 `.env.example`、图片描述、浏览器控制台或成长记录。

真实 Provider 冒烟测试默认跳过，只允许操作者在当前终端临时提供 key。它只发送仓库内的公开素材，不打印模型原文或凭证：

```powershell
$env:COMPANION_VISION_LIVE_TEST = 'true'
$env:DASHSCOPE_API_KEY = '由操作者在当前终端临时提供'
.\scripts\mvnw-java21.ps1 "-Dtest=DashScopeVisionLiveSmokeTest" test
Remove-Item Env:\DASHSCOPE_API_KEY
Remove-Item Env:\COMPANION_VISION_LIVE_TEST
```

自动化代理和 CI 不得代填、读取或回显这个 key；CI 只运行本地 HTTP stub。

## 数据与一致性

伙伴核心使用四张表：

- `companion`：每个主体一行当前快照，使用 `revision` 做乐观锁。
- `companion_skill`：按伙伴和技能码保存技能经验。
- `companion_feed_run`：保存请求键、输入指纹、状态、失败摘要、请求策略和请求的 Provider/模型。
- `companion_growth_record`：只追加的成长事实，保存来源图片 ID、变化量、规则版本、实际来源（Provider/模型/置信度/降级原因）和结果快照。

唯一键保护“一主体一个伙伴”和“同伙伴同幂等键一个 run”。伙伴、技能、成长记录和 run 完成状态在一个事务中同成同败；同图并发先锁伙伴再计算。规则层还限制每日经验、单图影响、单次人格变化，并对重复图片 sharply diminish。日志与数据库只保留安全错误码/消息及 correlation ID，不保存 token、供应商响应正文或堆栈。

## 分片模式上线顺序

ShardingSphere 启动时会读取物理单表元数据，因此冷库必须先迁移、再启动分片数据源。

先设置 `MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DATABASE`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`，然后：

```powershell
powershell -File scripts/migrate-companion-physical.ps1
```

Linux 可通过直连物理 MySQL 的 Liquibase Maven 配置运行：

```bash
./mvnw -q liquibase:update
```

只有迁移退出码为 `0`，且 `DATABASECHANGELOG` 中存在 `20260811-01` 到 `20260811-07` 后，才能启动 `sharding-static` 或 `sharding-dynamic`。禁止把 Liquibase 指向 `jdbc:shardingsphere:`。

## 验证命令

后端聚焦与全量验证：

```powershell
.\scripts\mvnw-java21.ps1 "-Dtest=CompanionSchemaMigrationTest,ShardingModeConfigurationTest,CompanionSingleTableRoutingIntegrationTest" test
.\scripts\mvnw-java21.ps1 "-Dtest=CompanionTest,FeedingRunTest,CompanionBalancePropertyTest,DomainDependencyTest" test
.\scripts\mvnw-java21.ps1 "-Dtest=CompanionPersistenceIntegrationTest,CompanionFeedingIntegrationTest,CompanionControllerTest" test
.\scripts\mvnw-java21.ps1 -B "-Dspring.profiles.active=test" verify
```

前端、开关产物和浏览器验证：

```powershell
Set-Location li-picture-cloud-frontend
npm audit --omit=dev --audit-level=high --registry=https://registry.npmjs.org
npm run lint
npm test
Remove-Item Env:\VITE_COMPANION_ENABLED -ErrorAction SilentlyContinue
npm run build
npm run check:bundle
$env:VITE_COMPANION_ENABLED = 'true'
npm run build
npm run check:bundle
npx playwright install chromium chromium-headless-shell
npm run test:e2e
```

浏览器 E2E 使用独立 H2、真实 Spring Boot HTTP 栈、真实登录会话和 Chromium；本地执行前要保证
`127.0.0.1:6379` 的 Redis 可用。无密码实例可直接运行；若本地 Redis 启用了密码，仅在当前终端设置
`REDIS_PASSWORD` 后运行测试，不要把密码写进仓库。

## 发布前人工隐私与钱包审核

每次启用真实视觉策略前，由审核人逐项签字：

1. 浏览器/服务抓包只出现发往配置 DashScope endpoint 的视觉请求。
2. 请求不包含 COS Secret、永久签名参数、对象存储管理凭证或其他用户图片。
3. 应用日志、数据库和指标不含 Data URL、原始模型 JSON、API key、图片 URL 或用户原始描述。
4. 同一用户第 11 次视觉调用在模型出站前被拦截；失败与降级不退还已经预占的次数。
5. 401/403 凭证错误不会降级；超时、429、5xx 和无效结构只按白名单降级并记录安全原因码。
6. 撤销图片或空间权限后，新的调用和旧幂等键回放都不能向无权主体返回成长结果。
7. 喂养前后来源图片的 ID、名称、URL、空间、审核状态和删除状态完全一致。

## 明确未包含

本轮没有实现长期记忆、主动行为、主动剧情、模型/MCP 控制中心、图片生成/融合、用户自带 token、平台额度、订阅/支付或极端属性剧情。现有技能码仍只是未来能力的扩展位。

## 生产发布警告

不要把元数据、演示或视觉降级公开宣传为 AI 图片理解。视觉调用已采用不可信输出校验、图片二次授权、成本硬上限和来源审计；生产发布仍需要落实凭证加密与撤销、隐私/数据保留策略、CORS/来源控制，以及真实 MySQL、Redis、对象存储和更广泛浏览器 E2E。下一阶段边界见[伙伴图片观察基础层设计](superpowers/specs/2026-08-13-companion-vision-foundation-design.md)，生产冷启动与人工签字流程见[代码复审与人工验收手册](reviews/2026-08-13-companion-life-core-review-and-manual-checklist.md)。
