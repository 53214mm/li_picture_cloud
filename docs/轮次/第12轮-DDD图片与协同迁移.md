# 第 12 轮：DDD 图片与协同迁移

## 这一轮要解决什么

第 11 轮让权限模块摆脱了成员数据库实体，但它查询图片时仍拿到一整行 `Picture`。协同模块也把版本、旋转角度和缩放规则直接写在 Spring Service 中。结果是业务规则仍然和框架代码粘在一起。

本轮把这两部分变成真正的领域模型，同时保留原有 HTTP、WebSocket 与前端协议。

## 图片领域

权限判断其实只需要三个值：图片 ID、所有者 ID、空间 ID。新增的 `PictureAsset` 就是这个最小业务视图：

- 没有空间 ID 的图片是公共图片；
- 有空间 ID 的图片属于某个空间；
- 图片 ID 和所有者 ID 永远不能为空。

`PictureAssetRepository` 是权限读取图片的接口。MyBatis 适配器仍可为旧 CRUD 返回完整实体，也能为新领域代码返回 `PictureAsset`。这叫“绞杀者迁移”：新旧路径短期共存，新路径逐步取代旧路径。

## 协同领域

新增 `CollaborationSession` 聚合，它独立负责：

- 左旋、右旋；
- 放大、缩小；
- 缩放范围限制在 0.25 到 4.0；
- 每个成功动作让版本加一；
- 拒绝基于旧版本的修改。

Spring 的 `CollaborationSessionService` 现在只处理应用层职责：保存活动会话、命令幂等、收集指标，以及把旧 WebSocket 模型映射到领域模型。业务计算不再依赖 Spring。

## 为什么保留两个“版本冲突异常”

领域异常 `StaleCollaborationVersionException` 不认识接口错误码；旧的 `CollaborationVersionConflictException` 是现有 WebSocket 层理解的异常。应用层捕获前者并转换为后者，协议无需改变。这层翻译保护了领域层，也保护了已上线的客户端。

## 测试怎么读

- `PictureAssetTest` 验证公共/空间图片判断和身份约束。
- `CollaborationSessionTest` 直接从领域接口验证连续编辑、旧版本拒绝和缩放下限。
- 原 `CollaborationSessionServiceTest` 验证幂等及异常转换没有退化。
- 全量构建验证所有上下文仍能在 Spring 中组合。

## 本轮没有做什么

旧的 `model.entity.Picture` 仍是写入模型，因为上传、审核和编辑字段较多，强行一次迁完风险高。当前先迁移高频且安全敏感的读取路径；以后可为图片写入用例逐个建立领域命令。
