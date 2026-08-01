# 第 6 轮实施计划：图片仓储边界与静态分表

## 目标

在不影响默认启动方式的前提下，引入 ShardingSphere-JDBC 静态双表能力，并在业务代码与 MyBatis-Plus 之间建立图片仓储边界。

## 决策

- 静态分片键使用非空的 `userId`，算法为 `userId % 2`。
- 物理表为 `picture_0` 和 `picture_1`，业务 SQL 仍访问逻辑表 `picture`。
- 默认 profile 不启用分表；只有显式启用 `sharding-static` 才使用 ShardingSphereDriver。
- 原表暂不删除，作为迁移与回滚保障。
- 先让权限资源加载走 `PictureRepository`，后续 DDD 轮次再逐步迁移其他图片读写。

## 验证

1. 仓储适配器单元测试覆盖查询、插入、更新、删除返回值。
2. 默认 test profile 完整构建，证明未开启分表时没有回归。
3. 检查静态 YAML 可被 ShardingSphere 解析。
4. SQL 脚本提供分表创建、幂等复制和行数核对。

