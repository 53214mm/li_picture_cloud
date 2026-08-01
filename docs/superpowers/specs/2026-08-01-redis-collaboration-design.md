# Redis 多实例协同编辑设计

## 目标

将协同图片的编辑状态、版本和命令幂等记录从单个 JVM 迁移到 Redis，并通过 Redis Pub/Sub 把事件传播到所有后端实例。用户连接到不同实例时，仍能实时看到同一张团队图片的左旋、右旋、放大、缩小和成员进出提示。

现有 WebSocket 地址、请求格式和响应格式保持兼容。该功能仍只对团队空间开放，并在握手和每条编辑命令执行前校验编辑权限。

## 一致性模型

每张图片在 Redis 中只有一个权威状态：`rotation`、`scale`、`version`。客户端发送 `baseVersion`，服务端仅在它等于权威版本时接受命令。

一段 Redis Lua 脚本在单次原子执行中完成：

1. 检查命令幂等结果；
2. 读取或初始化图片状态；
3. 比较客户端版本和服务端版本；
4. 计算旋转或缩放结果；
5. 递增版本；
6. 保存状态和命令结果；
7. 刷新过期时间。

这样无需分布式锁，也不会发生两个实例先后读取同一版本后互相覆盖。

## Redis 数据结构

- `picture-cloud:collaboration:state:{pictureId}`：Hash，字段为 `rotation`、`scale`、`version`，无操作 24 小时后过期。
- `picture-cloud:collaboration:command:{pictureId}:{commandId}`：String，保存该命令成功后的 JSON 状态，无操作 24 小时后过期。
- `picture-cloud:collaboration:events`：Pub/Sub channel，承载操作和成员进出事件。

键中的 `pictureId` 使用 Redis Cluster hash tag，确保状态和命令键落在同一 slot，允许 Lua 脚本在集群模式执行。

## 模块接口

`CollaborationStateStore` 是应用层读取和提交协同状态的接口：

- `current(pictureId)` 返回权威状态；
- `apply(command)` 原子执行命令，并返回状态和“首次应用/重复命令”标志；
- `activeSessionCount()` 返回可观测的近似活动状态数，Redis 实现不执行生产环境全库扫描。

生产使用 `RedisCollaborationStateStore`，测试使用 `InMemoryCollaborationStateStore`。现有 `CollaborationSessionService` 保留参数校验、指标统计和异常语义，内部委托给状态存储。

`CollaborationEventPublisher` 负责发布跨实例事件。Redis 适配器发布 JSON；监听器收到消息后交给 WebSocket Handler，仅广播到当前 JVM 的本地连接。

## 事件流

编辑命令流程：

```text
客户端 → 本机 WebSocket Handler → 权限复查 → Redis Lua 原子提交
      → Redis Pub/Sub → 所有后端实例 → 各实例本地 WebSocket 连接
```

JOIN/LEAVE 不修改图片状态，只携带 Redis 当前状态发布到相同 channel。事件包含唯一 `eventId` 和 `sourceInstanceId`，便于诊断；每个事件只通过订阅回路广播一次，发布实例不额外本地广播。

Pub/Sub 允许在线消息的至多一次传递。客户端断线重连后会先读取 Redis 权威状态，因此即便断线期间漏掉事件，也会恢复到最新编辑效果。

## Redis 故障行为

Redis 是多实例协同的权威存储。Redis 不可用时：

- 新连接无法读取状态，连接建立后发送错误并关闭；
- 编辑命令返回协同错误，不修改本地状态；
- 不回退到 JVM 内存，防止不同实例产生无法合并的分叉状态；
- 权限系统和普通图片接口维持各自原有行为。

## 配置

- `app.collaboration.store=redis`：生产默认 Redis。
- `app.collaboration.state-ttl=24h`：状态及幂等记录过期时间。
- `app.collaboration.channel=picture-cloud:collaboration:events`：广播 channel。
- 测试 profile 使用 `app.collaboration.store=memory`，不要求开发机安装 Redis。

## 测试策略

1. 状态存储契约测试同时约束内存与 Redis 适配器的初始化、四类操作、版本冲突、幂等和 TTL。
2. Redis Lua 使用 Testcontainers Redis 做真实集成测试；Docker 不可用时该组测试明确跳过，本地纯单元测试仍可运行。
3. 事件传播测试创建两个本地广播端点，共享同一消息总线，证明实例 A 发布的事件能到达实例 B。
4. 保留现有 WebSocket、权限、领域状态机和前端重连测试。
5. CI 启动 Redis service，执行完整 Redis 集成测试，不允许在 CI 跳过。

## 迁移与兼容

部署新版本前无需迁移旧 JVM 内存状态，因为它本来就是临时状态。滚动发布期间旧实例无法接收 Redis 事件，因此发布窗口必须一次替换全部协同实例，或暂时将 WebSocket 流量排空后部署。

完成后删除 `docs/未决问题.md` 中的“协同编辑跨实例一致性”问题，并新增面向新手的实施、配置、验证和故障排查文档。
