# 图像伙伴生命核心与演示喂养指南

## 这次交付了什么

本轮加入了第一版“图像生命体”闭环。每个登录主体最多拥有一个伙伴，可以在 `/companion` 唤醒它，再从本人最早创建的私有空间中选择一张有权查看的图片进行喂养。

一次成功喂养会由后端统一计算并保存生命经验、等级、生命阶段、五条人格倾向、技能熟练度和成长历史。页面只展示服务端结果，不自行计算成长。重复使用同一个请求键只会重放第一次结果；同一图片换新键再次喂养只获得少量熟悉感。

## 图片营养与内容理解边界

当前支持两种确定性营养模式：

- `METADATA_DETERMINISTIC`：默认模式。读取图片尺寸、格式、大小，以及“是否填写简介/分类”这些状态；不保存或传播原始文字，不读取像素，不调用视觉模型。
- `DEMO_DETERMINISTIC`：测试和 E2E 模式。只根据图片 ID 选择固定档案，不读取任何图片信息。

所有 feeding run 和成长历史都会持久化实际模式。当前两种模式均为：

- `contentUnderstood=false`

前端会按模式显示“图片元数据营养（确定性）”或“演示营养（确定性）”，并持续显示“未进行图片内容理解”。不要把当前效果宣传为 AI 看懂了图片。

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
| `COMPANION_NUTRITION_MODE` | `METADATA_DETERMINISTIC` | 图片营养模式；测试可设为 `DEMO_DETERMINISTIC` |
| `VITE_COMPANION_ENABLED` | `false` | 构建时加入前端路由、导航和懒加载页面 |

生产环境必须同时开启前端与后端开关。`VITE_COMPANION_ENABLED` 是构建时变量，修改后必须重新构建 web 镜像；只重启容器不会改变前端产物。默认生产构建和 Compose 均关闭此功能。

## 数据与一致性

伙伴核心使用四张表：

- `companion`：每个主体一行当前快照，使用 `revision` 做乐观锁。
- `companion_skill`：按伙伴和技能码保存技能经验。
- `companion_feed_run`：保存请求键、输入指纹、状态、失败摘要和原始结果引用。
- `companion_growth_record`：只追加的成长事实，保存来源图片 ID、变化量、规则版本、营养模式和结果快照。

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

浏览器 E2E 使用独立 H2、真实 Spring Boot HTTP 栈、真实登录会话和 Chromium；本地执行前要保证 `127.0.0.1:6379` 有无密码 Redis。

## 明确未包含

本轮没有实现真实视觉理解、长期记忆、主动行为、主动剧情、模型/MCP 控制中心、图片生成/融合、用户自带 token、平台额度、订阅/支付或极端属性剧情。元数据营养是视觉模型之前的基础层；现有技能码仍只是未来能力的扩展位。

## 生产发布警告

不要把元数据或演示适配器公开宣传为 AI 图片理解。接入真实 Provider 前，仍需单独完成模型输出不可信校验、图片二次授权、凭证加密与撤销、隐私/数据保留策略、成本硬上限、CORS/来源控制，以及真实 MySQL、Redis、对象存储和更广泛浏览器 E2E。下一阶段边界见[伙伴图片观察基础层设计](superpowers/specs/2026-08-13-companion-vision-foundation-design.md)，生产冷启动与人工签字流程见[代码复审与人工验收手册](reviews/2026-08-13-companion-life-core-review-and-manual-checklist.md)。
