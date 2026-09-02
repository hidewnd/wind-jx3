package com.hidewnd.winds.scout.service.impl;

import cn.hutool.http.HtmlUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hidewnd.winds.scout.model.WeiboPost;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
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
    private static final DateTimeFormatter MINUTE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Pattern TOPIC_PATTERN = Pattern.compile("#([^#]+)#");
    private static final Pattern BR_TAG = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern BLOCK_END_TAG = Pattern.compile(
            "(?i)</(?:p|div|section|article|li|ul|ol|h[1-6])\\s*>");
    private static final Pattern INTEGER = Pattern.compile("[+-]?\\d+");
    private static final List<String> COVER_FIELDS = List.of(
            "url", "cover_image_url", "cover_url", "thumbnail_pic", "thumb_url", "pic_url");

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
        String rawContent = textOrNull(mblog.path("text"));
        List<String> videoCovers = videoCovers(mblog, false);
        List<String> images = images(mblog);
        images.addAll(videoCovers);
        return new WeiboPost(
                uid,
                mblog.path("user").path("screen_name").asText(""),
                mblog.path("id").asText(),
                formatTime(mblog.path("created_at").asText("")),
                removeTopics(cleanText(rawContent), topics),
                rawContent,
                cleanNullableText(mblog.path("source")),
                trimmedText(mblog.path("region_name")),
                nullableLong(mblog.path("reposts_count")),
                nullableLong(mblog.path("comments_count")),
                nullableLong(mblog.path("attitudes_count")),
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
        String rawContent = textOrNull(origin.path("text"));
        List<String> covers = videoCovers(origin, true);
        List<String> images = images(origin);
        images.addAll(covers);
        return new WeiboPost.Retweet(
                origin.path("user").path("screen_name").asText(""),
                cleanText(rawContent),
                rawContent,
                cleanNullableText(origin.path("source")),
                trimmedText(origin.path("region_name")),
                nullableLong(origin.path("reposts_count")),
                nullableLong(origin.path("comments_count")),
                nullableLong(origin.path("attitudes_count")),
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

    private List<String> videoCovers(JsonNode mblog, boolean includeNestedRetweet) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        collectCovers(mblog, values, 0, includeNestedRetweet);
        return new ArrayList<>(values);
    }

    private void collectCovers(
            JsonNode mblog,
            LinkedHashSet<String> values,
            int depth,
            boolean includeNestedRetweet) {
        if (!mblog.isObject() || depth >= 5) {
            return;
        }
        JsonNode pageInfo = mblog.path("page_info");
        addPageInfoCovers(pageInfo, values);
        for (JsonNode item : mblog.path("mix_media_info").path("items")) {
            addPageInfoCovers(item.path("page_info"), values);
            addPageInfoCovers(item.path("data"), values);
            addCover(item.path("page_pic"), values);
        }
        if (includeNestedRetweet) {
            collectCovers(mblog.path("retweeted_status"), values, depth + 1, true);
        }
    }

    private void addPageInfoCovers(JsonNode pageInfo, LinkedHashSet<String> values) {
        if (!pageInfo.isObject()) {
            return;
        }
        addCover(pageInfo.path("page_pic"), values);
        addCover(pageInfo, values);
        JsonNode mediaInfo = pageInfo.path("media_info");
        if (mediaInfo.isObject()) {
            for (String field : COVER_FIELDS) {
                addCover(mediaInfo.path(field), values);
            }
            addCover(mediaInfo.path("page_pic"), values);
        }
    }

    private void addCover(JsonNode value, LinkedHashSet<String> values) {
        String url = extractUrl(value);
        if (url != null) {
            values.add(url);
        }
    }

    private String extractUrl(JsonNode value) {
        if (value.isTextual()) {
            String text = value.asText().trim();
            return text.isEmpty() ? null : text;
        }
        if (!value.isObject()) {
            return null;
        }
        for (String field : COVER_FIELDS) {
            String url = extractUrl(value.path(field));
            if (url != null) {
                return url;
            }
        }
        return extractUrl(value.path("page_pic"));
    }

    private String cleanText(String html) {
        if (html == null) {
            return "";
        }
        String text = html.replace("\r\n", "\n").replace('\r', '\n');
        text = BR_TAG.matcher(text).replaceAll("\n");
        text = BLOCK_END_TAG.matcher(text).replaceAll("\n");
        return HtmlUtil.unescape(HtmlUtil.cleanHtmlTag(text)).strip();
    }

    private String cleanNullableText(JsonNode value) {
        String text = textOrNull(value);
        if (text == null || text.isBlank()) {
            return null;
        }
        String cleaned = cleanText(text);
        return cleaned.isBlank() ? null : cleaned;
    }

    private String trimmedText(JsonNode value) {
        String text = textOrNull(value);
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    private String textOrNull(JsonNode value) {
        return value.isTextual() ? value.asText() : null;
    }

    private Long nullableLong(JsonNode value) {
        if (value.isIntegralNumber()) {
            return value.canConvertToLong() ? value.longValue() : null;
        }
        if (!value.isTextual()) {
            return null;
        }
        String text = value.asText().trim();
        if (!INTEGER.matcher(text).matches()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
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
            if (value.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")) {
                return LocalDateTime.parse(value, MINUTE_TIME).format(OUTPUT_TIME);
            }
            if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(value, DATE).atStartOfDay().format(OUTPUT_TIME);
            }
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
            if (value.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")) {
                return LocalDateTime.parse(value, MINUTE_TIME)
                        .atZone(ZoneId.systemDefault())
                        .toInstant();
            }
            if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(value, DATE)
                        .atStartOfDay(ZoneId.systemDefault())
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
