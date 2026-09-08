package com.hidewnd.winds.jx3;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import java.time.*;
import java.util.Map;
import java.util.concurrent.*;

/** 专用四线程调度，不占用微博的默认 Spring 调度器；每个来源独立退避。 */
@Slf4j
public class Jx3Polling {
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
    private final Map<String, Failure> failures = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastSuccess = new ConcurrentHashMap<>();
    private final ArticleMonitor news, maintenance;
    private final PatchMonitor patches;
    private final ServerMonitor servers;
    private final Jx3Configuration.Properties properties;

    public Jx3Polling(ArticleMonitor news, ArticleMonitor maintenance, PatchMonitor patches, ServerMonitor servers, Jx3Configuration.Properties properties) {
        this.news = news; this.maintenance = maintenance; this.patches = patches; this.servers = servers; this.properties = properties;
    }

    @PostConstruct public void start() {
        schedule("news", news::poll, properties.newsInterval());
        schedule("maintenance", maintenance::poll, properties.maintenanceInterval());
        schedule("server", servers::poll, properties.serverInterval());
        schedule("patch", patches::poll, properties.patchInterval());
    }

    private void schedule(String name, Runnable task, Duration interval) {
        executor.scheduleWithFixedDelay(() -> {
            Failure failure = failures.get(name);
            if (failure != null && Instant.now().isBefore(failure.nextAttempt())) return;
            try {
                task.run();
                lastSuccess.put(name, Instant.now());
                if (failures.remove(name) != null) log.info("剑三采集恢复，来源={}", name);
            } catch (RuntimeException exception) {
                int count = failure == null ? 1 : Math.min(6, failure.count() + 1);
                failures.put(name, new Failure(count, Instant.now().plusMillis(Math.min(600_000, interval.toMillis() * (1L << (count - 1))))));
                log.warn("剑三采集失败，来源={}，最近成功={}，原因={}", name, lastSuccess.get(name), exception.getMessage(), exception);
            }
        }, 10_000, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy public void close() { executor.shutdownNow(); }
    private record Failure(int count, Instant nextAttempt) {}
}
