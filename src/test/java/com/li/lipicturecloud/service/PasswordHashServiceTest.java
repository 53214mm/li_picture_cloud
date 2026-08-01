package com.li.lipicturecloud.service;

import cn.hutool.crypto.digest.DigestUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordHashServiceTest {

    private final PasswordHashService service = new PasswordHashService();

    @Test
    void encodesAndMatchesWithBcrypt() {
        String encoded = service.encode("correct-password");
        assertThat(encoded).startsWith("$2").isNotEqualTo("correct-password");
        assertThat(service.matches("correct-password", encoded)).isTrue();
        assertThat(service.matches("wrong-password", encoded)).isFalse();
    }

    @Test
    void rejectsLegacyAndMalformedHashes() {
        assertThat(service.matches("correct-password",
                DigestUtil.md5Hex("correct-password" + "liPictureCloud2026"))).isFalse();
        assertThat(service.matches("correct-password", "")).isFalse();
    }
}
