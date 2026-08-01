# User Seed Scripts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add one directly runnable local-development seed script and one deliberately non-runnable production template for creating a normal user and an administrator with BCrypt strength 12 passwords.

**Architecture:** Keep environment safety visible at the file boundary: the development SQL contains fixed test credentials and valid hashes, while the production SQL contains only commented examples and an unmistakable non-hash replacement marker. A Java contract test reads the delivered SQL files themselves, verifies their roles and idempotency syntax, and uses the existing `PasswordHashService` to prove the development hashes match the documented passwords.

**Tech Stack:** MySQL 8 SQL, Java 21, Spring Security BCrypt, JUnit 5, AssertJ, Maven.

## Global Constraints

- BCrypt strength is exactly 12.
- The local development accounts are `user_seed` / `LocalUser123!` and `admin_seed` / `LocalAdmin123!`.
- Both scripts use `INSERT IGNORE`; rerunning them must never overwrite an existing account.
- The production template contains no fixed plaintext password and no valid default BCrypt hash.
- Production insert statements remain commented until an operator replaces `REPLACE_WITH_BCRYPT_12_HASH` and deliberately enables them.
- Seed scripts must never run automatically during Spring Boot startup.
- The local `src/main/resources/application.yaml` modification must never be staged or committed.
- Every implementation change follows RED → GREEN, and verified commits are pushed to `origin/main`.

---

## File Structure

- `sql/dev_seed_users.sql`: executable local-only seed data and a password-free verification query.
- `sql/prod_seed_users_template.sql`: commented production examples, operator checklist, and safe verification query.
- `src/test/java/com/li/lipicturecloud/sql/UserSeedSqlContractTest.java`: contract tests over both real SQL deliverables.
- `docs/round-17-user-seed-guide.md`: beginner instructions for local execution and production preparation.

### Task 1: SQL contract test and both seed files

**Files:**
- Create: `src/test/java/com/li/lipicturecloud/sql/UserSeedSqlContractTest.java`
- Create: `sql/dev_seed_users.sql`
- Create: `sql/prod_seed_users_template.sql`

**Interfaces:**
- Consumes: `PasswordHashService.matches(String rawPassword, String encodedPassword)`.
- Produces: two manually executed MySQL scripts; no Spring bean, endpoint, or startup hook is added.

- [ ] **Step 1: Write the failing contract test**

Create `UserSeedSqlContractTest` with tests that load the repository files as UTF-8, extract the two development hashes, and enforce all safety rules:

```java
package com.li.lipicturecloud.sql;

import com.li.lipicturecloud.service.PasswordHashService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class UserSeedSqlContractTest {

    private static final Path DEV_SCRIPT = Path.of("sql", "dev_seed_users.sql");
    private static final Path PROD_TEMPLATE = Path.of("sql", "prod_seed_users_template.sql");
    private static final Pattern BCRYPT_HASH = Pattern.compile("\\$2[ayb]\\$12\\$[./A-Za-z0-9]{53}");
    private final PasswordHashService passwordHashService = new PasswordHashService();

    @Test
    void developmentScriptContainsRunnableIdempotentAccountsWithValidPasswords() throws IOException {
        String sql = read(DEV_SCRIPT);
        Matcher matcher = BCRYPT_HASH.matcher(sql);

        assertThat(sql).contains("INSERT IGNORE INTO user");
        assertThat(sql).contains("'user_seed'", "'user'", "'admin_seed'", "'admin'");
        assertThat(matcher.find()).isTrue();
        String userHash = matcher.group();
        assertThat(matcher.find()).isTrue();
        String adminHash = matcher.group();
        assertThat(matcher.find()).isFalse();
        assertThat(passwordHashService.matches("LocalUser123!", userHash)).isTrue();
        assertThat(passwordHashService.matches("LocalAdmin123!", adminHash)).isTrue();
    }

    @Test
    void developmentVerificationQueryDoesNotSelectPassword() throws IOException {
        String sql = read(DEV_SCRIPT);
        String verificationSection = sql.substring(sql.indexOf("-- 验证结果"));

        assertThat(verificationSection).contains("SELECT userAccount, userName, userRole, isDelete");
        assertThat(verificationSection).doesNotContain("userPassword");
    }

    @Test
    void productionTemplateCannotCreateAnAccountWithoutOperatorEditing() throws IOException {
        String sql = read(PROD_TEMPLATE);

        assertThat(sql).contains("REPLACE_WITH_BCRYPT_12_HASH");
        assertThat(sql).contains("-- INSERT IGNORE INTO user");
        assertThat(sql).doesNotContain("LocalUser123!", "LocalAdmin123!", "user_seed", "admin_seed");
        assertThat(BCRYPT_HASH.matcher(sql).find()).isFalse();
        assertThat(sql.lines()
                .filter(line -> line.stripLeading().startsWith("INSERT IGNORE INTO user")))
                .isEmpty();
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 2: Run RED and confirm the expected cause**

Run:

```powershell
$env:JAVA_HOME='G:\JDK\Java\jdk-21'
.\mvnw.cmd -Dtest=UserSeedSqlContractTest test
```

Expected: the test fails with `NoSuchFileException` for `sql/dev_seed_users.sql`; this proves the test is exercising the missing deliverable.

- [ ] **Step 3: Generate the two development hashes locally**

Use the project dependency classpath and Java 21 JShell; do not use an online hash generator:

```powershell
.\mvnw.cmd -q -DincludeScope=test dependency:build-classpath "-Dmdep.outputFile=target/seed-classpath.txt"
$seedClasspath = (Get-Content -Raw target/seed-classpath.txt).Trim()
@'
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
var encoder = new BCryptPasswordEncoder(12);
System.out.println(encoder.encode("LocalUser123!"));
System.out.println(encoder.encode("LocalAdmin123!"));
/exit
'@ | & "$env:JAVA_HOME\bin\jshell.exe" --class-path $seedClasspath
```

Expected: exactly two generated values matching `$2[ayb]$12$` followed by 53 BCrypt characters. Copy them only into the development SQL password values; do not create a generated classpath file outside ignored `target/`.

- [ ] **Step 4: Add the directly runnable development script**

Create `sql/dev_seed_users.sql` with a prominent local-only warning, the two generated hashes in this order, and the exact column list:

```sql
-- 仅用于本地开发，严禁在生产环境执行。
-- 普通用户：user_seed / LocalUser123!
-- 管理员：admin_seed / LocalAdmin123!
-- INSERT IGNORE 表示同名账号已存在时保留原数据，不重置密码或角色。

INSERT IGNORE INTO user
    (userAccount, userPassword, userName, userRole, isDelete)
VALUES
    ('user_seed', '<GENERATED_LOCAL_USER_BCRYPT_12_HASH>', '本地测试用户', 'user', 0),
    ('admin_seed', '<GENERATED_LOCAL_ADMIN_BCRYPT_12_HASH>', '本地测试管理员', 'admin', 0);

-- 验证结果：故意不查询 userPassword，避免在数据库客户端中展示密码密文。
SELECT userAccount, userName, userRole, isDelete
FROM user
WHERE userAccount IN ('user_seed', 'admin_seed')
ORDER BY userRole, userAccount;
```

The angle-bracket values above are plan notation only. The committed SQL must contain the two real hashes produced in Step 3 and must contain no angle-bracket markers.

- [ ] **Step 5: Add the deliberately disabled production template**

Create `sql/prod_seed_users_template.sql`. Every insert line remains commented, and no other line may start with executable `INSERT IGNORE INTO user`:

```sql
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
```

- [ ] **Step 6: Run GREEN and inspect the scripts**

Run:

```powershell
.\mvnw.cmd -Dtest=UserSeedSqlContractTest test
rg -n "INSERT IGNORE|user_seed|admin_seed|REPLACE_WITH_BCRYPT_12_HASH" sql/dev_seed_users.sql sql/prod_seed_users_template.sql
git diff --check
```

Expected: three tests pass; local SQL shows the two accounts; production SQL shows only commented inserts and replacement markers; no whitespace errors.

- [ ] **Step 7: Commit and push Task 1**

Stage exactly these files so the local YAML is excluded:

```powershell
git add -- sql/dev_seed_users.sql sql/prod_seed_users_template.sql src/test/java/com/li/lipicturecloud/sql/UserSeedSqlContractTest.java
git diff --cached --name-only
git commit -m "feat: add safe user seed scripts"
git push origin main
```

Expected staged paths: the two SQL files and one Java test only.

---

### Task 2: Beginner seed-script guide

**Files:**
- Create: `docs/round-17-user-seed-guide.md`

**Interfaces:**
- Documents Task 1 for developers and production operators; it does not introduce a runtime interface.

- [ ] **Step 1: Write the beginner guide**

Create the guide with these concrete sections and commands:

1. Explain that seed data is initial database data, not an automatic registration feature.
2. Local preparation: start MySQL, select the intended local database, execute `sql/user.sql`, then execute `sql/dev_seed_users.sql`.
3. List both local account/password pairs and state that they are public development credentials.
4. Explain `INSERT IGNORE`: first execution inserts rows; later executions preserve existing rows; it never repairs or resets an existing same-name account.
5. Show a password-free verification query selecting `userAccount`, `userName`, `userRole`, and `isDelete`.
6. Explain why the production template is commented and why copying the local hashes to production is forbidden.
7. Show the same local JShell procedure from Task 1 for generating a strength-12 BCrypt hash, then instruct the operator to replace the marker, review the account/role, uncomment only the required statement, and execute it.
8. Require a database backup, immediate temporary-password change, and Session namespace rotation as described in `docs/round-16-password-ai-security-guide.md`.
9. Troubleshooting: missing table means `user.sql` was not executed; affected rows 0 means the account exists; login failure means check `isDelete = 0`, the selected database, and whether the hash belongs to the entered password.

- [ ] **Step 2: Verify the guide is complete**

Run:

```powershell
rg -n "user_seed|admin_seed|INSERT IGNORE|BCrypt|strength 12|生产|Session|user.sql" docs/round-17-user-seed-guide.md
git diff --check
git status --short
```

Expected: every required topic has a match; no whitespace errors; the unrelated YAML remains unstaged.

- [ ] **Step 3: Commit and push Task 2**

```powershell
git add -- docs/round-17-user-seed-guide.md
git diff --cached --name-only
git commit -m "docs: explain user seed setup"
git push origin main
```

Expected staged path: only `docs/round-17-user-seed-guide.md`.

---

### Task 3: Full delivery verification

**Files:**
- Verify only. If a defect is found, first add or adjust a failing contract test that reproduces it, then make the smallest correction.

**Interfaces:**
- Proves the scripts match the current authentication implementation and that the repository remains buildable.

- [ ] **Step 1: Run the complete backend verification**

```powershell
$env:JAVA_HOME='G:\JDK\Java\jdk-21'
.\mvnw.cmd -B "-Dspring.profiles.active=test" verify
```

Expected: Maven reports `BUILD SUCCESS`, zero test failures, and zero test errors. Redis-dependent integration tests may report their existing assumption-based skips when Redis is unavailable.

- [ ] **Step 2: Recheck the security invariants**

```powershell
rg -n "\$2[ayb]\$12\$" sql/dev_seed_users.sql
rg -n "LocalUser123!|LocalAdmin123!|user_seed|admin_seed|\$2[ayb]\$12\$" sql/prod_seed_users_template.sql
rg -n "^[[:space:]]*INSERT IGNORE INTO user" sql/prod_seed_users_template.sql
```

Expected: the development script contains two cost-12 hashes; both production searches return no matches.

- [ ] **Step 3: Verify Git scope and remote alignment**

```powershell
git diff --check
git status --short
git fetch origin main
git rev-parse HEAD
git rev-parse origin/main
```

Expected: `src/main/resources/application.yaml` is the only modified file; it is not staged; local `HEAD` equals `origin/main`.

- [ ] **Step 4: Report credentials and operating boundary**

Tell the user the two local credentials, link both SQL files and the beginner guide, state that the production template still requires an operator-generated BCrypt hash, and explicitly confirm the local YAML was excluded.
