# Context Map

## Contexts

- [身份与访问](./src/main/java/com/li/lipicturecloud/domain/identityaccess/CONTEXT.md) — 识别当前用户，并决定其可执行的动作。
- [空间](./src/main/java/com/li/lipicturecloud/domain/space/CONTEXT.md) — 管理空间与成员关系。
- [图片](./src/main/java/com/li/lipicturecloud/domain/picture/CONTEXT.md) — 管理图片资产及其生命周期。
- [协同编辑](./src/main/java/com/li/lipicturecloud/domain/collaboration/CONTEXT.md) — 管理多人同时编辑一张图片时的会话和状态。
- [伙伴](./src/main/java/com/li/lipicturecloud/domain/companion/CONTEXT.md) — 管理图像生命体的成长、记忆与受控主动行为。
- [AI 运行](./src/main/java/com/li/lipicturecloud/domain/airuntime/CONTEXT.md) — 管理能力、模型与 MCP 连接、任务路由和平台额度。

## Relationships

- **身份与访问 → 空间**：读取空间成员关系和角色，计算权限。
- **图片 → 空间**：团队图片归属于一个空间。
- **协同编辑 → 身份与访问**：加入和发送编辑命令前都需要校验权限。
- **协同编辑 → 图片**：协同会话以图片为对象，最终状态通过图片模块持久化。
- **伙伴 → 身份与访问**：喂养、反馈和主动行为都由已认证主体发起，并受自主契约和权限约束。
- **伙伴 → 图片**：伙伴只从已授权图片获得营养和记忆；图片删除会影响相应派生记忆。
- **伙伴 → AI 运行**：伙伴通过能力目录请求理解、创作或投递，不直接连接模型或 MCP。
- **AI 运行 → 身份与访问**：能力调用、模型连接、凭据引用和费用来源都绑定主体。
- **AI 运行 → 图片 / 空间**：模型与能力只能处理授权图片，并在目标空间权限与容量允许时创建新图片。
