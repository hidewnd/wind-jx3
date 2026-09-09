package com.hidewnd.winds.scout.exception;

/**
 * 微博明确拒绝当前登录凭据，账号需人工更新凭据后恢复。
 * 使用固定消息，避免外部响应中的凭据或其他敏感内容进入日志和通知。
 */
public class WeiboAccountInvalidException extends IllegalStateException {

    public WeiboAccountInvalidException() {
        super("微博账号登录凭据无效，请更新Cookie后重试");
    }
}
