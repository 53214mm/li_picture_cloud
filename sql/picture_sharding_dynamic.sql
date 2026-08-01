-- 第 7 轮：动态四表初始化与迁移脚本。
-- 空间图片按 spaceId 路由；公共图片的 spaceId 为空，回退按 userId 路由。

CREATE TABLE IF NOT EXISTS picture_0 LIKE picture;
CREATE TABLE IF NOT EXISTS picture_1 LIKE picture;
CREATE TABLE IF NOT EXISTS picture_2 LIKE picture;
CREATE TABLE IF NOT EXISTS picture_3 LIKE picture;

INSERT INTO picture_0 SELECT * FROM picture WHERE MOD(COALESCE(spaceId, userId), 4) = 0
ON DUPLICATE KEY UPDATE id = VALUES(id);
INSERT INTO picture_1 SELECT * FROM picture WHERE MOD(COALESCE(spaceId, userId), 4) = 1
ON DUPLICATE KEY UPDATE id = VALUES(id);
INSERT INTO picture_2 SELECT * FROM picture WHERE MOD(COALESCE(spaceId, userId), 4) = 2
ON DUPLICATE KEY UPDATE id = VALUES(id);
INSERT INTO picture_3 SELECT * FROM picture WHERE MOD(COALESCE(spaceId, userId), 4) = 3
ON DUPLICATE KEY UPDATE id = VALUES(id);

SELECT (SELECT COUNT(*) FROM picture) AS source_count,
       (SELECT COUNT(*) FROM picture_0) + (SELECT COUNT(*) FROM picture_1)
           + (SELECT COUNT(*) FROM picture_2) + (SELECT COUNT(*) FROM picture_3) AS sharded_count;
