package com.hidewnd.winds.scout.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 修改微博抓取账号状态的请求参数。
 */
@Schema(description = "微博抓取账号状态修改参数")
public record AccountStatusRequest(
        @Schema(description = "账号状态", allowableValues = {"active", "fail"}, example = "active")
        @NotBlank(message = "status不能为空")
        @Pattern(regexp = "active|fail", message = "status仅支持active或fail") String status) {
}
