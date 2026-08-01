# Context Map

## Contexts

- [身份与访问](./src/main/java/com/li/lipicturecloud/domain/identityaccess/CONTEXT.md) — 识别当前用户，并决定其可执行的动作。
- [空间](./src/main/java/com/li/lipicturecloud/domain/space/CONTEXT.md) — 管理空间与成员关系。
- [图片](./src/main/java/com/li/lipicturecloud/domain/picture/CONTEXT.md) — 管理图片资产及其生命周期。
- [协同编辑](./src/main/java/com/li/lipicturecloud/domain/collaboration/CONTEXT.md) — 管理多人同时编辑一张图片时的会话和状态。

## Relationships

- **身份与访问 → 空间**：读取空间成员关系和角色，计算权限。
- **图片 → 空间**：团队图片归属于一个空间。
- **协同编辑 → 身份与访问**：加入和发送编辑命令前都需要校验权限。
- **协同编辑 → 图片**：协同会话以图片为对象，最终状态通过图片模块持久化。
