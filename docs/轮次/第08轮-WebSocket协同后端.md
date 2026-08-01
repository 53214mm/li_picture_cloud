# 第 08 轮：WebSocket 协同编辑后端

## 1. WebSocket 和普通 HTTP 有什么不同

普通 HTTP 像寄信：前端发一次请求，后端回一次响应，连接就结束。WebSocket 像保持通话：建立连接后，前后端都能随时发送消息，所以适合协同编辑。

连接地址是：

```text
/api/ws/collaboration?pictureId=图片编号
```

浏览器建立连接时会带上当前站点的 Session Cookie。握手拦截器从服务端 Session 读取用户，不接受客户端在消息里自报用户编号。

## 2. 谁能加入

必须同时满足：

- 已登录；
- 图片属于团队空间；
- 当前用户是该团队的 editor 或 admin；
- 权限集合包含 `collaboration:edit`。

公共图库和私有空间即使是所有者也没有协同权限，因为产品要求协同只对团队空间开放。

## 3. 为什么每条消息还要再检查权限

用户连接后可能被管理员移出团队或降为 viewer。如果只在握手时检查，旧连接可以继续编辑。处理器因此在每条操作前重新查询服务端权限。

## 4. 客户端发送什么

```json
{
  "commandId": "由客户端生成的 UUID",
  "operation": "ROTATE_RIGHT",
  "baseVersion": 3
}
```

操作只有 `ROTATE_LEFT`、`ROTATE_RIGHT`、`ZOOM_IN`、`ZOOM_OUT`。图片编号和用户编号不从消息读取，而使用握手阶段保存的可信属性。

## 5. 服务端状态

```json
{
  "pictureId": 1001,
  "rotation": 90,
  "scale": 1.1,
  "version": 4
}
```

- 旋转角度始终是 0、90、180、270。
- 每次缩放变化 0.1，范围限制为 0.25 到 4.0。
- 每次成功操作版本加 1。
- 相同 `commandId` 重发时直接返回第一次结果，不重复旋转或缩放。

## 6. 为什么要 baseVersion

假设甲和乙都看到版本 3。甲先右旋，服务端变成版本 4；乙仍用版本 3 发缩小。服务端拒绝乙的旧命令并返回版本 4 的完整状态，乙同步后再操作。这样不会悄悄覆盖别人刚完成的编辑。

## 7. 服务端广播

- `STATE`：刚连接时同步完整状态；
- `JOIN` / `LEAVE`：成员进入或离开；
- `OPERATION`：操作成功，包含操作人、操作类型、完整状态和中文提示；
- `ERROR`：版本冲突、权限变化或消息格式错误，同时带最新状态。

## 8. 安全配置

默认只允许 `http://localhost:5173` 发起 WebSocket。部署时通过 `COLLABORATION_ALLOWED_ORIGINS` 设置真实前端域名，不应在生产使用通配符 `*`。

## 9. 当前单实例限制

房间和状态目前保存在 JVM 内存中，应用重启会清空，多实例之间也不会自动同步。该问题已写入 `docs/未决问题.md`。在引入 Redis 房间状态和跨实例事件总线前，协同连接应使用单实例或按图片粘性路由。

## 10. 验证

```powershell
.\mvnw.cmd -B "-Dtest=CollaborationSessionServiceTest,CollaborationHandshakeInterceptorTest" test
.\mvnw.cmd -B "-Dspring.profiles.active=test" package
```
