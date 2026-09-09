package com.hidewnd.winds.scout.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.AssertTrue;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

/**
 * 订阅博主的请求参数；UID 缺省时通过微博接口精确匹配全称。
 */
@Schema(description = "微博监控博主创建参数")
public record BloggerCreateRequest(
        @Schema(description = "微博 UID，仅支持数字", example = "1761587065")
        @Pattern(regexp = "\\d+", message = "UID仅支持数字") String uid,
        @Schema(description = "博主全称；未传 UID 时用于微博用户查询，传 UID 时仅用于首次建档", example = "剑网3")
        String screenName,
        @Schema(description = "博主别称", example = "[\"官博\"]")
        List<@NotBlank(message = "别称不能为空") String> aliases) {

    @JsonIgnore
    @AssertTrue(message = "UID和博主全称至少填写一项")
    public boolean isTargetProvided() {
        return uid != null && !uid.isBlank() || screenName != null && !screenName.isBlank();
    }
}
