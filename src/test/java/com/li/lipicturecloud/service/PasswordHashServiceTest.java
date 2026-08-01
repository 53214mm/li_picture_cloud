package com.li.lipicturecloud.service;

import cn.hutool.crypto.digest.DigestUtil;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PasswordHashServiceTest {

    private final PasswordHashService service = new PasswordHashService();

    @Test
    void encodesAndMatchesWithBcrypt() {
        String encoded = service.encode("correct-password");
        assertThat(encoded).startsWith("$2").contains("$12$").isNotEqualTo("correct-password");
        assertThat(service.matches("correct-password", encoded)).isTrue();
        assertThat(service.matches("wrong-password", encoded)).isFalse();
    }

    @Test
    void rejectsPasswordsLongerThan72Utf8BytesWithControlledMessage() {
        String password = "a".repeat(70) + "你";
        assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(73);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.encode(password))
                .withMessage("密码不能超过 72 个 UTF-8 字节");
    }

    @Test
    void rejectsLegacyAndMalformedHashes() {
        assertThat(service.matches("correct-password",
                DigestUtil.md5Hex("correct-password" + "liPictureCloud2026"))).isFalse();
        assertThat(service.matches("correct-password", "")).isFalse();
        assertThat(service.matches("correct-password", "$2b$12$not-a-valid-bcrypt-hash")).isFalse();
    }
}
