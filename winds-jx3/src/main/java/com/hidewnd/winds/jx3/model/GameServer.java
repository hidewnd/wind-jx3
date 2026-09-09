package com.hidewnd.winds.jx3.model;

import java.util.List;

/** 同一连接地址对应的主服及合服别名。 */
public record GameServer(
        String zoneId,
        String zoneName,
        String serverName,
        List<String> aliases,
        String host,
        int port) {
    public String endpoint() {
        return host + ":" + port;
    }
}
