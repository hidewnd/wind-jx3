package com.hidewnd.winds.jx3.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.hidewnd.winds.jx3.model.Article;
import com.hidewnd.winds.jx3.support.Jx3Time;

import org.jsoup.Jsoup;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/** 官网正文在输入边界转成完整纯文本，发布时间必须来自官方数据。 */
public final class ArticleParser {
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private static final String ARTICLE_PAGE =
            "https://jx3.xoyo.com/index/index.html#/article-details?";
    private static final Pattern LEGACY_ARTICLE =
            Pattern.compile("https://jx3\\.xoyo\\.com/show-(\\d+)-(\\d+)-\\d+\\.html");
    private static final Pattern START =
            Pattern.compile(
                    "(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})日(?:[（(][^）)]*[）)])?\\s*(\\d{1,2})[:：](\\d{2})");
    private static final Pattern END =
            Pattern.compile("预计(?:维护)?(?:结束时间(?:为)?|于)?\\s*(\\d{1,2})[:：](\\d{2})(?:结束)?");

    private ArticleParser() {}

    public static String toPlainText(String html) {
        if (html == null) {
            return null;
        }
        var doc = Jsoup.parse(html);
        doc.select("script,style").remove();
        return doc.text().trim();
    }

    public static String summarize(String text) {
        String value = toPlainText(text);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.codePointCount(0, value.length()) > 300
                ? value.substring(0, value.offsetByCodePoints(0, 300)) + "…"
                : value;
    }

    public static Article parse(JsonNode node, String content, boolean maintenance) {
        String id = node.path("id").asText();
        String title = toPlainText(node.path("title").asText());
        if (id.isBlank() || title.isBlank()) {
            throw new IllegalArgumentException("公告缺少标识或标题");
        }
        String category = node.path("catid").asText();
        String published = parseTimestamp(node.get("inputtime"));
        if (published == null) {
            published = parseTimestamp(node.get("asktime"));
        }
        if (published == null) {
            throw new IllegalArgumentException("文章 " + id + " 缺少有效的官方发布时间");
        }
        String updated = parseTimestamp(node.get("updatetime"));
        String body = toPlainText(content);
        String description = summarize(node.path("description").asText());
        if (description == null) {
            description = summarize(body);
        }
        String url;
        // 与官网 latest 的点击路由一致：客服公告用 kid，新闻用栏目和文章 ID，链接活动保留目标。
        if (category.equals("0")) {
            url = ARTICLE_PAGE + "kid=" + id;
        } else if (node.path("islink").asText().equals("1")) {
            url = node.path("url").asText();
            var target = LEGACY_ARTICLE.matcher(url);
            if (target.matches()) {
                url = ARTICLE_PAGE + "catid=" + target.group(1) + "&id=" + target.group(2);
            }
        } else {
            url = ARTICLE_PAGE + "catid=" + category + "&id=" + id;
        }
        String status = "unknown";
        String starts = null;
        String ends = null;
        // 公告通道还包含版本更新、处罚等文章；只为正式服维护通知提取维护状态和时间。
        if (maintenance
                && title.matches(".*(?:维护|开服|停服).*")
                && !title.matches(".*(?:测试服|体验服|缘起|国际服|热线|客服系统).*")) {
            String text = title + " " + body;
            if (text.contains("取消")) {
                status = "cancelled";
            } else if (text.contains("已完成")) {
                status = "completed";
            } else if (text.matches("(?s).*(?:维护延期|延长维护|维护时间延长|维护延迟).*")) {
                status = "delayed";
            } else if (text.matches("(?s).*(?:将于|将进行|进行例行维护|进行临时维护).*")) {
                status = "scheduled";
            }
            var match = START.matcher(text);
            if (match.find() && (match.group(1) != null || published != null)) {
                try {
                    int year =
                            match.group(1) != null
                                    ? Integer.parseInt(match.group(1))
                                    : Jx3Time.parse(published).atZone(BEIJING).getYear();
                    LocalDate date =
                            LocalDate.of(
                                    year,
                                    Integer.parseInt(match.group(2)),
                                    Integer.parseInt(match.group(3)));
                    if (match.group(1) == null) {
                        LocalDate pub = Jx3Time.parse(published).atZone(BEIJING).toLocalDate();
                        if (date.isBefore(pub.minusMonths(6))) {
                            date = date.plusYears(1);
                        }
                        if (date.isAfter(pub.plusMonths(6))) {
                            date = date.minusYears(1);
                        }
                    }
                    var start =
                            date.atTime(
                                            Integer.parseInt(match.group(4)),
                                            Integer.parseInt(match.group(5)))
                                    .atZone(BEIJING);
                    starts = Jx3Time.format(start.toInstant());
                    var end = END.matcher(text);
                    if (end.find()) {
                        var finish =
                                date.atTime(
                                                Integer.parseInt(end.group(1)),
                                                Integer.parseInt(end.group(2)))
                                        .atZone(BEIJING);
                        // 未明确跨日时不能猜测次日结束。
                        if (!finish.isBefore(start)) {
                            ends = Jx3Time.format(finish.toInstant());
                        }
                    }
                } catch (DateTimeException | NumberFormatException ignored) {
                    starts = null;
                    ends = null;
                }
            }
        }
        return new Article(
                id,
                category,
                title,
                description,
                url,
                published,
                updated,
                body,
                maintenance,
                status,
                starts,
                ends);
    }

    public static String parseTimestamp(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            String text = value.asText();
            if (text.matches("\\d+")) {
                long seconds = Long.parseLong(text);
                return seconds > 0 ? Jx3Time.format(Instant.ofEpochSecond(seconds)) : null;
            }
            return Jx3Time.format(
                    LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                            .atZone(BEIJING)
                            .toInstant());
        } catch (DateTimeException | NumberFormatException ignored) {
            return null;
        }
    }
}
