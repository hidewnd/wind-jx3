package com.hidewnd.winds.jx3;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** 新增剑三消息的固定协议，沿用项目 yyyy-MM-dd HH:mm:ss 北京时间约定。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record Jx3Event(String type, String eventId, String occurredAt, String message, Object data) {
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(BEIJING);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(BEIJING);

    public static String time(Instant time) { return time == null ? null : TIME.format(time); }
    public static Instant parseTime(String value) { return LocalDateTime.parse(value, TIME).atZone(BEIJING).toInstant(); }

    public static Jx3Event patch(String id, Instant observed, String previous, String version, int count, long bytes) {
        String message = String.format(Locale.ROOT, "[%s]西山居又偷偷更新了！\n版本 %s->%s\n共%d个更新包，总计%.2f MB",
                CLOCK.format(observed), previous, version, count, bytes / 1048576.0);
        return new Jx3Event("jx3.patch.updated", id, time(observed), message, new Patch(previous, version, count, bytes));
    }

    public static Jx3Event article(String id, Instant observed, ArticleParser.Article article, boolean created) {
        String change = created ? "created" : "updated";
        String label = article.maintenance() ? "剑网3维护公告" : "剑网3新闻";
        String message = "[" + CLOCK.format(observed) + "]" + label + (created ? "发布" : "更新") + "\n" + article.title()
                + (article.summary() == null ? "" : "\n" + article.summary()) + "\n" + article.url();
        Object data = article.maintenance()
                ? new Maintenance(article.articleId(), change, article.title(), article.summary(), article.url(), article.publishedAt(),
                    article.updatedAt(), article.maintenanceStatus(), article.startsAt(), article.expectedEndsAt())
                : new News(article.articleId(), article.categoryId(), change, article.title(), article.summary(), article.url(),
                    article.publishedAt(), article.updatedAt());
        return new Jx3Event(article.maintenance() ? "jx3.maintenance.updated" : "jx3.news.updated", id, time(observed), message, data);
    }

    public static Jx3Event server(String id, Instant observed, Instant confirmed, ServerMonitor.Server server,
                                   String previous, String status) {
        String message = "[" + CLOCK.format(observed) + "]" + server.serverName()
                + (status.equals("reachable") ? "开服啦！" : "暂时无法连接，可能维护中。");
        return new Jx3Event("jx3.server.changed", id, time(observed), message,
                new Server(server.zoneId(), server.zoneName(), server.serverName(), server.aliases(), previous, status, "tcp", time(confirmed)));
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record News(String articleId, String categoryId, String changeType, String title, String summary, String url,
                       String publishedAt, String updatedAt) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Maintenance(String articleId, String changeType, String title, String summary, String url,
                              String publishedAt, String updatedAt, String maintenanceStatus, String startsAt, String expectedEndsAt) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Server(String zoneId, String zoneName, String serverName, List<String> aliases, String previousStatus,
                         String status, String detectedBy, String confirmedAt) {}
    public record Patch(String previousVersion, String version, int packageCount, long totalBytes) {}
}
