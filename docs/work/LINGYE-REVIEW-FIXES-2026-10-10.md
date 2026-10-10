# 绫页四项 review 修复与验证（2026-10-10）

## 范围与恢复

基线 `d8a6e1a57afd0f8b995bdc700807099f22512b19`，唯一目标分支 `nexus/mainline-r11`。项目元数据定位到原工作区 `G:\IDEA_NEW\product\picture-cloud\li-picture-cloud`；它有 5 类未跟踪内容，完整保留，未切分支、更新文件或重启日常服务。另一个 Claude 工作树 detached 在 52080d3，未触碰。已知同项目任务均 notLoaded，未发现正在运行的写入任务；未发现项目/祖先 AGENTS.md 或项目 .agents/skills。

实施在独立本地 clone：`C:\Users\MECHREVO\Documents\Codex\2026-10-10\task\li-picture-cloud-nexus`。同分支从上述基线恢复，原工作区未提交内容不复制。恢复优先远端目标分支，读取 STATE、DECISIONS、本文件以及 LINGYE-CHAT-SESSION / LINGYE-MEMORY-VISION。提交以 `fix(companion): preserve session boundaries and fence asynchronous chat writes` 定位；发布前核对远端仍为基线，仅允许非强制快进与预期 HEAD 检查。未开 PR、合并 main 或部署。

## 实际修改

1. 长连续聊天：模型窗口仍有 20 条上限，但真实段起点用 (createTime, id) seek 分页向后查找。遇到最后登录或超过两小时间隔才停止；同时间戳消息不跳过。当前段滚出窗口的消息不进入旧段总结。
2. 总结：聊天流结束后提交任务；若调用线程仍有事务，同步器等待 afterCommit，rollback 不调度。2 workers + 32 queue，同伙伴单飞，最多最近两段，已有 45 秒单次上限不变。失败/拒绝/进程关闭不推进持久进度，下轮聊天再试；无 timer、Agent 工具循环或无限后台重试。当前轮召回已完成经历，新总结供后续轮次使用。
3. 清空：短事务中持有伙伴行锁，一并删消息和经历；回复/总结写回持有同一锁，复核发起消息 id 及经历首尾源消息仍属于该伙伴/主体。源 id 是清空后的服务端版本令牌；清空删除源后旧模型输出不能落库。经历覆盖水位和既有唯一键防重复；不用新数据库迁移或变更生产数据。旧向量仍须与现存授权内容求交集，下次召回对账。
4. 图片索引：每轮仍最多补 20 条。若对账后尚有缺失/变更候选，全部当前可用候选参加字面召回，再接语义候选，保留编号优先。完整索引仍用原语义阈值，撤权、未确认、跨用户记忆仍被过滤。

普通聊天继续单轮；主动冒泡仍 DEMO，立绘保持原版。本轮没有读取或使用真实凭据、调用收费模型、删除实际用户数据。

## 失败回归与验证

- 旧实现会话回归：30 条连续消息，窗口误用 06:20 为起点，真实起点为 05:30；行为断言失败后修复。
- 旧实现总结回归：总结在当前首字之前执行，断言失败后移出关键路径。
- 旧实现清空回归：清空前启动、之后完成的回复仍执行第二次 append，断言失败后加持久源令牌。
- 旧实现索引回归：健康向量 stub + 26 条候选，尾部名字漏召回；独立 review 又发现首批名字在未完整时仍漏，补回归复现后修复。

| 检查 | 本轮结果 | 证据边界 |
|---|---|---|
| 受影响 8 个后端套件 | 64/64，0 失败/错误/跳过 | 真实 Spring/MyBatis/H2；供应商/向量为 stub |
| 完整后端 offline verify | 915 项：911 通过、0 失败/错误、4 跳过；JaCoCo/package 通过 | 3 Redis opt-in + 1 真实视觉 smoke 跳过，不拼入通过数 |
| 前端 Node 22.23.3 全套 | 431/431，0 跳过 | 实际组件/状态回归；Node24 首跑也通过但正式结果用 Node22 |
| lint | --max-warnings 0 通过 | 无前端产品改动 |
| Companion 开/关构建与预算 | 两组通过；最大 chunk 396005 bytes | 不部署 |
| Chromium daily 配置 | 26/26 | 聊天/小屋网络 fixture，不是本轮真实 HTTP 联调 |
| 独立 Standards review | 无阻断 | 只读，未由 reviewer 独立跑测试 |
| 独立 Spec review | 1 项召回缺口已修复，复核无剩余阻断 | 未把静态 review 当供应商验收 |

隔离 H2 新测试证明：删除经历失败回滚消息删除；写入中回复与清空串行；旧总结完成后不复活；新消息仍可继续；经历重复只写一次。251 条连续消息测试跨过两页，并检查同时间戳/登录边界。阻塞 summarizer stub 验证 worker 上限、32 queue、同伙伴去重及 afterCommit/rollback。

最初仅加载测试 YAML 时遗漏 camelCase 映射，12 项 H2 错误；补回原项目非敏感映射设置后复跑。随后 1 项 Mockito 代理夹具错误改为委托 stub，最终 64/64。完整 clean 首次因正在写入的浏览器日志文件锁失败，随后完整 verify 从重新编译源码开始通过；不宣称 clean verify 通过。最后补强失败后恢复断言时，Mockito 重新设定抛异常桩触发原异常，改用 doReturn 后再次完整 verify，最终 915 项、0 失败/错误、4 跳过，09:51 UTC BUILD SUCCESS。验证日志移至 `evidence-nexus/`，不入库；Surefire/JaCoCo 在 target，浏览器痕迹在 frontend/test-results。启动的自有 Vite 已由 Playwright 清理；15173/18124/6380 检查无监听。

## 安全复跑入口

执行器默认沙箱曾在进程启动前失败；经 auto review 的 require_escalated 路径可执行。未请求/绕过凭据或改网络/TLS。Java 21 位于 `G:/JDK/Java/jdk-21`；Maven wrapper 使用已有离线缓存。只加载测试 YAML，避免加载本地 application-local 或真实服务配置：

```powershell
$env:JAVA_HOME='G:/JDK/Java/jdk-21'
./mvnw.cmd -o -B '-Dspring.config.location=classpath:/application-test.yaml' '-Dmybatis-plus.configuration.map-underscore-to-camel-case=false' '-Dmybatis-plus.global-config.db-config.logic-delete-field=isDeleted' '-Dmybatis-plus.global-config.db-config.logic-delete-value=1' '-Dmybatis-plus.global-config.db-config.logic-not-delete-value=0' verify
```

已缓存 Node22 路径在本机 `C:/Users/MECHREVO/AppData/Local/npm-cache/_npx/52027bd8fc0022aa/node_modules/node/bin/node.exe`。frontend 执行 `--test tests/*.test.mjs`、eslint、vite build、check-bundle-size。浏览器命令为 `node node_modules/@playwright/test/cli.js test --config=e2e/config/companion-daily.config.js`，PATH 中 Node22 优先；固定隔离端口、reuseExistingServer=false，已有服务占用则停止。

## 未验收与剩余限制

真实模型人格/总结质量、真实供应商账单、MySQL 行锁/pgvector 实库、生产和人工体验未验收。独立真实 HTTP 故事线本轮未重跑：现有 run-api-e2e.mjs 明确拒绝 Windows/POSIX-only，不修改日常 Redis 为它凑环境。3 Redis 专项仍跳过。

真实连续段起点回溯成本随段长度增长，当前在聊天事务中；有现有索引，未声称超长会话生产性能验收。总结在当前流终态后处理，连接一直未终止时不启动；故障与排队任务不会自动重启，持久源消息支持后续用户轮次恢复。并发上限是每应用实例 2，跨实例的持久写回仍通过数据库锁/唯一键防重复，不声称全局收费调用 exactly-once。
