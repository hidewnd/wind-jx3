package com.hidewnd.winds.scout.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 创建微博抓取账号的请求参数。
 */
@Schema(description = "微博抓取账号创建参数")
public record AccountCreateRequest(
        @Schema(description = "抓取账号唯一 ID", example = "account-1")
        @NotBlank(message = "账号ID不能为空") String id,
        @Schema(description = "包含 XSRF-TOKEN 的微博登录 Cookie", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank(message = "Cookie不能为空") String cookie) {
}
