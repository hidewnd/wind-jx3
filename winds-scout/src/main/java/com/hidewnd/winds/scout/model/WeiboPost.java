package com.hidewnd.winds.scout.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

/**
 * 从微博接口解析并用于持久化、广播的微博内容。
 */
@Schema(description = "微博更新事件内容")
public record WeiboPost(
        @Schema(description = "微博 UID")
        String uid,
        @Schema(description = "博主显示名称")
        @Field("title")
        String screenName,
        @Schema(description = "微博 ID")
        @Id
        String weiboId,
        @Schema(description = "发布时间，格式为 yyyy-MM-dd HH:mm:ss")
        @Field("date")
        String publishedAt,
        @Schema(description = "移除 HTML 并保留段落、换行和有效空白的纯文本正文")
        String content,
        @Schema(description = "微博接口返回的原始正文 HTML，属于不可信外部内容", nullable = true)
        @Field("raw_content")
        String rawContent,
        @Schema(description = "发布来源", nullable = true)
        String source,
        @Schema(description = "发布地区", nullable = true)
        @Field("region_name")
        String regionName,
        @Schema(description = "转发数", nullable = true)
        @Field("reposts_count")
        Long repostsCount,
        @Schema(description = "评论数", nullable = true)
        @Field("comments_count")
        Long commentsCount,
        @Schema(description = "点赞数", nullable = true)
        @Field("attitudes_count")
        Long attitudesCount,
        @Schema(description = "不含查询参数的微博详情地址")
        String url,
        @Schema(description = "正文图片及卡片、视频封面地址")
        @Field("imgs")
        List<String> images,
        @Schema(description = "微博话题")
        List<String> topics,
        @Schema(description = "卡片和视频封面地址")
        @Field("video_cover_imgs")
        List<String> videoCoverImages,
        @Schema(description = "转发微博内容，非转发微博为空")
        Retweet retweet,
        @Schema(description = "按上游顺序提取的图片、视频和 Live Photo")
        List<Media> media,
        @Schema(description = "正文链接、用户提及、话题和特殊内容卡片")
        List<Link> links,
        @Schema(description = "头条文章；未提供全文时仍保留卡片摘要", nullable = true)
        Article article,
        @Schema(description = "上游标记为长文但尚未取得完整正文")
        boolean truncated) {

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
            List<String> videoCoverImages,
            String uid,
            String weiboId,
            String publishedAt,
            String url,
            List<String> topics,
            List<Media> media,
            List<Link> links,
            Article article,
            boolean truncated,
            @Schema(description = "原微博被删除或作者信息不可访问")
            boolean unavailable,
            @Schema(description = "更深层的转发，最多保留五层")
            Retweet retweet) {
    }

    @Schema(description = "媒体资源，URL 保留签名参数；livephoto 的 url 是视频，coverUrl 是静态图片")
    public record Media(String type, String url, String coverUrl) {
    }

    @Schema(description = "链接及卡片；type 保留上游类型，未知类型仍可通过 url 访问")
    public record Link(String type, String title, String url, String description, String image) {
    }

    @Schema(description = "头条文章，rawContent 是不可信原始 HTML；未返回的全文和付费标志为 null")
    public record Article(String title, String url, String summary, String content, String rawContent,
                          String publishedAt, Boolean paid, Boolean trial) {
    }
}
