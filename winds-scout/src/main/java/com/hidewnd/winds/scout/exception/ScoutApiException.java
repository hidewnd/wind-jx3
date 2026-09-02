package com.hidewnd.winds.scout.exception;

import org.springframework.http.HttpStatus;

/**
 * Scout 管理接口业务异常，携带应返回给调用方的 HTTP 状态码。
 */
public class ScoutApiException extends RuntimeException {

    private final HttpStatus status;

    /**
     * 创建 Scout 管理接口业务异常。
     *
     * @param status  HTTP 响应状态
     * @param message 面向调用方的错误信息
     */
    public ScoutApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * 获取异常对应的 HTTP 状态。
     *
     * @return HTTP 状态
     */
    public HttpStatus getStatus() {
        return status;
    }
}
