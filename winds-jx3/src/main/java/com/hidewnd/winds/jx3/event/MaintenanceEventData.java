package com.hidewnd.winds.jx3.event;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 官方公告载荷；沿用 Maintenance 事件契约，普通公告的维护状态为 unknown、时间为 null。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record MaintenanceEventData(
        String articleId,
        String changeType,
        String title,
        String summary,
        String url,
        String publishedAt,
        String updatedAt,
        String maintenanceStatus,
        String startsAt,
        String expectedEndsAt) {}
