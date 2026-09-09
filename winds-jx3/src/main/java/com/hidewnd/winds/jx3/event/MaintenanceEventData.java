package com.hidewnd.winds.jx3.event;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Maintenance 事件的固定业务载荷。 */
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
