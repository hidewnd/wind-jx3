package com.hidewnd.winds.scout.service;

/**
 * 微博监控轮询服务。
 */
public interface WeiboMonitorService {

    /**
     * 获取轮询租约并依次检查所有已启用博主，保存新微博并发布更新事件。
     */
    void poll();
}
