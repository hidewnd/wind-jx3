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
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import lombok.extern.slf4j.Slf4j;

/**
 * 微博监控管理接口异常处理器，将业务异常和参数校验异常转换为统一响应。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
@RestControllerAdvice(assignableTypes = WeiboManagementController.class)
public class ScoutApiExceptionHandler {

    /** 缺失查询参数和非法 JSON 统一返回 400，不回显原始输入。 */
    @ExceptionHandler({MissingServletRequestParameterException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<R<Object>> handleMalformedRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "请求参数缺失或格式错误");
    }

    /** 数据库错误可能包含查询 Token，只记录异常类型，不向客户端泄漏查询详情。 */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<R<Object>> handleUnexpected(RuntimeException exception) {
        log.error("微博接口处理失败，异常类型={}", exception.getClass().getSimpleName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "微博服务处理失败");
    }

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
