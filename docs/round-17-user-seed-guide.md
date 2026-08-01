# 第 17 轮：用户种子 SQL 使用指南

## 1. 种子 SQL 是什么

“种子数据”就是项目第一次搭建时主动放进数据库的一小批初始数据。本轮提供两个文件：

- `sql/dev_seed_users.sql`：本地开发专用，可以直接创建一个普通用户和一个管理员。
- `sql/prod_seed_users_template.sql`：生产环境安全模板，默认不能创建用户，必须人工填写账号和 BCrypt 密文后才能启用。

这两个文件都需要手动执行。启动 Spring Boot 不会自动执行它们，它们也不会代替项目中的用户注册接口。

## 2. 本地开发环境：从零开始执行

### 第一步：确认你连接的是本地数据库

先启动 MySQL，再使用 IDEA Database、DataGrip、Navicat 或命令行连接数据库。执行前检查当前连接的主机、端口和数据库名，避免误连测试服或生产库。

可以先执行：

```sql
SELECT DATABASE();
```

返回值必须是你准备给本项目使用的本地数据库。

### 第二步：创建用户表

如果这是一个空数据库，先执行：

```text
sql/user.sql
```

它会创建 `user` 表以及账号唯一索引。已经有 `user` 表时，`CREATE TABLE IF NOT EXISTS` 不会删除原有数据。

### 第三步：执行开发种子脚本

再完整执行：

```text
sql/dev_seed_users.sql
```

脚本提供以下公开的本地测试凭据：

| 身份 | 登录账号 | 登录密码 | userRole |
| --- | --- | --- | --- |
| 普通用户 | `user_seed` | `LocalUser123!` | `user` |
| 管理员 | `admin_seed` | `LocalAdmin123!` | `admin` |

这些密码只为方便本地联调而公开，不能复制到互联网环境、演示服务器或生产环境。

### 第四步：确认结果

开发脚本末尾已经包含下面的查询。也可以单独再执行一次：

```sql
SELECT userAccount, userName, userRole, isDelete
FROM user
WHERE userAccount IN ('user_seed', 'admin_seed')
ORDER BY userRole, userAccount;
```

正常情况下会返回两行，并且 `isDelete` 都是 `0`。查询故意不选择 `userPassword`，避免数据库工具把密码密文展示在结果窗口或日志中。

## 3. 为什么脚本使用 INSERT IGNORE

`userAccount` 有唯一索引，所以同一个账号不能插入两次。开发脚本使用 `INSERT IGNORE` 后：

- 第一次执行：插入不存在的 `user_seed` 和 `admin_seed`。
- 再次执行：同名账号被跳过，不会产生重复数据。
- 同名账号已经存在：不会覆盖它的密码、角色、昵称或删除状态。

因此，`INSERT IGNORE` 是“安全地跳过”，不是“自动修复”。如果之前已经创建过 `admin_seed`，但密码或角色与本文不同，重复执行脚本不会重置它。请先确认这是不是你需要保留的数据，再通过正常的密码重置或管理员管理流程处理。

## 4. 生产模板为什么默认不能执行

`sql/prod_seed_users_template.sql` 中的插入语句全部以 `--` 开头，也就是 SQL 注释。密码位置是：

```text
REPLACE_WITH_BCRYPT_12_HASH
```

这不是有效密码密文。这样设计是为了防止运维人员把公开的本地账号和密码带进生产环境，也防止刚下载模板就误创建一个所有人都知道密码的管理员。

严禁将 `dev_seed_users.sql` 中的账号、明文密码或 BCrypt 密文复制到生产模板。BCrypt 密文虽然不能直接还原成明文，但开发密码已经公开，攻击者仍然可以直接使用对应明文登录。

## 5. 在可信机器上生成生产 BCrypt 密文

项目登录逻辑只接受 BCrypt，并且 strength 必须是 12。下面的流程使用本机 Maven 缓存里的 Spring Security Crypto，不访问在线密码生成网站。

### 第一步：准备项目依赖路径

在项目根目录打开 PowerShell：

```powershell
$env:JAVA_HOME='G:\JDK\Java\jdk-21'
.\mvnw.cmd -q -DincludeScope=test dependency:build-classpath "-Dmdep.outputFile=target/seed-classpath.txt"
$seedEntries = (Get-Content -Raw target/seed-classpath.txt).Trim() -split ';'
$seedCp = ($seedEntries | Where-Object { $_ -match 'spring-security-crypto|spring-jcl' }) -join ';'
```

这里特意只保留 BCrypt 需要的两个 JAR。如果直接把整个 Maven classpath 交给 JShell，Windows 可能报 `The filename or extension is too long`。

### 第二步：用环境变量临时保存密码

请自己设计一个足够长、生产环境从未使用过的临时密码，并在可信终端中设置：

```powershell
$env:SEED_TEMP_PASSWORD='在这里输入你自己设计的临时密码'
```

不要把真实密码提交到 Git、聊天记录、工单或共享截图中。

### 第三步：进入 JShell

```powershell
& "$env:JAVA_HOME\bin\jshell.exe" --class-path $seedCp
```

看到 `jshell>` 提示符后，逐行输入：

```java
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
var encoder = new BCryptPasswordEncoder(12);
encoder.encode(System.getenv("SEED_TEMP_PASSWORD"));
/exit
```

输出中以 `$2a$12$`、`$2b$12$` 或 `$2y$12$` 开头的 60 字符字符串，就是 strength 12 的 BCrypt 密文。只复制引号内部的密文。

生成后立即从当前 PowerShell 会话清除临时密码：

```powershell
Remove-Item Env:SEED_TEMP_PASSWORD
```

`target/seed-classpath.txt` 位于 Maven 构建目录中，已被 Git 忽略；它只包含依赖路径，不包含密码。

## 6. 安全地使用生产模板

执行前先备份数据库，然后按顺序操作：

1. 复制 `sql/prod_seed_users_template.sql` 到一个临时工作文件，不要直接修改模板原件。
2. 将示例账号 `replace_with_real_admin_account` 或 `replace_with_real_user_account` 改为真实账号。
3. 将对应的 `REPLACE_WITH_BCRYPT_12_HASH` 替换为刚生成的 BCrypt strength 12 密文。
4. 再次核对 `userRole`：管理员是 `admin`，普通用户是 `user`。
5. 只删除你确实需要执行的那一组语句前面的 `--`。不需要普通用户时，就让普通用户示例继续保持注释。
6. 确认当前数据库连接后执行插入语句，再执行模板末尾的无密码查询。
7. 使用临时密码首次登录，立即通过受控流程更换密码。
8. 按 `docs/round-16-password-ai-security-guide.md` 的说明轮换 Session 命名空间并重启后端，使旧 Session 全部失效。

如果执行结果显示受影响行数为 `0`，通常不是脚本坏了，而是 `INSERT IGNORE` 发现相同账号已经存在。不要直接删除生产账号；先查询并确认记录归属。

## 7. 常见问题排查

### 报错：Table 'user' doesn't exist

当前数据库还没有用户表，或者你选择了错误的数据库。先执行 `SELECT DATABASE();`，确认无误后执行 `sql/user.sql`。

### 执行成功，但受影响行数是 0

相同 `userAccount` 已经存在，因此 `INSERT IGNORE` 保留了旧数据。执行无密码验证查询检查账号、角色和 `isDelete`，不要假设脚本重置了密码。

### 能查到账号，但登录失败

依次检查：

1. 后端连接的数据库是否就是你执行脚本的数据库。
2. `isDelete` 是否为 `0`。
3. 输入的明文密码是否与生成该 BCrypt 密文时使用的密码相同。
4. 密文是否完整复制，是否以 `$2a$12$`、`$2b$12$` 或 `$2y$12$` 开头且总长 60 个字符。
5. 修改数据库配置或用户数据后，后端是否已经重启。

### 重复执行开发脚本后，密码还是不对

这是预期行为：`INSERT IGNORE` 不覆盖已有账号。确认本地数据不再需要后，可以通过项目管理功能重置账号，或者只在确定没有保留价值的本地开发库中清理对应测试记录，再重新执行脚本。不要把这种处理方式照搬到生产数据库。

## 8. 上线前检查清单

- [ ] 已备份生产数据库。
- [ ] 没有执行 `sql/dev_seed_users.sql`。
- [ ] 生产账号不是 `user_seed` 或 `admin_seed`。
- [ ] 生产临时密码不是 `LocalUser123!` 或 `LocalAdmin123!`。
- [ ] 密文由本项目 BCrypt strength 12 流程在可信机器生成。
- [ ] 只启用了需要的生产模板语句。
- [ ] 首次登录后已经修改临时密码。
- [ ] 已轮换 Session 命名空间并重启后端。
