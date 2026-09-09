package com.hidewnd.winds.jx3.event;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Patch 事件的固定业务载荷。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PatchEventData(
        String previousVersion, String version, int packageCount, long totalBytes) {}
