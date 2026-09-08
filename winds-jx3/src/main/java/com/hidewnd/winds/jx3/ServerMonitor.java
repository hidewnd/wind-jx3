package com.hidewnd.winds.jx3;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import lombok.extern.slf4j.Slf4j;
import java.net.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** 官方清单按合服地址探测；连续两次明确结果才确认，未知结果中断确认序列。 */
@Slf4j
public class ServerMonitor implements AutoCloseable {
    private final OfficialClient client;
    private final Jx3Store store;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final ExecutorService probes = Executors.newFixedThreadPool(4);
    private final Map<String, Confirmation> confirmations = new HashMap<>();
    private final TcpProbe probe;
    private List<Server> servers = List.of();
    private Instant refreshed;

    public ServerMonitor(OfficialClient client, Jx3Store store, ApplicationEventPublisher publisher, ObjectMapper mapper, Clock clock, TcpProbe probe) {
        this.client = client; this.store = store; this.publisher = publisher; this.mapper = mapper; this.clock = clock; this.probe = probe;
    }

    public void poll() {
        Instant now = clock.instant();
        if (refreshed == null || !now.isBefore(refreshed.plusSeconds(3600))) {
            List<Server> updated;
            try { updated = parse(client.serverList()); }
            catch (RuntimeException exception) {
                confirmations.values().forEach(c -> c.observe(Result.UNKNOWN, now));
                throw exception;
            }
            confirmations.keySet().retainAll(updated.stream().map(Server::endpoint).toList());
            servers = updated;
            refreshed = now;
        }
        List<Callable<Result>> tasks = servers.stream().<Callable<Result>>map(server -> () -> probe.check(server)).toList();
        List<Result> results = new ArrayList<>();
        try {
            for (Future<Result> future : probes.invokeAll(tasks)) results.add(future.get());
        } catch (InterruptedException exception) {
            confirmations.values().forEach(c -> c.observe(Result.UNKNOWN, now));
            Thread.currentThread().interrupt();
            throw new IllegalStateException("区服探测被中断", exception);
        } catch (ExecutionException exception) {
            confirmations.values().forEach(c -> c.observe(Result.UNKNOWN, now));
            throw new IllegalStateException("区服探测异常", exception.getCause());
        }
        // 集中超时不转成关服；明确的拒绝连接仍可连续确认，否则全服维护后无法报开服。
        long unknownCount = results.stream().filter(r -> r == Result.UNKNOWN).count();
        if (unknownCount > 0) log.warn("区服探测存在未知结果，数量={}，总数={}；不据此判断维护", unknownCount, results.size());
        for (int i = 0; i < servers.size(); i++) {
            Server server = servers.get(i);
            Result result = results.get(i);
            Confirmation confirmation = confirmations.computeIfAbsent(server.endpoint(), ignored -> new Confirmation());
            Transition transition = confirmation.observe(result, now);
            if (result == Result.UNKNOWN || confirmation.confirmed == null) continue;
            String status = transition == null ? confirmation.confirmed : transition.status();
            var state = mapper.createObjectNode().put("status", status);
            state.set("server", mapper.valueToTree(server));
            Jx3Event event = transition == null ? null : Jx3Event.server(UUID.randomUUID().toString(), transition.observed(),
                    clock.instant(), server, confirmation.confirmed, status);
            boolean inserted = store.save("server:" + server.zoneId() + ":" + server.serverName(), state, event);
            if (transition != null) {
                confirmation.confirmed = status;
                if (inserted) publisher.publishEvent(event);
            }
        }
    }

    public static List<Server> parse(String text) {
        Map<String, List<String[]>> groups = new LinkedHashMap<>();
        for (String line : text.split("\\R")) {
            if (line.isBlank()) continue;
            String[] fields = line.split("\\t", -1);
            if (fields.length < 12 || !fields[3].matches("\\d{1,3}(?:\\.\\d{1,3}){3}") || !fields[4].matches("\\d+"))
                throw new IllegalArgumentException("区服清单格式变化");
            int port = Integer.parseInt(fields[4]);
            if (port < 1 || port > 65535 || fields[9].isBlank() || fields[10].isBlank()) throw new IllegalArgumentException("区服地址或标识无效");
            groups.computeIfAbsent(fields[3] + ":" + fields[4], ignored -> new ArrayList<>()).add(fields);
        }
        if (groups.isEmpty()) throw new IllegalArgumentException("区服清单为空");
        List<Server> result = new ArrayList<>();
        for (var rows : groups.values()) {
            String[] row = rows.getFirst();
            List<String> aliases = rows.stream().map(r -> r[1]).filter(name -> !name.equals(row[10])).distinct().sorted().toList();
            result.add(new Server(row[9], row[11], row[10], aliases, row[3], Integer.parseInt(row[4])));
        }
        return List.copyOf(result);
    }

    @Override public void close() { probes.shutdownNow(); }
    public record Server(String zoneId, String zoneName, String serverName, List<String> aliases, String host, int port) {
        public String endpoint() { return host + ":" + port; }
    }
    public enum Result { REACHABLE, REFUSED, UNKNOWN }
    public record Transition(String status, Instant observed) {}

    /** 将网络错误与明确的拒绝连接区分，不把超时当作关服。 */
    public static class TcpProbe {
        public Result check(Server server) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(server.host(), server.port()), 3000);
                return Result.REACHABLE;
            } catch (ConnectException exception) {
                String message = Objects.toString(exception.getMessage(), "").toLowerCase(Locale.ROOT);
                return message.contains("refused") || message.contains("拒绝") ? Result.REFUSED : Result.UNKNOWN;
            } catch (IOException exception) { return Result.UNKNOWN; }
        }
    }

    public static class Confirmation {
        private String confirmed;
        private Result candidate;
        private int count;
        private Instant first;

        public Transition observe(Result result, Instant now) {
            if (result == Result.UNKNOWN) { candidate = null; count = 0; return null; }
            if (candidate != result) { candidate = result; count = 1; first = now; return null; }
            count = Math.min(2, count + 1);
            String status = result == Result.REACHABLE ? "reachable" : "unreachable";
            if (confirmed == null) { confirmed = status; return null; }
            return status.equals(confirmed) ? null : new Transition(status, first);
        }
    }
}
