# 绫页第二增量：最终隔离 HTTP 回归

日期：2026-10-09，16:43 UTC。

## 被测范围

第二增量冻结后的工作树，基线 HEAD 为 `cef2be11ae8724e1b07c3196652a3b4ba25fc645`，包含尚待提交的窄范围记忆权限修复及前端站内 presence / 静态形象增量。此次只复跑既有 API-only 工作流，不把 HTTP 结果冒充浏览器、视觉或真实供应商验收；没有为本次测试修改产品源码。

## 结果

- **40/40 TAP 检查通过，零失败、零跳过**
- 计数：31 个业务阶段（原 27 个 + presence 4 个）、4 个协议检查、5 个父容器
- 六个实际套件：companion、gateway、presence、protocol、recipe、story
- presence 覆盖匿名拒绝、显式开启、真实模式默认拒绝、身份/未知字段隔离、DEMO 决策、字符串序号、去重、预算、静音、用户消息优先、关闭后失效、历史不污染和关闭后的普通 SSE 聊天
- 其余套件包括既有聊天历史、记忆、模型网关替身、故事及配方流程，全部在新建隔离 H2 实例运行
- 运行时间：2026-10-09T16:43:10.935Z 至 2026-10-09T16:43:43.869Z

## 隔离与清理

使用已验证的官方 Redis7.2.11、本地 Java21 / Maven3.9.15 / Node22 工具，沿用 `scripts/run-api-e2e.mjs`。执行前验证 6380 / 18124 端口为空，仅启动本次拥有的 Redis 与 Java 子进程。固定 `test,e2e`、唯一内存 H2、loopback、DEMO/stub，真实 presence 开关显式关闭。不使用生产数据或真实凭据，不发收费模型请求，不启动浏览器。

最终 `run.json` 的 `exitCode` 为 0，所有子进程均为 `closed:true`：

- prepare：退出 0
- Redis：退出 0
- checks：退出 0
- Java backend：退出 143，对应 harness 完成检查后的预期 SIGTERM

## 证据

- 完整目录：`/tmp/lpc-api-e2e-VQtcAh/`
- 目录包含 `prepare.log`、`backend.log`、`redis.log`、`checks.log`、`classpath.txt`、`run.json`
- 汇总日志：仓库同级 `tool-cache/api-e2e-second-increment.log`
- 复现命令及工具变量沿用 [presence 后端验证](LINGYE-PRESENCE-BACKEND.md) 的 API-only 命令

第二增量的 Java 全量与前端验证由各自记录负责，本文件不重新累加或代替它们。托管云浏览器与执行器 loopback 隔离的实测限制见 `LINGYE-CLOUD-BROWSER.md`；本次没有浏览器通过声明。
