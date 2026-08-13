# 图像生命体核心：代码复审与人工验收手册

> 审核日期：2026-08-13
>
> 审核基线：`458b324d51156ceaefc29fece2ae7ea3f9ea4010`（`docs: design image life companion platform`）到当前工作区
>
> 当前实现范围：Task 1–7（数据库迁移、领域核心、MyBatis 持久化、应用编排、HTTP API、伙伴前端、浏览器 E2E）
> 不在本次结论内：生产发布、真实视觉模型、模型/MCP 用户配置与支付

## 1. 结论

当前实现的方向正确：采用“框架无关领域核心 + Ports and Adapters + 快照状态 + 追加式成长事实”，适合未来替换图片理解模型、增加 MCP 工具和用户自定义能力。此阶段不需要引入完整事件溯源框架；伙伴当前状态走快照，成长原因走事实记录，复杂度更可控。

初审结论曾是**有条件不放行**。同日修复复核后，R-01～R-07、R-09～R-11 已关闭，R-08 作为不影响行为的 P3 可维护性工作保留在 backlog。Task 1–7 已具备进入生产前人工验收的条件；生产发布仍需执行本手册的真实 MySQL 冷启动验收。

已确认的自动化证据：

- Surefire：142 个测试，0 失败，0 错误，3 跳过；跳过项均为既有 Redis collaboration 测试（event bus 1、state store 2），不属于本次伙伴功能。
- 伙伴领域 JaCoCo 分支覆盖：196 covered / 30 missed，86.73%，阈值检查通过。
- `verify`/打包：`BUILD SUCCESS`，最新测试报告时间 2026-08-13 16:23:11（Asia/Shanghai）。
- 前端：24 个 Node 测试通过，ESLint 0 warning；默认生产构建不包含伙伴路由，显式开启构建生成 16.62 KB 的懒加载块，两种构建均通过包体预算。Chromium E2E 已通过真实 Spring Boot/H2/登录会话/临时隔离 Redis 的唤醒、断响应重试、重载和来源图片不变验证。
- 当前测试覆盖了迁移 update → rollback → update、两种分片模式的 H2 路由、并发唤醒、伙伴 CAS、成长快照、失败信息保留、HTTP 主体不可伪造、幂等重试和认证启动竞态。

测试通过只说明“现有测试描述的行为成立”，并不自动证明迁移可恢复、非法状态不可表达或生产 YAML 已被原样验证。

## 2. 复审发现

优先级定义：P0 = 数据或安全事故；P1 = 继续开发/发布前必须修；P2 = 合并前修复或书面接受风险；P3 = 可排期改善。

| ID | 级别 | 发现 | 证据与影响 | 建议 |
|---|---|---|---|---|
| R-01 | P1 | Liquibase changeSet 仍存在 MySQL DDL 宕机窗口 | `2026-08-11-companion-life-core.xml` 的建表/建索引是无条件 DDL。MySQL 已提交 DDL、Liquibase 尚未写入 `DATABASECHANGELOG` 时若进程中断，重跑可能撞到已存在的表或索引，与计划声明的 resumable 不一致。 | 每个建表/索引 changeSet 增加可恢复 precondition（存在时经过结构校验后 `MARK_RAN`，异常 `HALT`），并新增“模拟对象已存在后继续迁移”的测试。 |
| R-02 | P1 | `FeedingRun` 可恢复出互相矛盾的状态 | `FeedingRun` 构造器只校验单字段，没有要求 `COMPLETED` 必须带 `resultGrowthRecordId`，也没有约束 `FAILED/REJECTED` 的安全错误信息；持久化适配器会直接把坏数据还原为领域对象。幂等回放和失败审计可能因此不完整。 | 在规范构造器建立状态组合不变量并补反例测试；迁移/适配器遇到坏行应明确失败，不要静默合法化。 |
| R-03 | P2 | H2 路由测试没有原样验证生产 `!SINGLE` 表清单 | `CompanionSingleTableRoutingIntegrationTest` 删除真实 dataSources，并把显式清单替换为 `primary.*`。字符串测试能保护表名，但不能证明两份生产 YAML 可被真实驱动加载并路由。计划第 4 步允许替换，第 6 步又禁止“测试近似配置”，文档自身矛盾。 | 保留快速 H2 测试，再增加 Testcontainers MySQL 的生产 YAML smoke test；在此之前把它标记为生产分片验收缺口。 |
| R-04 | P2 | “成长记录只追加”目前主要靠约定 | 领域端口只暴露 `append`，这是优点；但 `CompanionGrowthRecordMapper extends BaseMapper` 仍公开更新/删除能力，路由测试也实际更新、删除了成长记录。未来代码可能绕开端口。 | 生产代码只注入窄接口；把路由测试的更新/删除改到非审计表，或明确仅为临时物理 CRUD；再用架构测试禁止业务层依赖 mapper。需要更强保证时使用独立 DB 账号/权限限制 UPDATE、DELETE。 |
| R-05 | P2 | 伙伴 CAS 只校验旧 revision，不校验新 revision 必须恰好 `+1` | `MybatisCompanionRepository.save` 可写入相同、更小或跳跃的 `after.revision`，只要数据库旧 revision 命中。错误调用者可破坏单调版本。 | 保存前要求 `expectedRevision >= 0` 且 `after.revision() == expectedRevision + 1`，补回退、原值、跳号测试。 |
| R-06 | P2 | `findByOwnerIdForUpdate` 没有强制活动事务 | 在事务外调用时，数据库行锁会在语句结束后释放，但方法名会让调用者误以为锁持续有效。 | 使用 `Propagation.MANDATORY`，或把“加锁读取 + 状态变更”封装为单个事务型用例，不把裸锁方法作为普通仓储能力。 |
| R-07 | P2 | 极端经验值的等级计算可能长时间循环或溢出 | `CompanionBalance.levelFor` 从 1 逐级循环，`totalExperienceForLevel` 使用 `multiplyExact`。正常喂养受每日上限保护，但异常/迁移数据接近 `BIGINT` 上限时可能耗时极长并最终溢出。 | 改用溢出安全的二分查找或封闭计算，给 `0`、等级边界和 `Long.MAX_VALUE` 增加测试。 |
| R-08 | P3 | 五条性格轴存在散弹式映射 | 轴定义散落在领域对象、实体、JSON codec 和仓储中；以后新增属性需要同时修改多处。 | 保留列式存储，但集中轴元数据与映射顺序，增加“领域轴集合 = 持久化轴集合”的契约测试。 |
| R-09 | P3 | Java 21 包装脚本只检查完整 JDK，不验证主版本 | `mvnw-java21.ps1` 检查 `javac.exe`，但 `JAVA_HOME` 指向其他 JDK 时不会快速给出明确错误。 | 读取 `java.specification.version` 并要求为 21。 |
| R-10 | P2（文档） | 实施计划中的 JaCoCo include 写法与真实可执行配置不一致 | 计划要求 `com/li/...`，当前实际生效的是 `com.li...`；前者在当前插件规则中会静默匹配零个 PACKAGE。 | 把计划示例和解释改成已验证的点号写法，并保留 XML 包名存在性与 85% gate 的双重检查。 |
| R-11 | P1 | 旧分析尝试可终止已由新尝试接管的 feeding run | 旧 run 的终态 CAS 失败后，原实现重载新 revision 并再次写 FAILED/REJECTED；超时重启后的合法新请求可能被旧请求破坏。 | CAS 失败后只接受“数据库已到达相同目标终态”；若仍是 PROCESSING 或其他状态，一律报状态变化，不得拿新 revision 再写终态。 |

补充判断：五轴数值上限、单次变化量、每日经验上限、同图衰减、来源图片轻引用等领域方向符合当前规格；本轮未发现 Task 1–3 的明显范围膨胀。

### 2.1 修复复核记录

| 项目 | 状态 | 复核证据 |
|---|---|---|
| R-01 | 已关闭 | 七个 changeSet 支持 DDL 已提交但未记账时恢复；全局结构 precondition 会拒绝残缺同名表；迁移测试覆盖正常 update/rollback/update、恢复和错误结构。 |
| R-02 | 已关闭 | `FeedingRun` 规范构造器强制状态组合，反例测试覆盖完成无结果、处理中带结果、失败/拒绝无完整错误。 |
| R-03 | 已关闭（本地） | H2 测试仅替换 datasource，原样加载两份生产 `!SINGLE` 明细并完成 CRUD；真实 MySQL 仍属于生产放行人工步骤。 |
| R-04 | 已关闭 | 成长 Mapper 不再继承 `BaseMapper`，只公开 insert/select/aggregate；架构测试禁止 update/delete API 回归。 |
| R-05 | 已关闭 | 保存前强制新 revision 恰好等于 expected revision + 1。 |
| R-06 | 已关闭 | `findByOwnerIdForUpdate` 使用 `Propagation.MANDATORY`，事务外调用测试明确失败。 |
| R-07 | 已关闭 | 等级计算改为 BigInteger 辅助的二分查找，`Long.MAX_VALUE` 边界测试通过。 |
| R-08 | Backlog | 五轴集中元数据属于后续可维护性重构；当前由领域/持久化测试保护，不阻断功能。 |
| R-09 | 已关闭 | Maven 包装脚本读取 `java.specification.version` 并强制 JDK 21。 |
| R-10 | 已关闭 | 计划已改为 JaCoCo Maven rule 使用点号包名、XML 报告使用斜杠包名。 |
| R-11 | 已关闭 | 删除终态转换的二次 CAS；单元测试覆盖旧 fail/reject，H2 集成测试证明 restart 后旧 fail 失败且新 run 保持 `PROCESSING/revision=1`。 |

Task 4–6 新增验证包括：确定性演示营养、授权错误归一化、授权基础设施失败收尾、重复读主页快照、同 key 并发 reservation、同图不同 key 串行成长、最终 run CAS 失败整笔回滚、旧尝试不可破坏新接管、单次 Clock 读取、重复图片衰减、安全日志字段、Controller 主体来源、前端幂等回放防倒退与登录启动竞态。

## 3. 人工审核流程

人工审核不是“打开几个文件看一眼”，而是按风险从契约到实现再到运行证据逐层签字。建议每次合并固定执行下面七关。

### 第 0 关：冻结审核对象

1. 记录基线 commit、目标 commit/工作区状态、审核日期和实现范围。
2. 执行 `git status --short`，确认没有来源不明的文件。
3. 执行 `git diff --check`，先消除空白错误和冲突标记。
4. 未提交工作区在审核期间不要混入其他功能；若代码继续变化，原审核结论失效，需要重新跑受影响部分。

### 第 1 关：规格与范围

审核人逐条对照设计和计划：

- 本次只实现承诺的 Task，不把未来能力偷偷做成不可替换的硬编码。
- 图片、空间、主体、权限、授权资源等术语含义一致。
- 喂图不会修改、移动、重命名或删除来源图片。
- 失败不产生经验和成长历史；成功的伙伴快照、技能、运行记录、成长记录必须原子提交。
- 幂等键重放返回原结果，不再次成长；同图新键只获得衰减收益。

输出：规格逐项标记 `通过 / 不通过 / 不适用 / 延期（带负责人和日期）`。

### 第 2 关：领域与架构

1. 从公开入口开始读：领域对象 → 端口 → 适配器，不从 Controller 猜业务规则。
2. 尝试构造非法对象：负经验、越界性格、非法 revision、矛盾的 FeedingRun 状态。
3. 检查领域包是否仍不依赖 Spring、MyBatis、HTTP 或模型 SDK。
4. 检查可替换能力是否在小接口后面；当前推荐保留 Ports and Adapters，不引入重型 Agent 框架到领域核心。
5. 对核心算法审查边界：0、上限、上限前后、重复调用、`Long.MAX_VALUE`、时间跨日。

输出：领域不变量清单，以及每条不变量对应的测试名。

### 第 3 关：数据库、迁移与分片

1. 在临时数据库执行 update → rollback → update。
2. 检查七个 changeSet 的顺序、回滚顺序、唯一键、索引和 `DATABASECHANGELOG`。
3. 专门演练中断恢复：对象已经创建、changeSet 尚未登记时再次迁移。
4. 两种分片配置都必须加载；生产放行前至少一次使用真实 MySQL 和未改写的生产 YAML。
5. 确认 `picture` 仍由 `!SHARDING` 管理，伙伴四表和 legacy 单表由 `!SINGLE` 管理，Liquibase 只连物理 MySQL。

冷库/首次启用分片的强制顺序：

1. 备份数据库，并确认恢复点。
2. 停止或保持应用未启动。
3. 配置物理 MySQL 的 `MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DATABASE`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`。
4. 运行 `scripts/migrate-companion-physical.ps1`。
5. 查询 `DATABASECHANGELOG`，确认 `20260811-01`～`20260811-07` 全部存在。
6. 再启动 static 或 dynamic sharding profile。
7. 用专用测试主体做四表 CRUD/读取 smoke test；成长记录只做插入和读取验证。

禁止事项：不要把 Liquibase 指向 `jdbc:shardingsphere:`；不要在生产执行回滚演练；不要用真实用户图片做破坏性测试。

### 第 4 关：并发、事务与幂等

人工 reviewer 画出一次喂养的事务边界，并检查：

- 锁在同一事务中获取和消费。
- revision 只能单调 `+1`。
- 伙伴快照、技能、成长记录、feeding run 完成状态同成同败。
- 同 key 并发只产生一次效果；不同 key 同图串行计算衰减。
- 外部模型失败、数据库 CAS 失败和午夜跨日都不会产生半提交。
- 审计字段不会在重试成功后被无意清空。

至少保留四类集成测试：同 key 并发、不同 key 同图、CAS 失败事务回滚、跨午夜每日上限。

### 第 5 关：安全与隐私

对当前 HTTP API 与前端调用链逐项确认：

- 服务端从登录态获取 subjectId，不接受客户端指定主体。
- 图片必须在分析前和提交前各做一次授权检查。
- 成长记录只保留图片 ID 和必要摘要，不复制原图或敏感元数据。
- safe error 只存可展示信息，不存 token、供应商响应全文或堆栈。
- 用户自带模型 token 未来应加密、脱敏、可撤销；公共额度必须有硬上限。

### 第 5.1 关：HTTP 与前端交互

1. 使用已登录普通用户请求 `GET /companion/me`、`POST /companion/awaken`、`POST /companion/feed`；确认请求体没有 `userId` 字段，服务端主体只来自会话。
2. 在 feed JSON 中额外加入伪造的 `userId`，确认实际成长仍属于当前登录用户。
3. 断网或制造 5xx 后点击“重试这次喂养”，确认复用原 idempotency key；切换图片后必须创建新 key。
4. 重放旧成功 key，确认页面 revision、经验和时间线不会回退或重复。
5. 分别在 375px、768px 和桌面宽度检查：无横向滚动、图片按钮至少 44px、键盘焦点可见、错误旁有恢复动作。
6. 默认生产构建不应出现 `/companion` 入口；只有前端 `VITE_COMPANION_ENABLED=true` 与后端 `COMPANION_ENABLED=true` 同时开启时才放行。

### 第 6 关：自动化复核

PowerShell 中带逗号或点号的 Maven `-D...` 参数统一加引号：

```powershell
git diff --check
.\scripts\mvnw-java21.ps1 "-Dtest=CompanionSchemaMigrationTest,ShardingModeConfigurationTest,CompanionSingleTableRoutingIntegrationTest" test
.\scripts\mvnw-java21.ps1 "-Dtest=CompanionTest,FeedingRunTest,CompanionBalancePropertyTest,DomainDependencyTest" test
.\scripts\mvnw-java21.ps1 "-Dtest=CompanionPersistenceIntegrationTest,CompanionSchemaMigrationTest,DomainDependencyTest" test
.\scripts\mvnw-java21.ps1 -B "-Dspring.profiles.active=test" verify
Set-Location li-picture-cloud-frontend
npm test
npm run lint
npm run build
npm run check:bundle
```

验收时记录命令、退出码、测试总数、失败数、覆盖率和时间。不得只写“本地通过”。

### 第 7 关：放行会议

放行规则：

- 任意 P0/P1 未关闭：不放行。
- P2 未关闭：必须写明风险、临时保护、负责人和截止日期，由技术负责人接受。
- P3：可进入 backlog，不阻断。
- 代码变化后，原 reviewer 至少复查变更部分和关联测试。
- 最终由实现人、领域 reviewer、数据库 reviewer 三方签字；涉及生产 token/额度后再增加安全 reviewer。

## 4. 人工审核记录模板

复制下面表格到每次 PR 或审核记录中：

| 项目 | 填写内容 |
|---|---|
| 审核范围 | Task / 模块 / 不包含内容 |
| 基线与目标 | base commit / target commit |
| 实现人 | 姓名、日期 |
| 领域审核人 | 姓名、结论、日期 |
| 数据库审核人 | 姓名、结论、日期 |
| 安全审核人 | 姓名、结论、日期或不适用 |
| 自动化证据 | 命令、测试数、覆盖率、构建链接/日志 |
| P0/P1 | 必须为 0 个未关闭 |
| 已接受 P2 | 风险、保护措施、负责人、截止日期 |
| 回滚方案 | 数据备份、应用回退、迁移处理方式 |
| 最终结论 | 放行 / 有条件放行 / 不放行 |

## 5. 下一阶段顺序

1. 完成 Task 7：使用真实 Spring Boot + H2 + Redis + Vite 的 Playwright 流程，不 mock HTTP。
2. 在临时 MySQL 上执行物理预迁移，并用 static、dynamic 两份生产 YAML 做冷启动 smoke test。
3. 人工检查伙伴页的 375px/768px/桌面布局、键盘操作、错误恢复、幂等重试和来源图片不变。
4. 将 R-08 的五轴元数据集中化放入后续可维护性迭代，不与本次 E2E 混做。
5. 真实视觉模型、MCP 用户配置、BYOK token 和平台额度保持在独立设计/安全评审之后实施。
