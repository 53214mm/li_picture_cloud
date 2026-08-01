# 第 11 轮：DDD 身份访问与空间成员

## 这一轮要解决什么

旧代码把“成员是什么”“怎样从数据库查询成员”“怎样计算权限”混在服务类和 MyBatis 实体里。代码能运行，但以后改角色、换存储方式或写单元测试时，修改会扩散到很多地方。

这一轮不做大爆炸式重写，只迁移最先被权限与协同共同依赖的“成员关系”。这是渐进式 DDD：每次移动一个完整业务概念，同时保持接口行为不变。

## 先认识四层

可以把系统想象成一家餐厅：

1. **领域层**写餐厅规则，例如“成员必须属于一个空间，并且只能有 viewer/editor/admin 三种角色”。它不知道 MySQL、HTTP 或 Spring。
2. **应用层**组织用例，例如“计算这个用户能不能编辑这张图片”。
3. **基础设施层**负责技术细节，例如用 MyBatis 把数据库行变成领域对象。
4. **接口层**接收 HTTP 或 WebSocket 请求，把外部输入交给应用层。

DDD 的重点不是目录好看，而是业务规则不再依赖数据库框架。

## 本轮具体改动

- 新增 `SpaceMemberRole`，未知角色会立即失败，避免拼写错误意外获得权限。
- 新增不可变的 `SpaceMembership`，集中保证空间 ID、用户 ID 和角色必填。
- 新增 `SpaceMembershipRepository` 查询接口。权限模块只理解“成员关系”，不再理解 MyBatis 查询条件。
- 新增 `MybatisSpaceMembershipRepository` 适配器，唯一负责把 `SpaceUser` 数据库实体转换为领域对象。
- 权限访问模块改为依赖领域仓储接口，现有 Controller、注解和 WebSocket 调用方式不变。
- 新增 `CONTEXT-MAP.md` 和四个上下文词汇表，统一“成员关系”“编辑命令”等名称。

## 请求如何流动

```text
HTTP / WebSocket
      ↓
权限访问模块
      ↓
SpaceMembershipRepository（稳定接口）
      ↓
MybatisSpaceMembershipRepository（技术适配器）
      ↓
space_user 表
```

以后若成员信息来自远程服务，只需增加新的适配器，权限规则不用跟着重写。

## 测试说明

- `SpaceMembershipTest` 检查合法还原、未知角色拒绝和必填身份约束。
- `MybatisSpaceMembershipRepositoryTest` 检查数据库实体能够正确转换成领域成员。
- 全量打包继续保护原有权限、协同、分片和 Spring 上下文行为。

## 为什么暂时保留旧 Service

成员新增、编辑、列表接口仍依赖 MyBatis-Plus 的通用 Service。现在强行一起替换会把一次安全的小迁移变成大范围改造。第 12 轮会继续迁移图片与协同边界；旧接口在调用者迁完之后再删除，而不是为了目录纯洁提前破坏功能。
