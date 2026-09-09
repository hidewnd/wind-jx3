package com.hidewnd.winds.jx3.event;

import com.fasterxml.jackson.annotation.JsonInclude;

/** News 事件的固定业务载荷。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record NewsEventData(
        String articleId,
        String categoryId,
        String changeType,
        String title,
        String summary,
        String url,
        String publishedAt,
        String updatedAt) {}
