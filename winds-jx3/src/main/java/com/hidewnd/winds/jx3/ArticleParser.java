package com.hidewnd.winds.jx3;

import com.fasterxml.jackson.databind.JsonNode;
import org.jsoup.Jsoup;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/** 官网正文只在输入边界转成纯文本，完整 HTML 另行留档，不发送给客户端。 */
public final class ArticleParser {
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private static final Pattern START = Pattern.compile("(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})日(?:[（(][^）)]*[）)])?\\s*(\\d{1,2})[:：](\\d{2})");
    private static final Pattern END = Pattern.compile("预计(?:维护)?(?:结束时间(?:为)?|于)?\\s*(\\d{1,2})[:：](\\d{2})(?:结束)?");

    public static String plain(String html) {
        if (html == null) return null;
        var doc = Jsoup.parse(html);
        doc.select("script,style").remove();
        return doc.text().trim();
    }

    public static String summary(String text) {
        String value = plain(text);
        if (value == null || value.isBlank()) return null;
        return value.codePointCount(0, value.length()) > 300 ? value.substring(0, value.offsetByCodePoints(0, 300)) + "…" : value;
    }

    public static Article parse(JsonNode node, String content, boolean maintenance) {
        String id = node.path("id").asText();
        String title = plain(node.path("title").asText());
        if (id.isBlank() || title.isBlank()) throw new IllegalArgumentException("公告缺少标识或标题");
        String category = node.path("catid").asText();
        String published = timestamp(node.get("inputtime"));
        if (published == null) published = timestamp(node.get("asktime"));
        String updated = timestamp(node.get("updatetime"));
        String body = plain(content);
        String description = summary(node.path("description").asText());
        if (description == null) description = summary(body);
        String url = category.equals("0")
                ? "https://kefu.xoyo.com/?game_name=jx3&id=" + id + "&r=gonggao" : node.path("url").asText();
        if (url.isBlank()) url = "https://jx3.xoyo.com/show-" + category + "-" + id + "-1.html";
        String status = "unknown", starts = null, ends = null;
        if (maintenance) {
            String text = title + " " + body;
            status = text.contains("取消") ? "cancelled" : text.contains("已完成") ? "completed"
                    : text.matches("(?s).*(?:维护延期|延长维护|维护时间延长|维护延迟).*" ) ? "delayed"
                    : text.matches("(?s).*(?:将于|将进行|进行例行维护|进行临时维护).*" ) ? "scheduled" : "unknown";
            var match = START.matcher(text);
            if (match.find() && (match.group(1) != null || published != null)) {
                try {
                    int year = match.group(1) != null ? Integer.parseInt(match.group(1)) : Jx3Event.parseTime(published).atZone(BEIJING).getYear();
                    LocalDate date = LocalDate.of(year, Integer.parseInt(match.group(2)), Integer.parseInt(match.group(3)));
                    if (match.group(1) == null) {
                        LocalDate pub = Jx3Event.parseTime(published).atZone(BEIJING).toLocalDate();
                        if (date.isBefore(pub.minusMonths(6))) date = date.plusYears(1);
                        if (date.isAfter(pub.plusMonths(6))) date = date.minusYears(1);
                    }
                    var start = date.atTime(Integer.parseInt(match.group(4)), Integer.parseInt(match.group(5))).atZone(BEIJING);
                    starts = Jx3Event.time(start.toInstant());
                    var end = END.matcher(text);
                    if (end.find()) {
                        var finish = date.atTime(Integer.parseInt(end.group(1)), Integer.parseInt(end.group(2))).atZone(BEIJING);
                        // 未明确跨日时不能猜测次日结束。
                        if (!finish.isBefore(start)) ends = Jx3Event.time(finish.toInstant());
                    }
                } catch (DateTimeException | NumberFormatException ignored) {
                    starts = null;
                    ends = null;
                }
            }
        }
        return new Article(id, category, title, description, url, published, updated, content, maintenance, status, starts, ends);
    }

    public static String timestamp(JsonNode value) {
        if (value == null || value.isNull()) return null;
        try {
            String text = value.asText();
            if (text.matches("\\d+")) {
                long seconds = Long.parseLong(text);
                return seconds > 0 ? Jx3Event.time(Instant.ofEpochSecond(seconds)) : null;
            }
            return Jx3Event.time(LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(BEIJING).toInstant());
        } catch (DateTimeException | NumberFormatException ignored) { return null; }
    }

    public record Article(String articleId, String categoryId, String title, String summary, String url, String publishedAt,
                          String updatedAt, String rawContent, boolean maintenance, String maintenanceStatus, String startsAt, String expectedEndsAt) {}
}
