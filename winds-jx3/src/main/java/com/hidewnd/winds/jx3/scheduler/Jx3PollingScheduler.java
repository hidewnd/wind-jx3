package com.hidewnd.winds.jx3.scheduler;

import com.hidewnd.winds.jx3.config.Jx3Properties;
import com.hidewnd.winds.jx3.service.ArticleMonitorService;
import com.hidewnd.winds.jx3.service.PatchMonitorService;
import com.hidewnd.winds.jx3.service.ServerMonitorService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** 专用四线程调度，不占用微博的默认 Spring 调度器；每个来源独立退避。 */
@Slf4j
public class Jx3PollingScheduler {
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
    private final Map<String, Failure> failures = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastSuccess = new ConcurrentHashMap<>();
    private final ArticleMonitorService news;
    private final ArticleMonitorService maintenance;
    private final PatchMonitorService patches;
    private final ServerMonitorService servers;
    private final Jx3Properties properties;

    public Jx3PollingScheduler(
            ArticleMonitorService news,
            ArticleMonitorService maintenance,
            PatchMonitorService patches,
            ServerMonitorService servers,
            Jx3Properties properties) {
        this.news = news;
        this.maintenance = maintenance;
        this.patches = patches;
        this.servers = servers;
        this.properties = properties;
    }

    @PostConstruct
    public void start() {
        schedule("news", news::poll, properties.newsInterval());
        schedule("maintenance", maintenance::poll, properties.maintenanceInterval());
        schedule("server", servers::poll, properties.serverInterval());
        schedule("patch", patches::poll, properties.patchInterval());
    }

    private void schedule(String name, Runnable task, Duration interval) {
        executor.scheduleWithFixedDelay(
                () -> {
                    Failure failure = failures.get(name);
                    if (failure != null && Instant.now().isBefore(failure.nextAttempt())) {
                        return;
                    }
                    try {
                        task.run();
                        lastSuccess.put(name, Instant.now());
                        if (failures.remove(name) != null) {
                            log.info("剑三采集恢复，来源={}", name);
                        }
                    } catch (RuntimeException exception) {
                        int count = failure == null ? 1 : Math.min(6, failure.count() + 1);
                        long delayMillis =
                                Math.min(600_000, interval.toMillis() * (1L << (count - 1)));
                        failures.put(
                                name, new Failure(count, Instant.now().plusMillis(delayMillis)));
                        log.warn(
                                "剑三采集失败，来源={}，最近成功={}，原因={}",
                                name,
                                lastSuccess.get(name),
                                exception.getMessage(),
                                exception);
                    }
                },
                10_000,
                interval.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }

    private record Failure(int count, Instant nextAttempt) {}
}
