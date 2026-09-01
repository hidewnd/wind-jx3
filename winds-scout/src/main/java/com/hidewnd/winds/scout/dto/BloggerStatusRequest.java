package com.hidewnd.winds.scout.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 修改微博监控博主启用状态的请求参数。
 */
@Schema(description = "微博监控博主状态修改参数")
public record BloggerStatusRequest(
        @Schema(description = "是否启用监控", example = "true")
        @NotNull(message = "enabled不能为空") Boolean enabled) {
}
