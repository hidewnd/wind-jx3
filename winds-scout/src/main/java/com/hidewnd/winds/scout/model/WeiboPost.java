package com.hidewnd.winds.scout.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 从微博接口解析并用于持久化、广播的微博内容。
 */
@Schema(description = "微博更新事件内容")
public record WeiboPost(
        @Schema(description = "微博 UID")
        String uid,
        @Schema(description = "博主显示名称")
        String screenName,
        @Schema(description = "微博 ID")
        String weiboId,
        @Schema(description = "发布时间，格式为 yyyy-MM-dd HH:mm:ss")
        String publishedAt,
        @Schema(description = "去除 HTML 标签和话题标记后的正文")
        String content,
        @Schema(description = "不含查询参数的微博详情地址")
        String url,
        @Schema(description = "正文图片和视频封面地址")
        List<String> images,
        @Schema(description = "微博话题")
        List<String> topics,
        @Schema(description = "视频封面地址")
        List<String> videoCoverImages,
        @Schema(description = "转发微博内容，非转发微博为空")
        Retweet retweet) {

    /**
     * 转发微博的原始作者、正文及媒体资源。
     */
    @Schema(description = "转发微博内容")
    public record Retweet(
            @Schema(description = "原作者显示名称")
            String screenName,
            @Schema(description = "转发微博正文")
            String content,
            @Schema(description = "转发微博图片和视频封面地址")
            List<String> images,
            @Schema(description = "转发微博视频封面地址")
            List<String> videoCoverImages) {
    }
}
