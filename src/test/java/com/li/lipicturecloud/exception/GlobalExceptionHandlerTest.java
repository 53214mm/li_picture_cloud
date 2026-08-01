package com.li.lipicturecloud.exception;

import com.li.lipicturecloud.common.BaseResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsBusinessCodesToHttpStatus() {
        assertStatus(ErrorCode.PARAMS_ERROR, 400);
        assertStatus(ErrorCode.NOT_LOGIN_ERROR, 401);
        assertStatus(ErrorCode.NO_AUTH_ERROR, 403);
        assertStatus(ErrorCode.FORBIDDEN_ERROR, 403);
        assertStatus(ErrorCode.NOT_FOUND_ERROR, 404);
        assertStatus(ErrorCode.OPERATION_ERROR, 500);
    }

    @Test
    void hidesUnexpectedExceptionDetails() {
        ResponseEntity<BaseResponse<?>> response = handler.handleException(new RuntimeException("database password"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("系统内部异常");
    }

    private void assertStatus(ErrorCode errorCode, int expectedStatus) {
        ResponseEntity<BaseResponse<?>> response = handler.handleBusinessException(new BusinessException(errorCode));
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(errorCode.getCode());
    }
}
