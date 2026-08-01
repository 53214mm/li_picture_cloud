package com.li.lipicturecloud.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class PasswordHashService {

    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;
    private static final String PASSWORD_TOO_LONG_MESSAGE = "密码不能超过 72 个 UTF-8 字节";
    private static final String BCRYPT_COST_12_PATTERN = "^\\$2[ayb]\\$12\\$.*";

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    public String encode(String rawPassword) {
        validateRawPassword(rawPassword);
        return passwordEncoder.encode(rawPassword);
    }

    public void validateRawPassword(String rawPassword) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException(PASSWORD_TOO_LONG_MESSAGE);
        }
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        if (encodedPassword == null || !encodedPassword.matches(BCRYPT_COST_12_PATTERN)) {
            return false;
        }
        try {
            return passwordEncoder.matches(rawPassword, encodedPassword);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
