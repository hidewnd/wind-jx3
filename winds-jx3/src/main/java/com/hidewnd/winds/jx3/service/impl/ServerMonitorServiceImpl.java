package com.hidewnd.winds.jx3.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hidewnd.winds.jx3.client.OfficialClient;
import com.hidewnd.winds.jx3.client.TcpServerProbe;
import com.hidewnd.winds.jx3.event.Jx3Event;
import com.hidewnd.winds.jx3.event.Jx3EventFactory;
import com.hidewnd.winds.jx3.model.GameServer;
import com.hidewnd.winds.jx3.model.ProbeStatus;
import com.hidewnd.winds.jx3.model.ServerProbeResult;
import com.hidewnd.winds.jx3.model.ServerStateTracker;
import com.hidewnd.winds.jx3.model.ServerTransition;
import com.hidewnd.winds.jx3.parser.ServerListParser;
import com.hidewnd.winds.jx3.repository.Jx3RecordRepository;
import com.hidewnd.winds.jx3.service.ServerMonitorService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** 编排清单刷新、网关探测、状态确认与持久化后的事件发布。 */
@Slf4j
public class ServerMonitorServiceImpl implements ServerMonitorService {
    private final OfficialClient client;
    private final Jx3RecordRepository repository;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final TcpServerProbe probe;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Map<String, ServerStateTracker> trackers = new HashMap<>();
    private List<GameServer> servers = List.of();
    private Instant refreshedAt;

    public ServerMonitorServiceImpl(
            OfficialClient client,
            Jx3RecordRepository repository,
            ApplicationEventPublisher publisher,
            ObjectMapper mapper,
            Clock clock,
            TcpServerProbe probe) {
        this.client = client;
        this.repository = repository;
        this.publisher = publisher;
        this.mapper = mapper;
        this.clock = clock;
        this.probe = probe;
    }

    @Override
    public void poll() {
        Instant now = clock.instant();
        if (refreshedAt == null || !now.isBefore(refreshedAt.plusSeconds(3600))) {
            refreshServers(now);
        }
        List<ServerProbeResult> results = probeServers(now);
        for (int index = 0; index < servers.size(); index++) {
            GameServer server = servers.get(index);
            ServerProbeResult result = results.get(index);
            ServerStateTracker tracker =
                    trackers.computeIfAbsent(
                            server.endpoint(), ignored -> new ServerStateTracker());
            ServerTransition transition = tracker.observe(result.status(), now);
            if (result.status() == ProbeStatus.UNKNOWN) {
                log.warn(
                        "区服探测结果未知，大区={}，服务器={}，地址={}，耗时={}ms，异常类型={}，原因={}；不据此判断维护",
                        server.zoneName(),
                        server.serverName(),
                        server.endpoint(),
                        result.elapsedMillis(),
                        result.failureType(),
                        result.detail());
                continue;
            }
            if (tracker.getConfirmedStatus() == null) {
                continue;
            }
            String status = transition == null ? tracker.getConfirmedStatus() : transition.status();
            var state = mapper.createObjectNode().put("status", status);
            state.set("server", mapper.valueToTree(server));
            Jx3Event event =
                    transition == null
                            ? null
                            : Jx3EventFactory.createServerEvent(
                                    UUID.randomUUID().toString(),
                                    transition.observed(),
                                    clock.instant(),
                                    server,
                                    tracker.getConfirmedStatus(),
                                    status);
            boolean inserted =
                    repository.save(
                            "server:" + server.zoneId() + ":" + server.serverName(), state, event);
            if (transition != null) {
                tracker.confirm(status);
                if (inserted) {
                    publisher.publishEvent(event);
                }
            }
        }
    }

    private void refreshServers(Instant now) {
        List<GameServer> updated;
        try {
            updated = ServerListParser.parse(client.fetchServerList());
        } catch (RuntimeException exception) {
            trackers.values().forEach(tracker -> tracker.observe(ProbeStatus.UNKNOWN, now));
            throw exception;
        }
        trackers.keySet().retainAll(updated.stream().map(GameServer::endpoint).toList());
        servers = updated;
        refreshedAt = now;
    }

    private List<ServerProbeResult> probeServers(Instant now) {
        List<Callable<ServerProbeResult>> tasks =
                servers.stream()
                        .<Callable<ServerProbeResult>>map(server -> () -> probe.check(server))
                        .toList();
        List<ServerProbeResult> results = new ArrayList<>();
        try {
            for (Future<ServerProbeResult> future : executor.invokeAll(tasks)) {
                results.add(future.get());
            }
            return results;
        } catch (InterruptedException exception) {
            trackers.values().forEach(tracker -> tracker.observe(ProbeStatus.UNKNOWN, now));
            Thread.currentThread().interrupt();
            throw new IllegalStateException("区服探测被中断", exception);
        } catch (ExecutionException exception) {
            trackers.values().forEach(tracker -> tracker.observe(ProbeStatus.UNKNOWN, now));
            throw new IllegalStateException("区服探测异常", exception.getCause());
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
