package com.hidewnd.winds.jx3.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Server 事件的固定业务载荷。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ServerEventData(
        String zoneId,
        String zoneName,
        String serverName,
        List<String> aliases,
        String previousStatus,
        String status,
        String detectedBy,
        String confirmedAt) {}
