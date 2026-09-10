package com.hidewnd.winds.jx3.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** 统一应用下的剑三采集配置，运行环境不影响输入协议编码。 */
@ConfigurationProperties("winds.jx3")
public record Jx3Properties(
        @DefaultValue("30s") Duration newsInterval,
        @DefaultValue("30s") Duration maintenanceInterval,
        @DefaultValue("10s") Duration serverInterval,
        @DefaultValue("30s") Duration patchInterval,
        @DefaultValue("3s") Duration serverConnectTimeout) {

    public Jx3Properties {
        for (Duration interval :
                new Duration[] {newsInterval, maintenanceInterval, serverInterval, patchInterval}) {
            if (interval == null || interval.compareTo(Duration.ofSeconds(1)) < 0) {
                throw new IllegalArgumentException("剑三轮询间隔不得小于一秒");
            }
        }
        if (serverConnectTimeout == null
                || serverConnectTimeout.toMillis() <= 0
                || serverConnectTimeout.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("区服连接超时必须在 1 到 2147483647 毫秒之间");
        }
    }
}
