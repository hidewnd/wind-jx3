package com.hidewnd.winds.jx3.event;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 共享 WebSocket 的剑三事件信封，不承担采集或消息构造。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record Jx3Event(
        String type, String eventId, String occurredAt, String message, Object data) {}
