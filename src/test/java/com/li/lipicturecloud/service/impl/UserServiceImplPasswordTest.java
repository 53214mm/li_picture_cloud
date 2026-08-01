package com.li.lipicturecloud.service.impl;

import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.model.dto.user.UserAddRequest;
import com.li.lipicturecloud.model.dto.user.UserRegisterRequest;
import com.li.lipicturecloud.service.PasswordHashService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceImplPasswordTest {

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl();
        ReflectionTestUtils.setField(service, "passwordHashService", new PasswordHashService());
    }

    @Test
    void registrationRejectsPasswordLongerThan72Utf8BytesAsParamsError() {
        String password = passwordWith73Utf8Bytes();
        UserRegisterRequest request = new UserRegisterRequest();
        request.setUserAccount("test-account");
        request.setUserPassword(password);
        request.setCheckPassword(password);

        assertPasswordTooLong(() -> service.userRegister(request));
    }

    @Test
    void administratorCreationRejectsPasswordLongerThan72Utf8BytesAsParamsError() {
        UserAddRequest request = new UserAddRequest();
        request.setUserAccount("test-account");
        request.setUserPassword(passwordWith73Utf8Bytes());

        assertPasswordTooLong(() -> service.addUser(request));
    }

    private String passwordWith73Utf8Bytes() {
        String password = "a".repeat(70) + "你";
        assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(73);
        return password;
    }

    private void assertPasswordTooLong(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(ErrorCode.PARAMS_ERROR.getCode());
                    assertThat(exception).hasMessage("密码不能超过 72 个 UTF-8 字节");
                });
    }
}
