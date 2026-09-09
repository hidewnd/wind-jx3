package com.hidewnd.winds.scout.dto;

import com.hidewnd.winds.scout.model.WeiboBlogger;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * 微博监控博主的管理接口响应。
 */
@Schema(description = "微博监控博主信息")
public record BloggerResponse(
        @Schema(description = "微博 UID", example = "1761587065")
        String uid,
        @Schema(description = "博主显示名称", example = "剑网3官方微博")
        String screenName,
        @Schema(description = "博主头像地址；历史记录可能为空")
        String avatar,
        @Schema(description = "博主别称")
        List<String> aliases,
        @Schema(description = "创建时间，格式为 yyyy-MM-dd HH:mm:ss")
        Instant createdAt,
        @Schema(description = "更新时间，格式为 yyyy-MM-dd HH:mm:ss")
        Instant updatedAt) {

    /**
     * 将博主实体转换为管理接口响应。
     *
     * @param blogger 微博监控博主实体
     * @return 博主管理响应
     */
    public static BloggerResponse from(WeiboBlogger blogger) {
        return new BloggerResponse(
                blogger.getUid(),
                blogger.getScreenName(),
                blogger.getAvatar(),
                blogger.getAliases() == null ? List.of() : List.copyOf(blogger.getAliases()),
                blogger.getCreatedAt(),
                blogger.getUpdatedAt());
    }
}
