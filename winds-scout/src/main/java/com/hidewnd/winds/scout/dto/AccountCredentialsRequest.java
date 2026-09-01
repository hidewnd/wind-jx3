package com.hidewnd.winds.scout.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 更新微博抓取账号登录凭据的请求参数。
 */
@Schema(description = "微博抓取账号凭据更新参数")
public record AccountCredentialsRequest(
        @Schema(description = "包含 XSRF-TOKEN 的微博登录 Cookie", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank(message = "Cookie不能为空") String cookie) {
}
