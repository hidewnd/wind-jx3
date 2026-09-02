package com.hidewnd.winds.scout.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * 创建微博监控博主的请求参数。
 */
@Schema(description = "微博监控博主创建参数")
public record BloggerCreateRequest(
        @Schema(description = "微博 UID，仅支持数字", example = "1761587065")
        @NotBlank(message = "UID不能为空") @Pattern(regexp = "\\d+", message = "UID仅支持数字") String uid,
        @Schema(description = "博主显示名称", example = "剑网3官方微博")
        String screenName,
        @Schema(description = "博主别称", example = "[\"官博\"]")
        List<@NotBlank(message = "别称不能为空") String> aliases,
        @Schema(description = "是否启用监控，空值默认为 true", example = "true")
        Boolean enabled) {
}
