package com.hidewnd.winds.jx3.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hidewnd.winds.jx3.client.OfficialClient;
import com.hidewnd.winds.jx3.event.Jx3Event;
import com.hidewnd.winds.jx3.event.Jx3EventFactory;
import com.hidewnd.winds.jx3.model.PatchManifest;
import com.hidewnd.winds.jx3.repository.Jx3RecordRepository;
import com.hidewnd.winds.jx3.service.PatchMonitorService;
import com.hidewnd.winds.jx3.support.Jx3Time;

import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** 基线只在完整补丁链及大小保存成功后前进；待处理版本保留首次发现时间。 */
public class PatchMonitorServiceImpl implements PatchMonitorService {
    private final OfficialClient client;
    private final Jx3RecordRepository store;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper mapper;
    private final Clock clock;
    private String previous;
    private String pending;
    private Instant observed;

    public PatchMonitorServiceImpl(
            OfficialClient client,
            Jx3RecordRepository store,
            ApplicationEventPublisher publisher,
            ObjectMapper mapper,
            Clock clock) {
        this.client = client;
        this.store = store;
        this.publisher = publisher;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public void poll() {
        PatchManifest manifest = client.fetchPatchManifest();
        boolean baseline = previous == null;
        if (baseline) {
            var saved = store.load("patch");
            if (saved != null
                    && saved.path("version").asText().equals(manifest.latestVersion())
                    && saved.hasNonNull("packageCount")
                    && saved.hasNonNull("totalBytes")
                    && saved.hasNonNull("updatedAt")) {
                previous = manifest.latestVersion();
                return;
            }
        }
        if (!baseline && PatchManifest.compareVersions(manifest.latestVersion(), previous) < 0) {
            throw new IllegalStateException("官方版本回退，保留已处理基线");
        }
        if (!baseline && pending == null && manifest.latestVersion().equals(previous)) {
            return;
        }
        if (pending == null || baseline && !pending.equals(manifest.latestVersion())) {
            pending = manifest.latestVersion();
            observed = clock.instant();
        }
        // 首轮没有升级起点，统计清单快照；后续仅统计本次实际升级链。
        var chain = baseline ? manifest.patches() : manifest.chain(previous, pending);
        long bytes = 0;
        Instant updated = null;
        for (var patch : chain) {
            var info = client.fetchPatchInfo(patch.fileName());
            bytes = Math.addExact(bytes, info.size());
            if (updated == null || info.updatedAt().isAfter(updated)) {
                updated = info.updatedAt();
            }
        }
        Jx3Event event =
                baseline
                        ? null
                        : Jx3EventFactory.createPatchEvent(
                                UUID.randomUUID().toString(),
                                observed,
                                previous,
                                pending,
                                chain.size(),
                                bytes);
        var state = mapper.createObjectNode().put("version", pending);
        state.set("packages", mapper.valueToTree(chain));
        state.put("totalBytes", bytes);
        state.put("packageCount", chain.size());
        state.put("updatedAt", Jx3Time.format(updated));
        state.put("updateTimeSource", "packageLastModified");
        state.put("packageScope", baseline ? "manifest" : "upgradeChain");
        boolean inserted = store.save("patch", state, event);
        previous = pending;
        pending = null;
        // 写入结果不确定后重试时，数据库可能已有同一状态；不广播未持久化的新事件 ID。
        if (inserted && event != null) {
            publisher.publishEvent(event);
        }
    }
}
