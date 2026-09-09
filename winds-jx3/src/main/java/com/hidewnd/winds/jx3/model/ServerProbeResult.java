package com.hidewnd.winds.jx3.model;

/** 保留网络失败证据，避免未知结果只剩计数而无法定位。 */
public record ServerProbeResult(
        ProbeStatus status, String failureType, String detail, long elapsedMillis) {}
