package com.hidewnd.winds.jx3.service;

/** 监听服务契约；调度层只依赖接口。 */
public interface ServerMonitorService extends AutoCloseable {
    void poll();

    @Override
    void close();
}
