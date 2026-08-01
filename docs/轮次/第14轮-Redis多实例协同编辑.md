# 第 14 轮：Redis 多实例协同编辑

## 为什么还要改 Redis

之前的协同状态保存在 Java 进程内。假设后端部署了 A、B 两台机器，小明连接 A，小红连接 B：A 的内存和 B 的内存互相看不到，双方会得到不同版本和图片效果。

本轮把 Redis 变成唯一权威状态，并把广播也交给 Redis 中转。负载均衡不再要求同一张图片固定落到同一台后端。

## 一条编辑命令经历了什么

```text
浏览器发送“右旋，基于版本 5”
  ↓
本机 WebSocket 再次检查团队编辑权限
  ↓
Redis Lua 原子校验并写成版本 6
  ↓
Redis Pub/Sub 发布操作事件
  ↓
所有后端实例收到事件
  ↓
每个实例只转发给自己连接的浏览器
```

“原子”可以理解为整套检查和修改不可被别人从中间插入。即使两台后端同时提交版本 5，也只会有一条成功；另一条会收到版本冲突并重新同步。

## Redis 中保存了什么

以图片 123 为例：

- `picture-cloud:collaboration:state:{123}`：Hash，保存 `rotation`、`scale`、`version`。
- `picture-cloud:collaboration:command:{123}:命令ID`：String，保存成功命令的结果。
- `picture-cloud:collaboration:events`：Pub/Sub channel，只传递在线广播，不保存历史。

花括号 `{123}` 是 Redis Cluster 的 hash tag，确保同一图片的状态键和命令键在同一个 slot，Lua 才能一次操作它们。

状态与命令结果默认 24 小时过期。每次读状态、执行命令或命中重复命令都会刷新 TTL。长时间无人访问的临时编辑状态会自动释放。

## 为什么命令结果也要保存

网络抖动时，浏览器可能没有收到响应并重发同一个 `commandId`。Redis 发现命令已经成功，就返回第一次的结果，而不会再旋转一次。这叫幂等。

## 为什么使用 Pub/Sub

协同编辑关心的是在线实时体验。用户断线期间不需要逐条补播历史，因为重连后首先读取 Redis 最新权威状态。Pub/Sub 简单、延迟低，符合当前需求。

如果将来需要审计每一步操作，再新增 Redis Stream 或数据库审计表，而不是让实时通道承担两种职责。

## 配置说明

常用环境变量：

```text
REDIS_HOST=Redis 地址
REDIS_PORT=6379
REDIS_PASSWORD=Redis 密码
COLLABORATION_STORE=redis
COLLABORATION_STATE_TTL=24h
COLLABORATION_CHANNEL=picture-cloud:collaboration:events
COLLABORATION_ALLOWED_ORIGINS=https://前端域名
```

测试 profile 使用 `COLLABORATION_STORE=memory` 的等价配置，因此普通单元测试不依赖 Redis。生产 profile 强制使用 Redis。

## 本地验证

先启动临时 Redis：

```powershell
docker run --rm -d --name picture-cloud-redis-test -p 6389:6379 redis:7.4-alpine
```

执行真实 Redis 测试：

```powershell
$env:JAVA_HOME = "你的 JDK 21 目录"
.\mvnw.cmd -B "-Dredis.integration.enabled=true" "-Dredis.port=6389" `
  "-Dtest=RedisCollaborationStateStoreTest,RedisCollaborationEventBusTest" test
```

结束后删除临时容器：

```powershell
docker stop picture-cloud-redis-test
```

## CI 如何保护这项功能

GitHub Actions 会启动 `redis:7.4-alpine` 服务，然后分别执行：

1. 普通后端测试与打包；
2. Redis Lua 集成测试；
3. 两个后端订阅端同时收到同一 Pub/Sub 事件的传播测试。

因此“单机测试通过、部署两台却不同步”的问题会在合并代码前暴露。

## Redis 故障时会怎样

系统不会偷偷退回本机内存，因为那会让各实例产生不同状态：

- 无法读取权威状态时，新协同连接失败；
- 无法原子写入时，编辑操作返回错误；
- 图片查询、空间管理等非协同业务按各自依赖继续运行；
- Redis 恢复后，客户端重连并读取最新状态。

生产监控至少应关注 Redis 连接失败数、命令延迟、内存使用、过期键数量和 Pub/Sub 订阅连接数。

## 发布注意事项

旧版本不订阅 Redis 事件，所以从旧版升级时不要让新旧协同实例长期混跑。推荐先在网关停止新的 WebSocket 连接，等待或关闭旧连接，然后一次替换全部协同实例。之后的同架构版本可以正常滚动发布。
