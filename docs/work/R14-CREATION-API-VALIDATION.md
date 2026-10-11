# R14 · 模型网关、Story、Recipe 的隔离 HTTP 回归

## 本轮目标

沿已发布 `7953141217980e7bf496ee7ed4dfbfcfafbc4ebc` 继续R14已有能力回归，补齐前轮Companion HTTP故事线之外的模型网关、Story与Recipe。扩展测试、安全入口检查和验证文档，并修复实际发现的Recipe创建时间响应/重读不一致；不发明新产品能力、不改API形状或领域规则。

复用 `R14-API-VALIDATION.md` 的 `npm run test:api:e2e` 和工具路径。入口拥有临时Redis、固定test,e2e的Java进程、唯一H2；外部连接探测、语言、图片和对象存储沿用现有stub。没有生产凭据或真实供应商请求，测试密钥只是固定无效字符串且仅留在本次可丢弃H2中。

默认单位测试和Playwright不收集`.api.js`。入口现在要求Companion、Gateway、Recipe、Story以及协议检查文件全部存在，在任何服务启动前拒绝不完整套件；文件顺序固定，并在run.json记录实际清单。

## 覆盖目标

- Gateway：凭据/连接真实保存和安全回显，启停、stub探测与能力、路由及使用记录、重新登录后持久化、跨用户隔离；两个连接共用旧凭据时，轮换仅影响被选连接；清理测试创建的连接、凭据和路由。
- Story：授权图片与幂等创建，大纲→草稿→保存、状态顺序、归属拒绝及重新读取；语言结果来自现有确定性stub。
- Recipe：能力/模板守门，试运行来源图片快照，最新版本用于新试运行、旧执行使用固定版本，拒绝替换图片/外部用户确认；执行只创建PENDING创作任务。真实周回顾机会产生待确认记录，用户确认之后才创建任务。

具体通过数量、实际覆盖和限制以本轮最终执行段为准。HTTP不覆盖前端请求生成、按钮/控件、共享视图、布局、触控/键盘、真实模型或部署环境；R11/R12/R13/R14/R16浏览器及完整V3验收仍pending。

## 合约注意事项

- Story当前UI直接调用`/draft`完成大纲确认与草稿阶段；测试复用这条已存在路径，不额外串联`/confirm-outline`改变状态机。
- Recipe执行固定旧版本，但仍重新检查当前图片权限和条件；试运行不等于执行，机会触发也不等于自动执行。
- 不以改写测试预期隐藏实际缺陷；发现问题先对照当前服务和持久化事实，必要时单独记录修复边界。

## 已确认的创建时间缺陷

RecipeExecution的complete/fail/reject原先把createdTime改成当前转换时间，而持久化transition只更新状态/快照/任务/错误，不更新createdTime；立即响应与刷新后的同一记录因此不同。数据库按原createdTime排序，DTO没有完成时间字段，这是同一字段的一致性错误。

修复只让三个终态转换保留原createdTime，保留now参数非空校验、身份、版本、触发时间、图片快照、机会键和原状态机。DRY_RUN/PENDING_CONFIRM分别完成、失败、拒绝的6个新测试在修复前全部断言失败；HTTP还验证确认/拒绝响应与数据库回读时间一致。无数据库迁移或接口字段变化。

## 最终执行证据

- 源代码 `117c8d4ae2aaabc5511d00f1c936a431a3167f9e`，对应远端对象 `9f77c02fd665e6ca0e6c240e4d545e596b844b8c`，tree `9c97360395a60afd2dfc084e4146ed4fe20993f8`；发布详情见STATE/PUBLICATION
- 真实HTTP：既有Companion8 + Gateway7 + Recipe7 + Story5 = **27个业务阶段**；另4项纯协议、4个父容器，Node TAP **35通过、0失败/跳过**。原始运行 `/tmp/lpc-api-e2e-VY6zoK`，独立新实例 `/tmp/lpc-api-e2e-sIESXM`，run.json确认全部自有子进程关闭
- Node22前端 **286/286**、lint、功能开/关build与bundle预算通过；最大390035bytes，CompanionView只在开启模式存在
- 浏览器重点 **111项/12文件**、默认 **142项/22文件** 仅收集，没有执行浏览器或误收HTTP套件
- 创建时间domain测试旧代码 **12项中6失败**、修复后 **12/12通过**，分别保存于 `/workspace/shared/nexus-tooling/r14-creation-validation/{red-domain,green-domain}`
- 新后端 **clean verify：728项，724通过，4预期跳过，0失败/错误**；143份XML，打包与原JaCoCo门通过；airuntime447/504=88.69%，companion541/626=86.42%，均≥85%。新报告/覆盖率在 `r14-creation-validation/clean-verify`，此前722项证据和pre-clean target另存，未混合统计
- 本轮真实HTTP直接覆盖完成/条件拒绝的时间一致性；失败终态由domain回归覆盖，不把stub成功路径称为真实上游失败验证
- 3项Redisopt-in仍沿用前轮单独执行通过的证据；本轮全量728中的4项跳过仍是3项Redisopt-in和1项操作者真实AI smoke，不将分开运行拼成新的单次全绿
- 不完整套件探针 `/tmp/lpc-api-e2e-G8CxBe`：缺gateway文件时退出1，子进程和端口检查都为空；确认在任何服务/业务请求之前停止

- 独立额外探针通过：BYOK配置下stub故事保存/重新登录读取、共享凭据单连接轮换、v1固定确认/v2不匹配、执行时重新检查当前分类、终态响应与重读相同、禁用路由不悄悄回退。证据 `/tmp/lpc-api-e2e-wADggw`；仍不代表真实供应商调用。
- 独立代码review无阻断性发现；源级对比再次复现旧三个终态时间错误并确认修复。旧150份报告/执行文件在pre-clean归档校验相同。
