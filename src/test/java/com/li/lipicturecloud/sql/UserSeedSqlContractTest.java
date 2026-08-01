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
        String verificationQuery = verificationSection.substring(verificationSection.indexOf("SELECT"));

        assertThat(verificationQuery).startsWith("SELECT userAccount, userName, userRole, isDelete");
        assertThat(verificationQuery).doesNotContain("userPassword");
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
