# 第二轮工程治理：持续集成与测试隔离设计

## 目标

建立不依赖开发机密钥、MySQL、Redis 与外部 MCP 服务的自动化验证基线，并让本地验证命令与 GitHub Actions 保持一致。

## 范围

- 为 Spring Boot 测试提供独立的 `test` 配置。
- 测试阶段不读取 `application-local.yaml`，不访问外部 MCP。
- 使用内存数据库和非 Redis Session 完成应用上下文测试。
- 为后端 Maven 构建和前端 Vite 构建增加 GitHub Actions。
- 在项目文档中记录统一验证命令。

图库分析模块的三个未跟踪文件不属于本轮范围，不修改、不暂存、不提交。

## 方案

### 测试隔离

测试类显式启用 `test` profile。`application-test.yaml` 使用 H2 的 MySQL 兼容模式、内存 Session，并关闭外部 MCP 工具初始化。测试只验证 Spring 容器能在无外部基础设施条件下启动。

外部 MCP Provider 增加配置开关，默认在正常运行环境启用，在测试环境禁用。依赖该 Provider 的业务 Bean 使用 Spring 的条件装配边界，确保禁用时容器仍可启动，并且不会向互联网发起连接。

### 持续集成

GitHub Actions 使用两个并行 job：

- 后端：JDK 21、Maven Wrapper、`./mvnw -B package`。
- 前端：Node.js 22、npm 缓存、`npm ci`、`npm run build`。

工作流在推送到 `main` 以及 pull request 时运行。CI 不配置业务密钥。

### 本地验证

后端统一使用 Maven Wrapper；前端统一使用锁文件安装。开发文档列出与 CI 对应的命令和通过标准。

## 错误处理

- 测试配置缺失时，测试应因 profile 或 Bean 装配失败而明确报错。
- CI 的后端与前端 job 独立展示失败原因。
- 正常环境的外部 MCP 行为保持不变，只有显式设置开关时才禁用。

## 验证标准

- 在不提供 `application-local.yaml` 和任何密钥的条件下，后端测试及打包成功。
- 测试日志中不出现 MCP 远程握手。
- 前端通过干净依赖安装后的生产构建。
- Git 暂存区不包含图库分析模块文件。
