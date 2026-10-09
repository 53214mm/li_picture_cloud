# 图片记忆召回与归属删除修复

日期：2026-10-09。范围是已有图片记忆的两个正确性缺陷，不是 `LINGYE-MEMORY-NEXT.md` 提议的完整记忆治理实现，也不新增产品决策或数据库迁移。

## 先复现，再修复

在修改生产代码前新增回归测试并运行现有 service/persistence 两组测试：21 个测试，1 个断言失败、1 个异常，其余通过。

1. 实际 H2/MyBatis 数据：一个较早的 CONFIRMED 记录之后插入 105 个较新的 PENDING/DISMISSED/INVALIDATED 记录。原实现先 `findRecent(100)` 再过滤确认状态，召回为空，预期的较早确认记录丢失。
2. 已确认归属的 INVALIDATED 记录，其来源图片已撤权。领域状态机允许删除，界面也提供删除，但应用服务先检查来源读取权限，返回“记忆来源图片已不可用”，使用户不能丢弃自己的记录。

原始失败输出在本次执行的 `/tmp/lingye-memory-red.log`；该路径是临时运行证据，不属于可移植项目依赖。

## 实现边界

- 仓储新增 `findConfirmed(companionId, subjectId, limit)`；SQL 在 LIMIT 前同时约束伙伴、主体和 CONFIRMED，保留创建时间/ID 倒序及 1–100 上限。
- 衍生召回入口拒绝伙伴 owner 与当前主体不一致；对仓储结果再次过滤伙伴、主体、确认状态。
- 来源授权检查针对这次真正准备返回的候选集合，不借用独立的最近 100 条 active 扫描。来源真正撤权/消失则失效并排除；即使 revision CAS 失败也排除。授权基础设施异常保持 fail-closed，不把故障永久写成撤权。
- 同一批次的同一图片只检查一次权限；列表读取仍使用原 active 扫描和相同失效逻辑。
- 删除仍需先找到当前登录主体的伙伴，并验证记忆的 companionId 与 subjectId；只有删除无需来源读取权限。返回 DELETED 视图，正文与原文均隐藏。确认、纠正、忽略仍要求来源权限。
- 没有修改聊天上下文组装器、前端、迁移、外部供应商或部署流程，没有真实模型付费调用。

## 回归覆盖

- 最终实际数据库测试增强为 156 条较新混合状态记录，其中 104 条 active；断言较早已确认记录不在旧 active(100) 窗口，但仍可被召回。随后撤销它的图片权限，断言它从召回中排除并持久化为 INVALIDATED。
- SQL 在 LIMIT 前隔离主体与伙伴，并保持排序。
- 拒绝其他 owner 的伙伴；防御性排除其他主体/伙伴的仓储返回行。
- 实际候选撤权但 CAS 失败仍不进入上下文。
- BusinessException 与运行时授权故障均 fail-closed；无错误失效写入。
- 已撤权 INVALIDATED 记录可以由 owner 删除，返回不含正文；跨主体删除仍拒绝。

## 验证

- 修复后的首次定向运行：26/26 通过，零失败/错误/跳过。之后增强了上述实际数据库 active-window 断言，纳入最终完整运行。
- 最终完整离线 `clean verify`：**849 个测试，845 通过，零失败/错误，4 个原有可选跳过**（live vision 1、Redis 3）；JAR 打包和原有 JaCoCo 门槛通过。最终实际数据库增强用例包含在此结果内。日志：`/tmp/lingye-memory-full.log`；JUnit XML：`target/surefire-reports/`。
- `git diff --check`（本次 Java 变更与文档）通过。
- 使用现有 `../tool-cache/run-mvn.sh`、离线缓存、test profile、显式 Mockito/JaCoCo 启动代理及 `/tmp/lingye-maven.lock`，不变更 POM 或质量门槛。

复跑命令（仓库根目录；已有工具缓存）：

```bash
COMPANION_VISION_LIVE_TEST=false flock /tmp/lingye-maven.lock \
  ../tool-cache/run-mvn.sh -o -B -ntp -Dspring.profiles.active=test \
  '-DargLine=-javaagent:/workspace/scratch/058c18b9b398/tool-cache/maven/repository/org/jacoco/org.jacoco.agent/0.8.13/org.jacoco.agent-0.8.13-runtime.jar=destfile=target/jacoco.exec -javaagent:/workspace/scratch/058c18b9b398/tool-cache/maven/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  clean verify
```

## 仍未完成的完整治理

这不是“彻底忘记”。DELETED 是隐藏正文的状态，数据库原文仍存在；旧聊天历史可能重新进入模型；再次喂养同一图片可产生新候选。没有实现旧聊天重放隔离、回复/摘要/索引依赖追踪、源级再提取阻断、对话事实聚合或语义擦除。完整 L-04 仍需单独设计与实施，具体提议见 `LINGYE-MEMORY-NEXT.md`。
