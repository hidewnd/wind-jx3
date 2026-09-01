package com.hidewnd.winds.scout.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 更新微博监控博主配置的请求参数。
 */
@Schema(description = "微博监控博主更新参数")
public record BloggerUpdateRequest(
        @Schema(description = "博主显示名称", example = "剑网3官方微博")
        String screenName,
        @Schema(description = "博主别称")
        List<@NotBlank(message = "别称不能为空") String> aliases,
        @Schema(description = "是否启用监控")
        Boolean enabled) {
}
