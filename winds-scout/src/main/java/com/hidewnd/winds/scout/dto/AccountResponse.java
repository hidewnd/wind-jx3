package com.hidewnd.winds.scout.dto;

import com.hidewnd.winds.scout.model.WeiboAccount;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 不包含 Cookie、XSRF Token 和错误详情的抓取账号响应。
 */
@Schema(description = "微博抓取账号状态，不包含登录凭据")
public record AccountResponse(
        @Schema(description = "抓取账号唯一 ID", example = "account-1")
        String id,
        @Schema(description = "账号状态", allowableValues = {"active", "fail"})
        String status,
        @Schema(description = "连续失败次数", example = "0")
        int failCount,
        @Schema(description = "累计请求次数", example = "12")
        long requestCount,
        @Schema(description = "最近失败时间，格式为 yyyy-MM-dd HH:mm:ss")
        Instant lastErrorAt,
        @Schema(description = "最近使用时间，格式为 yyyy-MM-dd HH:mm:ss")
        Instant lastUsedAt,
        @Schema(description = "自动恢复时间，格式为 yyyy-MM-dd HH:mm:ss，空值表示不自动恢复")
        Instant recoverAt,
        @Schema(description = "创建时间，格式为 yyyy-MM-dd HH:mm:ss")
        Instant createdAt,
        @Schema(description = "更新时间，格式为 yyyy-MM-dd HH:mm:ss")
        Instant updatedAt) {

    /**
     * 将账号实体转换为脱敏响应。
     *
     * @param account 微博抓取账号实体
     * @return 不包含登录凭据的账号响应
     */
    public static AccountResponse from(WeiboAccount account) {
        return new AccountResponse(
                account.getId(), account.getStatus(), account.getFailCount(), account.getRequestCount(),
                account.getLastErrorAt(), account.getLastUsedAt(),
                account.getRecoverAt(), account.getCreatedAt(), account.getUpdatedAt());
    }
}
