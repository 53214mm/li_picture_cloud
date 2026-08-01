-- 仅用于本地开发，严禁在生产环境执行。
-- 普通用户：user_seed / LocalUser123!
-- 管理员：admin_seed / LocalAdmin123!
-- INSERT IGNORE 表示同名账号已存在时保留原数据，不重置密码或角色。

INSERT IGNORE INTO user
    (userAccount, userPassword, userName, userRole, isDelete)
VALUES
    ('user_seed', '$2a$12$a5SNma8tchPGcKIOSIGgI.liyrqFQiARSqUVszUdmlO3qCR9U3Cs6', '本地测试用户', 'user', 0),
    ('admin_seed', '$2a$12$5Vq4upsXQOk.O0EeBnI48eLTzlFKaBlqa9CQmf1N5tLCyDecFTEmq', '本地测试管理员', 'admin', 0);

-- 验证结果：故意不查询 userPassword，避免在数据库客户端中展示密码密文。
SELECT userAccount, userName, userRole, isDelete
FROM user
WHERE userAccount IN ('user_seed', 'admin_seed')
ORDER BY userRole, userAccount;
