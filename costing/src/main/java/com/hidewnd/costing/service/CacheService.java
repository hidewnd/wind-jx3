package com.hidewnd.costing.service;

import java.util.concurrent.TimeUnit;

public interface CacheService {
    // 设置键值对
    void set(String key, Object value);

    // 设置键值对并指定过期时间
    void set(String key, Object value, long timeout, TimeUnit unit);

    // 设置键值对并指定过期时间
    void set(String key, Object value, long seconds);

    // 获取值
    Object get(String key);

    // 获取值
    String getString(String key);

    // 删除键
    Boolean delete(String key);

    // 判断键是否存在
    Boolean hasKey(String key);

    // 如果不存在，则设置
    Boolean setNx(String key, Object value);

    // 如果不存在，则设置，附带过期时间
    Boolean tryLock(String lockKey, String requestId, long seconds);
}
