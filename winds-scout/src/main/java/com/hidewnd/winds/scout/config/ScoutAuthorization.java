package com.hidewnd.winds.scout.config;

/**
 * Scout 管理令牌的鉴权结果。
 */
public enum ScoutAuthorization {
    /** 令牌有效且具有微博管理权限。 */
    AUTHORIZED,
    /** 令牌缺失、无效或已停用。 */
    INVALID,
    /** 令牌有效，但缺少微博管理权限。 */
    FORBIDDEN
}
