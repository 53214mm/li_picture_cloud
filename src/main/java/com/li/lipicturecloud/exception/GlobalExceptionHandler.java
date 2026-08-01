package com.li.lipicturecloud.exception;

import com.li.lipicturecloud.common.BaseResponse;
import com.li.lipicturecloud.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<BaseResponse<?>> handleBusinessException(BusinessException e) {
        log.error("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(resolveHttpStatus(e.getCode()))
                .body(ResultUtils.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<?>> handleException(Exception e) {
        log.error("系统异常: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResultUtils.error(ErrorCode.SYSTEM_ERROR.getCode(), "系统内部异常"));
    }

    private HttpStatus resolveHttpStatus(int code) {
        if (code == ErrorCode.PARAMS_ERROR.getCode()) {
            return HttpStatus.BAD_REQUEST;
        }
        if (code == ErrorCode.NOT_LOGIN_ERROR.getCode()) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (code == ErrorCode.NO_AUTH_ERROR.getCode() || code == ErrorCode.FORBIDDEN_ERROR.getCode()) {
            return HttpStatus.FORBIDDEN;
        }
        if (code == ErrorCode.NOT_FOUND_ERROR.getCode()) {
            return HttpStatus.NOT_FOUND;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
