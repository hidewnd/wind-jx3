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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executor;
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
    private final Executor executor;
    private final Map<String, ServerStateTracker> trackers = new HashMap<>();
    private List<GameServer> servers = List.of();
    private Instant nextRefreshAt;

    public ServerMonitorServiceImpl(
            OfficialClient client,
            Jx3RecordRepository repository,
            ApplicationEventPublisher publisher,
            ObjectMapper mapper,
            Clock clock,
            TcpServerProbe probe,
            Executor executor) {
        this.client = client;
        this.repository = repository;
        this.publisher = publisher;
        this.mapper = mapper;
        this.clock = clock;
        this.probe = probe;
        this.executor = executor;
    }

    @Override
    public void poll() {
        Instant now = clock.instant();
        if (nextRefreshAt == null || !now.isBefore(nextRefreshAt)) {
            refreshServers(now);
        }
        // 每个网关独立进行有超时的 TCP 探测，按完成顺序处理，避免慢网关阻塞快网关推送。
        var completed = new ExecutorCompletionService<ProbeObservation>(executor);
        List<Future<ProbeObservation>> pending = new ArrayList<>();
        try {
            for (GameServer server : servers) {
                pending.add(completed.submit(() -> {
                    ServerProbeResult result = probe.check(server);
                    return new ProbeObservation(server, result, clock.instant());
                }));
            }
            for (int index = 0; index < servers.size(); index++) {
                ProbeObservation observation = completed.take().get();
                acceptProbe(observation.server(), observation.result(), observation.observedAt());
            }
        } catch (InterruptedException exception) {
            trackers.values().forEach(tracker -> tracker.observe(ProbeStatus.UNKNOWN, clock.instant()));
            Thread.currentThread().interrupt();
            throw new IllegalStateException("区服探测被中断", exception);
        } catch (ExecutionException exception) {
            trackers.values().forEach(tracker -> tracker.observe(ProbeStatus.UNKNOWN, clock.instant()));
            throw new IllegalStateException("区服探测异常", exception.getCause());
        } finally {
            pending.forEach(future -> future.cancel(true));
        }
    }

    private void acceptProbe(GameServer server, ServerProbeResult result, Instant observedAt) {
        ServerStateTracker tracker =
                trackers.computeIfAbsent(
                        server.endpoint(), ignored -> new ServerStateTracker());
        String previousStatus = tracker.getConfirmedStatus();
        ServerTransition transition = tracker.observe(result.status(), observedAt);
        // 明确结果也要可追踪，否则线上只剩无关网关的超时日志，无法区分漏检测和漏投递。
        log.debug("区服探测完成，大区={}，服务器={}，地址={}，结果={}，已确认状态={}，耗时={}ms",
                server.zoneName(), server.serverName(), server.endpoint(), result.status(),
                previousStatus, result.elapsedMillis());
        if (result.status() == ProbeStatus.UNKNOWN) {
            log.warn(
                    "区服探测结果未知，大区={}，服务器={}，地址={}，耗时={}ms，异常类型={}，原因={}；不据此判断维护",
                    server.zoneName(),
                    server.serverName(),
                    server.endpoint(),
                    result.elapsedMillis(),
                    result.failureType(),
                    result.detail());
            return;
        }
        if (tracker.getConfirmedStatus() == null) {
            return;
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
        if (previousStatus == null) {
            log.info("区服基线已建立，大区={}，服务器={}，地址={}，状态={}；首次确认不推送",
                    server.zoneName(), server.serverName(), server.endpoint(), status);
        }
        if (transition != null) {
            tracker.confirm(status);
            if (inserted) {
                log.info("区服状态变化已保存，大区={}，服务器={}，地址={}，状态={}->{}，事件={}",
                        server.zoneName(), server.serverName(), server.endpoint(),
                        previousStatus, status, event.eventId());
                publisher.publishEvent(event);
            } else {
                log.info("区服状态变化记录已存在，服务器={}，状态={}->{}；不重复推送",
                        server.serverName(), previousStatus, status);
            }
        }
    }

    private void refreshServers(Instant now) {
        List<GameServer> updated;
        try {
            updated = ServerListParser.parse(client.fetchServerList());
        } catch (RuntimeException exception) {
            if (servers.isEmpty()) {
                throw exception;
            }
            // 清单刷新故障不等于网关状态未知；保留有效清单和候选状态，一分钟后重试刷新。
            nextRefreshAt = now.plusSeconds(60);
            log.warn("官方区服清单刷新失败，继续探测上次有效清单，数量={}，下次刷新={}",
                    servers.size(), nextRefreshAt, exception);
            return;
        }
        trackers.keySet().retainAll(updated.stream().map(GameServer::endpoint).toList());
        servers = updated;
        nextRefreshAt = now.plusSeconds(3600);
        log.info("官方区服清单已刷新，网关数={}，下次刷新={}", servers.size(), nextRefreshAt);
    }

    private record ProbeObservation(GameServer server, ServerProbeResult result, Instant observedAt) {}

}
