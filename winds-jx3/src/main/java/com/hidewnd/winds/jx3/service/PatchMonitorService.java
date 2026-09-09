package com.hidewnd.winds.jx3.service;

/** 监听服务契约；调度层只依赖接口。 */
public interface PatchMonitorService {
    void poll();
}
