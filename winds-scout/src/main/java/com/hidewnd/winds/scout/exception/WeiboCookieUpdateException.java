package com.hidewnd.winds.scout.exception;

/**
 * 微博响应 Cookie 无法完整持久化时抛出的本地状态异常。
 */
public class WeiboCookieUpdateException extends RuntimeException {

    public WeiboCookieUpdateException(String message, Throwable cause) {
        super(message, cause);
    }
}
