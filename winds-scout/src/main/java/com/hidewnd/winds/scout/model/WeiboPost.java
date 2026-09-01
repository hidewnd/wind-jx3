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
        @Schema(description = "移除 HTML 并保留段落、换行和有效空白的纯文本正文")
        String content,
        @Schema(description = "微博接口返回的原始正文 HTML，属于不可信外部内容", nullable = true)
        String rawContent,
        @Schema(description = "发布来源", nullable = true)
        String source,
        @Schema(description = "发布地区", nullable = true)
        String regionName,
        @Schema(description = "转发数", nullable = true)
        Long repostsCount,
        @Schema(description = "评论数", nullable = true)
        Long commentsCount,
        @Schema(description = "点赞数", nullable = true)
        Long attitudesCount,
        @Schema(description = "不含查询参数的微博详情地址")
        String url,
        @Schema(description = "正文图片及卡片、视频封面地址")
        List<String> images,
        @Schema(description = "微博话题")
        List<String> topics,
        @Schema(description = "卡片和视频封面地址")
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
            @Schema(description = "移除 HTML 并保留段落、换行和有效空白的转发纯文本正文")
            String content,
            @Schema(description = "微博接口返回的转发原文 HTML，属于不可信外部内容", nullable = true)
            String rawContent,
            @Schema(description = "转发原文发布来源", nullable = true)
            String source,
            @Schema(description = "转发原文发布地区", nullable = true)
            String regionName,
            @Schema(description = "转发原文的转发数", nullable = true)
            Long repostsCount,
            @Schema(description = "转发原文的评论数", nullable = true)
            Long commentsCount,
            @Schema(description = "转发原文的点赞数", nullable = true)
            Long attitudesCount,
            @Schema(description = "转发微博图片及卡片、视频封面地址")
            List<String> images,
            @Schema(description = "转发微博卡片和视频封面地址")
            List<String> videoCoverImages) {
    }
}
