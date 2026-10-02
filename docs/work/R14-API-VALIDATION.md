# R14 · 隔离 Redis 与真实 HTTP 验证

## 范围和验收边界

本轮沿已发布 `377ab3deecb05fb8779bf49b7fba643de36c5bbe` 继续，补齐 R14 中可独立执行的 Redis 和后端 API 证据。新增可复用 HTTP-only 测试入口，不修改产品源码、后端 API、数据库迁移、权限、成长或聊天规则。

这些测试确实启动 Redis、Spring Boot 和 H2，经过认证、控制器、服务及数据库；模型、视觉营养、COS 等外部能力使用现有 `test,e2e` 演示/stub 配置。它们不启动浏览器，不验证前端发出的请求、共享抽屉生命周期、布局、键盘/触控或真实模型输出。R11/R12/R13/R14/R16 的相关浏览器验收仍 pending，不能因此标记完整 V3 / DONE。

## 可重复运行

从 `li-picture-cloud-frontend` 运行：

```bash
export PATH=/workspace/shared/nexus-tooling/npm-cache/_npx/d18f28baf1132559/node_modules/node/bin:$PATH
export LPC_API_JAVA_HOME=/workspace/shared/nexus-tooling/jdk-21.0.12.1+1
export LPC_API_MAVEN_HOME=/tmp/lpc-r14-maven
export LPC_API_MAVEN_REPO=/tmp/lpc-r14-maven/repository
export LPC_API_REDIS_SERVER=/workspace/shared/nexus-tooling/redis-r14/redis-7.2.11/src/redis-server
npm run test:api:e2e
```

路径是本轮已准备工具的位置；新环境可以指定其经过验证的本地工具路径。需要 Node22、完整 JDK21、已缓存依赖和 Maven wrapper、官方 Redis7、空闲 localhost6380/18124。入口仅支持 POSIX，使用离线 Maven，不下载或安装依赖；不需要前端服务、浏览器或生产凭据。

入口在同一次进程编排中：

1. 拒绝复用现有6380/18124服务；离线编译测试类并生成test classpath。
2. 启动自己拥有的 Redis 子进程：仅127.0.0.1、protected-mode开启、无持久化、独立临时目录。
3. 启动自己拥有的直接 Java 子进程：固定test,e2e，唯一内存H2库，Redis DB15，localhost18124，DEMO_ONLY聊天/营养及全部现有外部stub。环境按白名单传递，不继承凭据、SPRING覆盖、JAVA_TOOL_OPTIONS、任意Maven参数或代理配置。
4. 先观察该子进程自己的启动完成，再验证HTTP就绪；测试还校验既有用户/图片、未唤醒状态及演示策略。所有请求禁止重定向，独立cookie会话；显式开关和准确目标校验在首个HTTP之前执行。
5. 测试结束/失败/中断时关闭自己的子进程；临时证据目录保留prepare/backend/redis/checks日志和run.json。不会关闭或清空已有服务。

测试文件使用 `.api.js`，由上述命令显式运行，不混入默认 `npm test` 或 Playwright 用例收集。

## HTTP 故事线

- 匿名/两个固定测试用户的登录、cookie隔离和注销
- 唤醒幂等，Home中性五轴、关系空态、默认关闭契约
- 相同喂养key返回同一收据，成长仅一次；权威Home经验、技能、情绪、关系及跨用户拒绝
- 当前前端请求头对应的SSE流，要求增量UTF-8/CRLF解析、明确done事件、完整回复；重新登录读取持久化且有序的用户/伙伴历史
- 记忆归属及确认、纠正、忽略、再确认、删除
- 契约开启后的周回顾提案、归属校验、终态不可重复接受，并恢复关闭契约
- 现有小ID图片批量选择、权限拒绝、仅修改选中项并恢复元数据；这不是大于2^53的真实端到端证明，大ID另有既有SFC/真实Axios载荷证据

4项纯协议检查另测目标/路径拒绝、UTF-8断帧及缺done/错误/截断响应拒绝；不把这4项算作实际后端场景。

## 已知兼容细节

首次真实运行纠正了测试对未唤醒Home chatPolicy和记忆枚举的错误假设；产品行为未改。记忆修改响应保留Instant纳秒，H2重读为毫秒，测试对其他字段逐一保持一致，并将时间比较限定到存储精度。

另一次请求显式使用 `Accept: text/event-stream` 得到406。现有控制器类级声明JSON；当前前端 `streamCompanionChat` 只发送Content-Type，使用默认通配Accept。因此HTTP测试复用当前前端头部，仍严格要求响应为text/event-stream、明确done与持久历史。记录严格Accept互操作缺口，不扩大API修复范围，也不声称任意SSE客户端兼容。

## Redis 专项

官方 Redis7.2.11源码来自 `https://download.redis.io/releases/redis-7.2.11.tar.gz`，SHA256为 `2f9886eca68d30114ad6a01da65631f8007d802fd3e6c9fac711251e6390323d`，与官方 `redis/redis-hashes` 匹配。只在可写工具目录构建，未使用被拒绝的apt配置读取路径或修改系统设置。

独立运行 `RedisCollaborationStateStoreTest` 两项与 `RedisCollaborationEventBusTest` 一项：**3通过、0失败/错误/跳过**，并由独立review复跑通过。覆盖原子去重、正TTL、过期版本拒绝和两个订阅容器收到同一事件；两个订阅容器属于一个JVM，不冒称两台独立部署服务器。

DB0用于该专项，DB15用于HTTP测试；每次都是自有临时Redis并正常关闭。原后端clean verify的722项（718通过、4跳过）及覆盖率数据不改写；3项Redis通过是额外执行证据，不把两组数字拼成一次新的全量verify。剩余真实AI smoke仍为操作者可选测试。

初次跨命令启动Redis后，后续命令无法连接，导致专项连接初始化失败；同一编排中启动服务和消费者后通过。没有证明底层命名空间机制，只记录执行边界的生命周期/可达性事实。`ss`的netlink查询曾返回EPERM，该路径停止；浏览器Unix IPC拒绝也保持，未重试或绕行。

## 最终执行证据

- 已审源测试代码 `a0a4922cb122bce016a424c1bc42ba2871df411f`；对应远端对象 `5d0aef4039be57d816637394a33e9bea3404c284`，tree `376dff18c26989216a75b50227455a7c3531d10e`，已只读回读父链/tree；发布状态见STATE和PUBLICATION
- 自有运行 `/tmp/lpc-api-e2e-59CQeI`、独立新实例运行 `/tmp/lpc-api-e2e-cpRy6O`：8个真实HTTP阶段和4项纯协议检查全部通过；Node TAP13另计父容器。各run.json记录所有自有子进程关闭
- Redis专项原始成功及独立成功在 `/workspace/shared/nexus-tooling/r14-validation/redis-optin/{isolated-success,independent-review}`；原覆盖率及143份报告哈希不变
- 独立安全探针：已占用端口拒绝且不关闭已有监听，无显式开关时首个HTTP前失败，重定向目标接收0次请求；SIGTERM中断返回130、三个已启动子进程全部关闭，两个端口可在同一调用重新绑定。位于 `/workspace/shared/nexus-tooling/r14-validation/api-independent-review`
- Node22全前端284/284、lint、双模式build/budget通过，最大390035bytes，CompanionView仅开启时存在；111项重点和142项默认浏览器用例仅收集，HTTP测试没有混入浏览器集
- 当前应用后端源码和POM仍未改；本轮没有将专项或HTTP执行数据追加到原JaCoCo全量结果。独立review通过，浏览器和V3整体状态仍pending
