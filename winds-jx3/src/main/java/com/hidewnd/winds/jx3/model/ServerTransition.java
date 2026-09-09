package com.hidewnd.winds.jx3.model;

import java.time.Instant;

/** 经连续探测确认的状态变化，时间为首次观察到候选状态的时刻。 */
public record ServerTransition(String status, Instant observed) {}
