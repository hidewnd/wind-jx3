package com.hidewnd.winds.scout.controller;

import com.hidewnd.winds.common.base.response.R;
import com.hidewnd.winds.scout.exception.ScoutApiException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 微博监控管理接口异常处理器，将业务异常和参数校验异常转换为统一响应。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = WeiboManagementController.class)
public class ScoutApiExceptionHandler {

    /**
     * 处理携带 HTTP 状态的 Scout 业务异常。
     *
     * @param exception Scout 业务异常
     * @return 对应状态码的统一错误响应
     */
    @ExceptionHandler(ScoutApiException.class)
    public ResponseEntity<R<Object>> handle(ScoutApiException exception) {
        return response(exception.getStatus(), exception.getMessage());
    }

    /**
     * 处理请求体字段校验失败。
     *
     * @param exception 参数校验异常
     * @return 400 参数错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Object>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> "[" + error.getField() + "]" + error.getDefaultMessage())
                .reduce((left, right) -> left + "," + right)
                .orElse("请求参数错误");
        return response(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseEntity<R<Object>> response(HttpStatus status, String message) {
        R<Object> body = R.error(status.value(), message);
        body.setSuccess(false);
        return ResponseEntity.status(status).body(body);
    }
}
