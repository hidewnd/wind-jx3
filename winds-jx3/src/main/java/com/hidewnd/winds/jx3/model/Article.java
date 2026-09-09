package com.hidewnd.winds.jx3.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 已解析的官方文章；完整正文与实际发布时间用于持久化。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record Article(
        String articleId,
        String categoryId,
        String title,
        String summary,
        String url,
        String publishedAt,
        String updatedAt,
        String content,
        boolean maintenance,
        String maintenanceStatus,
        String startsAt,
        String expectedEndsAt) {}
