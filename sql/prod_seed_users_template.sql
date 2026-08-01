-- 生产环境安全模板：本文件不能直接创建账号。
-- 1. 在可信的离线环境中使用 BCrypt strength 12 生成临时密码密文。
-- 2. 替换 REPLACE_WITH_BCRYPT_12_HASH；确认账号名后，手动移除目标语句的注释。
-- 3. 执行后立即登录并修改临时密码，再轮换 Session 命名空间使旧会话失效。
-- 4. INSERT IGNORE 不会覆盖已有同名账号；受影响行数为 0 时先检查现有记录。

-- 普通用户示例（如果生产用户只能通过注册接口创建，可以不启用此语句）：
-- INSERT IGNORE INTO user
--     (userAccount, userPassword, userName, userRole, isDelete)
-- VALUES
--     ('replace_with_real_user_account', 'REPLACE_WITH_BCRYPT_12_HASH', '生产普通用户', 'user', 0);

-- 管理员示例：
-- INSERT IGNORE INTO user
--     (userAccount, userPassword, userName, userRole, isDelete)
-- VALUES
--     ('replace_with_real_admin_account', 'REPLACE_WITH_BCRYPT_12_HASH', '生产管理员', 'admin', 0);

-- 执行已启用的插入语句后，再单独执行下面的查询；先替换两个账号名。
SELECT userAccount, userName, userRole, isDelete
FROM user
WHERE userAccount IN ('replace_with_real_user_account', 'replace_with_real_admin_account')
ORDER BY userRole, userAccount;
