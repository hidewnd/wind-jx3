package com.hidewnd.winds.jx3;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import java.time.*;
import java.util.UUID;

/** 基线只在完整补丁链及大小保存成功后前进；待处理版本保留首次发现时间。 */
public class PatchMonitor {
    private final OfficialClient client;
    private final Jx3Store store;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper mapper;
    private final Clock clock;
    private String previous;
    private String pending;
    private Instant observed;

    public PatchMonitor(OfficialClient client, Jx3Store store, ApplicationEventPublisher publisher, ObjectMapper mapper, Clock clock) {
        this.client = client; this.store = store; this.publisher = publisher; this.mapper = mapper; this.clock = clock;
    }

    public void poll() {
        PatchManifest manifest = client.manifest();
        if (previous == null) {
            store.save("patch", mapper.createObjectNode().put("version", manifest.latestVersion()), null);
            previous = manifest.latestVersion();
            return;
        }
        if (PatchManifest.compareVersions(manifest.latestVersion(), previous) < 0) throw new IllegalStateException("官方版本回退，保留已处理基线");
        if (pending == null && manifest.latestVersion().equals(previous)) return;
        if (pending == null) { pending = manifest.latestVersion(); observed = clock.instant(); }
        var chain = manifest.chain(previous, pending);
        long bytes = 0;
        for (var patch : chain) bytes = Math.addExact(bytes, client.patchSize(patch.fileName()));
        Jx3Event event = Jx3Event.patch(UUID.randomUUID().toString(), observed, previous, pending, chain.size(), bytes);
        var state = mapper.createObjectNode().put("version", pending);
        state.set("packages", mapper.valueToTree(chain));
        state.put("totalBytes", bytes);
        boolean inserted = store.save("patch", state, event);
        previous = pending;
        pending = null;
        // 写入结果不确定后重试时，数据库可能已有同一状态；不广播未持久化的新事件 ID。
        if (inserted) publisher.publishEvent(event);
    }
}
