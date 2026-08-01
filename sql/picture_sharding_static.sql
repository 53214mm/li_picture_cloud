-- 第 6 轮：静态双表初始化脚本。
-- 执行前请先备份 picture 表，并在测试数据库演练迁移。

CREATE TABLE IF NOT EXISTS picture_0 LIKE picture;
CREATE TABLE IF NOT EXISTS picture_1 LIKE picture;

-- 幂等迁移：相同主键已存在时更新，避免重复执行产生重复数据。
INSERT INTO picture_0 SELECT * FROM picture WHERE MOD(userId, 2) = 0
ON DUPLICATE KEY UPDATE id = VALUES(id);

INSERT INTO picture_1 SELECT * FROM picture WHERE MOD(userId, 2) = 1
ON DUPLICATE KEY UPDATE id = VALUES(id);

-- 迁移核对。两个分表的总数应与原表一致，再考虑切换应用 profile。
SELECT (SELECT COUNT(*) FROM picture) AS source_count,
       (SELECT COUNT(*) FROM picture_0) + (SELECT COUNT(*) FROM picture_1) AS sharded_count;

