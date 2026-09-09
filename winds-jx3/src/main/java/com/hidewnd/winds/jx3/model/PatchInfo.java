package com.hidewnd.winds.jx3.model;

import java.time.Instant;

/** 官方 HEAD 响应中的文件大小与最后修改时间。 */
public record PatchInfo(long size, Instant updatedAt) {}
