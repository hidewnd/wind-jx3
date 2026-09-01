package com.hidewnd.winds.scout.service.impl;

import cn.hutool.http.HtmlUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hidewnd.winds.scout.model.WeiboPost;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 微博移动端响应解析器，负责筛选最新非置顶微博并提取正文、媒体和话题。
 */
@Component
public class WeiboResponseParser {

    private static final DateTimeFormatter WEIBO_TIME =
            DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter OUTPUT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern TOPIC_PATTERN = Pattern.compile("#([^#]+)#");

    /**
     * 从微博列表响应中解析最新一条有效微博。
     *
     * @param root 微博列表响应
     * @param uid  微博 UID
     * @return 最新微博；响应无效或没有有效微博时返回空
     */
    public Optional<WeiboPost> parseLatest(JsonNode root, String uid) {
        if (root.path("ok").asInt() != 1) {
            return Optional.empty();
        }
        return selectLatestCard(root).map(card -> parseCard(card, uid));
    }

    Optional<ObjectNode> selectLatestCard(JsonNode root) {
        ObjectNode latest = null;
        Instant latestTime = Instant.MIN;
        for (JsonNode card : root.path("data").path("cards")) {
            if (!(card instanceof ObjectNode objectNode) || card.path("card_type").asInt() != 9) {
                continue;
            }
            JsonNode mblog = card.path("mblog");
            if (isPinned(mblog) || mblog.path("id").asText().isBlank()) {
                continue;
            }
            Instant publishedAt = parseTime(mblog.path("created_at").asText(""));
            if (latest == null || publishedAt.isAfter(latestTime)) {
                latest = objectNode;
                latestTime = publishedAt;
            }
        }
        return Optional.ofNullable(latest);
    }

    WeiboPost parseCard(JsonNode card, String uid) {
        JsonNode mblog = card.path("mblog");
        List<String> topics = topics(mblog);
        List<String> videoCovers = videoCovers(mblog);
        List<String> images = images(mblog);
        images.addAll(videoCovers);
        return new WeiboPost(
                uid,
                mblog.path("user").path("screen_name").asText(""),
                mblog.path("id").asText(),
                formatTime(mblog.path("created_at").asText("")),
                removeTopics(cleanText(mblog.path("text").asText("")), topics),
                stripQuery(card.path("scheme").asText("")),
                distinct(images),
                topics,
                videoCovers,
                retweet(mblog.path("retweeted_status")));
    }

    private WeiboPost.Retweet retweet(JsonNode origin) {
        if (!origin.isObject() || origin.isEmpty()) {
            return null;
        }
        List<String> covers = videoCovers(origin);
        List<String> images = images(origin);
        images.addAll(covers);
        return new WeiboPost.Retweet(
                origin.path("user").path("screen_name").asText(""),
                cleanText(origin.path("text").asText("")),
                distinct(images),
                covers);
    }

    private boolean isPinned(JsonNode mblog) {
        return mblog.path("isTop").asBoolean(false)
                || mblog.path("isTop").asInt(0) == 1
                || mblog.path("title").path("text").asText("").contains("置顶");
    }

    private List<String> topics(JsonNode mblog) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String key : List.of("topics", "topic_struct")) {
            for (JsonNode topic : mblog.path(key)) {
                String value = firstText(topic,
                        "topic_title", "topic_name", "topic", "name", "text", "title");
                if (!value.isEmpty()) {
                    values.add(normalizeTopic(value));
                }
            }
        }
        Matcher matcher = TOPIC_PATTERN.matcher(cleanText(
                mblog.path("raw_text").asText("") + " " + mblog.path("text").asText("")));
        while (matcher.find()) {
            values.add(normalizeTopic(matcher.group(1)));
        }
        return new ArrayList<>(values);
    }

    private String firstText(JsonNode node, String... fields) {
        if (node.isTextual()) {
            return node.asText("").trim();
        }
        for (String field : fields) {
            String value = node.path(field).asText("").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private String normalizeTopic(String value) {
        return value.trim().replaceAll("^#+|#+$", "");
    }

    private List<String> images(JsonNode mblog) {
        List<String> values = new ArrayList<>();
        for (JsonNode pic : mblog.path("pics")) {
            String url = pic.path("large").path("url").asText("");
            if (url.isBlank()) {
                url = pic.path("url").asText("");
            }
            if (!url.isBlank()) {
                values.add(url);
            }
        }
        return values;
    }

    private List<String> videoCovers(JsonNode mblog) {
        String url = mblog.path("page_info").path("page_pic").path("url").asText("");
        return url.isBlank() ? List.of() : List.of(url);
    }

    private String cleanText(String html) {
        return HtmlUtil.unescape(HtmlUtil.cleanHtmlTag(html)).trim();
    }

    private String removeTopics(String text, List<String> topics) {
        String result = text;
        for (String topic : topics) {
            result = result.replace("#" + topic + "#", "");
        }
        return result.trim();
    }

    private String formatTime(String value) {
        if (value.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
            return value;
        }
        try {
            return OffsetDateTime.parse(value, WEIBO_TIME).format(OUTPUT_TIME);
        } catch (DateTimeParseException ignored) {
            return value;
        }
    }

    private Instant parseTime(String value) {
        try {
            if (value.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
                return LocalDateTime.parse(value, OUTPUT_TIME)
                        .atZone(ZoneId.systemDefault())
                        .toInstant();
            }
            return OffsetDateTime.parse(value, WEIBO_TIME).toInstant();
        } catch (DateTimeParseException ignored) {
            return Instant.MIN;
        }
    }

    private String stripQuery(String value) {
        if (value.isBlank()) {
            return value;
        }
        try {
            URI uri = URI.create(value);
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null).toString();
        } catch (Exception ignored) {
            int index = value.indexOf('?');
            return index < 0 ? value : value.substring(0, index);
        }
    }

    private List<String> distinct(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }
}
