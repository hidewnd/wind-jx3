package com.hidewnd.winds.jx3.service;

import com.hidewnd.winds.jx3.model.ServerOpening;
import java.util.concurrent.CompletableFuture;

/** 监听服务契约；调度层只依赖接口。 */
public interface ServerMonitorService {
    void poll();

    CompletableFuture<Void> verifyOpening(ServerOpening opening);
}
