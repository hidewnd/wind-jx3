package com.hidewnd.winds.scout.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hidewnd.winds.scout.model.WeiboPost;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeVisitor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.ArrayDeque;
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
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private static final Pattern RELATIVE_TIME = Pattern.compile("(\\d+)(秒|分钟|小时)前");
    private static final Pattern INTEGER = Pattern.compile("[+-]?\\d+");
    private static final List<String> COVER_FIELDS = List.of(
            "url", "cover_image_url", "cover_url", "thumbnail_pic", "thumb_url", "pic_url");
    private final Clock clock;

    public WeiboResponseParser() {
        this(Clock.systemUTC());
    }

    @Autowired
    public WeiboResponseParser(Clock clock) {
        this.clock = clock;
    }

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
        Instant observedAt = clock.instant();
        ArrayDeque<JsonNode> pending = new ArrayDeque<>();
        root.path("data").path("cards").forEach(pending::addLast);
        while (!pending.isEmpty()) {
            JsonNode card = pending.removeFirst();
            card.path("card_group").forEach(pending::addLast);
            if (!(card instanceof ObjectNode objectNode) || card.path("card_type").asInt() != 9) {
                continue;
            }
            JsonNode mblog = card.path("mblog");
            if (isPinned(mblog) || firstText(mblog, "idstr", "id").isBlank()) {
                continue;
            }
            Instant publishedAt = parseTime(mblog.path("created_at").asText(""), observedAt);
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
        String rawContent = rawContent(mblog);
        List<String> videoCovers = videoCovers(mblog, false);
        List<WeiboPost.Media> media = media(mblog);
        List<String> images = images(media);
        images.addAll(videoCovers);
        return new WeiboPost(
                uid,
                mblog.path("user").path("screen_name").asText(""),
                firstText(mblog, "idstr", "id"),
                formatTime(mblog.path("created_at").asText("")),
                content(mblog),
                rawContent,
                cleanNullableText(mblog.path("source")),
                trimmedText(mblog.path("region_name")),
                nullableLong(mblog.path("reposts_count")),
                nullableLong(mblog.path("comments_count")),
                nullableLong(mblog.path("attitudes_count")),
                postUrl(mblog, card.path("scheme").asText("")),
                distinct(images),
                topics,
                videoCovers,
                retweet(mblog.path("retweeted_status"), 0),
                media, links(mblog), article(mblog), needsExtendedText(mblog));
    }

    private WeiboPost.Retweet retweet(JsonNode origin, int depth) {
        if (!origin.isObject() || origin.isEmpty() || depth >= 5) {
            return null;
        }
        String rawContent = rawContent(origin);
        List<String> covers = videoCovers(origin, true);
        List<WeiboPost.Media> media = media(origin);
        List<String> images = images(media);
        images.addAll(covers);
        return new WeiboPost.Retweet(
                origin.path("user").path("screen_name").asText("[原微博不可访问]"),
                content(origin),
                rawContent,
                cleanNullableText(origin.path("source")),
                trimmedText(origin.path("region_name")),
                nullableLong(origin.path("reposts_count")),
                nullableLong(origin.path("comments_count")),
                nullableLong(origin.path("attitudes_count")),
                distinct(images),
                covers,
                firstText(origin.path("user"), "idstr", "id"),
                firstText(origin, "idstr", "id"),
                formatTime(origin.path("created_at").asText("")), postUrl(origin, ""),
                topics(origin), media, links(origin), article(origin), needsExtendedText(origin),
                origin.path("deleted").asInt() == 1 || !origin.path("user").isObject(),
                retweet(origin.path("retweeted_status"), depth + 1));
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
                value = normalizeTopic(cleanText(value));
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
        }
        Matcher matcher = TOPIC_PATTERN.matcher(content(mblog));
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

    private List<String> images(List<WeiboPost.Media> media) {
        List<String> values = new ArrayList<>();
        for (WeiboPost.Media item : media) {
            if ("image".equals(item.type())) {
                values.add(item.url());
            } else if (item.coverUrl() != null) {
                values.add(item.coverUrl());
            }
        }
        return values;
    }

    /** 按字段语义提取媒体，避免将视频播放地址或网页地址误当作封面。 */
    private List<WeiboPost.Media> media(JsonNode mblog) {
        LinkedHashSet<WeiboPost.Media> values = new LinkedHashSet<>();
        // 混排列表是图片与视频顺序的依据，后续兼容字段只补充未出现的资源。
        for (JsonNode item : mblog.path("mix_media_info").path("items")) {
            JsonNode data = item.path("data");
            if ("pic".equals(item.path("type").asText())) {
                addPicture(data, values);
            } else {
                addVideo(data, values);
                addVideo(item.path("page_info"), values);
            }
        }
        for (JsonNode pic : mblog.path("pics")) {
            addPicture(pic, values);
        }
        JsonNode infos = mblog.path("pic_infos");
        if (mblog.path("pic_ids").isArray()) {
            for (JsonNode id : mblog.path("pic_ids")) {
                addPicture(infos.path(id.asText()), values);
            }
        } else {
            infos.forEach(pic -> addPicture(pic, values));
        }
        addVideo(mblog.path("page_info"), values);
        Element body = htmlBody(rawContent(mblog));
        body.appendChildren(htmlBody(mblog.path("article").path("content").asText("")).childNodesCopy());
        // 表情的 alt 属于正文；url-icon 标记链接装饰（如视频播放图标），均不属于实际配图。
        for (Element img : body.select("img")) {
            if (img.attr("alt").matches("\\[[^\\]]+\\]") || img.hasClass("emoji") || img.hasClass("face")
                    || img.closest(".url-icon") != null) {
                continue;
            }
            String url = safeUrl(img.hasAttr("data-src") ? img.attr("data-src") : img.attr("src"));
            if (url != null) {
                values.add(new WeiboPost.Media("image", url, null));
            }
        }
        for (Element video : body.select("video")) {
            String url = safeUrl(video.attr("src"));
            if (url == null && video.selectFirst("source[src]") != null) {
                url = safeUrl(video.selectFirst("source[src]").attr("src"));
            }
            if (url != null) {
                values.add(new WeiboPost.Media("video", url, safeUrl(video.attr("poster"))));
            }
        }
        return new ArrayList<>(values);
    }

    private void addPicture(JsonNode pic, LinkedHashSet<WeiboPost.Media> values) {
        String url = null;
        for (String size : List.of("largest", "original", "large", "bmiddle")) {
            url = extractUrl(pic.path(size));
            if (url != null) {
                break;
            }
        }
        if (url == null) {
            url = extractUrl(pic);
        }
        String video = safeUrl(firstText(pic, "videoSrc", "video"));
        if (video != null) {
            values.add(new WeiboPost.Media("livephoto", video, url));
        } else if (url != null) {
            values.add(new WeiboPost.Media("image", url, null));
        }
    }

    private void addVideo(JsonNode page, LinkedHashSet<WeiboPost.Media> values) {
        // 优先通用 H.264 清晰流，HEVC 仅作候选；字段缺失时保留页面链接供客户端访问。
        String url = null;
        for (String field : List.of("mp4_720p_mp4", "mp4_hd_mp4", "mp4_hd_url", "stream_url_hd",
                "stream_url", "mp4_sd_url", "mp4_ld_mp4", "hevc_mp4_hd")) {
            url = safeUrl(page.path("urls").path(field).asText(""));
            if (url == null) {
                url = safeUrl(page.path("media_info").path(field).asText(""));
            }
            if (url != null) {
                break;
            }
        }
        if (url == null) {
            for (JsonNode item : page.path("media_info").path("playback_list")) {
                url = safeUrl(item.path("play_info").path("url").asText(""));
                if (url != null) {
                    break;
                }
            }
        }
        if (url != null) {
            String cover = extractUrl(page.path("page_pic"));
            if (cover == null) {
                cover = extractUrl(page.path("media_info").path("cover_image_url"));
            }
            values.add(new WeiboPost.Media("video", url, cover));
        }
    }

    private List<WeiboPost.Link> links(JsonNode mblog) {
        LinkedHashSet<WeiboPost.Link> values = new LinkedHashSet<>();
        Element body = htmlBody(rawContent(mblog));
        body.appendChildren(htmlBody(mblog.path("article").path("content").asText("")).childNodesCopy());
        for (Element link : body.select("a[href]")) {
            String url = safeUrl(link.attr("href"));
            String title = cleanText(link.html());
            if (url != null) {
                String type = title.startsWith("@") ? "mention" : title.startsWith("#") ? "topic" : "link";
                values.add(new WeiboPost.Link(type, title, url, null, null));
            }
        }
        for (JsonNode link : mblog.path("url_struct")) {
            String url = safeUrl(firstText(link, "long_url", "short_url"));
            if (url != null) {
                values.add(new WeiboPost.Link("link", cleanText(firstText(link, "url_title", "short_url")),
                        url, null, extractUrl(link.path("url_type_pic"))));
            }
        }
        JsonNode page = mblog.path("page_info");
        String url = safeUrl(firstText(page, "page_url", "url"));
        if (url != null) {
            values.add(new WeiboPost.Link(firstText(page, "type", "object_type"),
                    cleanText(firstText(page, "page_title", "title")), url,
                    cleanNullableText(page.path("page_desc")), extractUrl(page.path("page_pic"))));
        }
        return new ArrayList<>(values);
    }

    private WeiboPost.Article article(JsonNode mblog) {
        JsonNode page = mblog.path("page_info");
        JsonNode article = mblog.path("article");
        if (!article.isObject() && !"article".equals(page.path("type").asText())) {
            return null;
        }
        String raw = textOrNull(article.path("content"));
        // 微博该字段名称与含义相反：RSSHub formatArticle 注明 1 代表收费。
        return new WeiboPost.Article(
                cleanText(firstText(article, "title").isBlank() ? firstText(page, "page_title") : firstText(article, "title")),
                safeUrl(page.path("page_url").asText("")),
                cleanText(firstText(article, "summary").isBlank() ? firstText(page, "page_desc") : firstText(article, "summary")),
                raw == null ? null : cleanText(raw), raw,
                formatTime(article.path("create_at").asText("")),
                article.hasNonNull("is_article_free") ? article.path("is_article_free").asBoolean() || article.path("is_article_free").asInt() == 1 : null,
                article.hasNonNull("is_trial") ? article.path("is_trial").asBoolean() || article.path("is_trial").asInt() == 1 : null);
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
        for (String field : COVER_FIELDS) {
            if (!"url".equals(field)) {
                addCover(pageInfo.path(field), values);
            }
        }
        JsonNode mediaInfo = pageInfo.path("media_info");
        if (mediaInfo.isObject()) {
            for (String field : COVER_FIELDS) {
                if (!"url".equals(field)) {
                    addCover(mediaInfo.path(field), values);
                }
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
            return safeUrl(value.asText());
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

    static String rawContent(JsonNode mblog) {
        String embedded = mblog.path("longText").path("longTextContent").asText("");
        if (!embedded.isBlank()) {
            return embedded;
        }
        return mblog.path("text").isTextual() ? mblog.path("text").asText() : null;
    }

    static boolean needsExtendedText(JsonNode mblog) {
        if (!mblog.path("longText").path("longTextContent").asText("").isBlank()) {
            return false;
        }
        String text = mblog.path("text").asText("");
        return mblog.path("isLongText").asBoolean() || mblog.path("isLongText").asInt() == 1
                || mblog.path("is_long_text").asBoolean() || mblog.path("is_long_text").asInt() == 1
                || text.contains("展开全文") || text.endsWith("...全文") || text.endsWith("…全文");
    }

    private String content(JsonNode mblog) {
        String raw = rawContent(mblog);
        // text_raw/raw_text 是纯文本：不能再次当作 HTML，避免吃掉用户输入的 <...>。
        return raw == null ? firstText(mblog, "text_raw", "raw_text") : cleanText(raw);
    }

    private Element htmlBody(String html) {
        Element body = Jsoup.parseBodyFragment(html == null ? "" : html, "https://m.weibo.cn").body();
        body.select("script, style, iframe, object, template").remove();
        return body;
    }

    private String cleanText(String html) {
        Element body = htmlBody(html);
        StringBuilder text = new StringBuilder();
        // 使用 DOM 文本节点只解码一次实体，保留空行、NBSP、组合 emoji 和表情 alt。
        body.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode value) {
                    text.append(value.getWholeText());
                } else if (node instanceof Element element) {
                    if ("br".equals(element.normalName())) {
                        text.append('\n');
                    } else if ("img".equals(element.normalName())) {
                        text.append(element.attr("alt"));
                    } else if (element.isBlock() && element != body && !text.isEmpty()
                            && text.charAt(text.length() - 1) != '\n') {
                        text.append('\n');
                    }
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element element && element != body && element.isBlock()) {
                    text.append('\n');
                }
            }
        });
        return text.toString().replace("\r\n", "\n").replace('\r', '\n').strip();
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

    private String formatTime(String value) {
        Instant time = parseTime(value, clock.instant());
        return time.equals(Instant.MIN) ? value : OUTPUT_TIME.format(time.atZone(BEIJING));
    }

    private Instant parseTime(String value, Instant now) {
        try {
            if ("刚刚".equals(value)) {
                return now;
            }
            Matcher relative = RELATIVE_TIME.matcher(value);
            if (relative.matches()) {
                long unit = switch (relative.group(2)) {
                    case "小时" -> 3600;
                    case "分钟" -> 60;
                    default -> 1;
                };
                return now.minusSeconds(Math.multiplyExact(Long.parseLong(relative.group(1)), unit));
            }
            if (value.startsWith("今天 ") || value.startsWith("昨天 ")) {
                LocalDate date = now.atZone(BEIJING).toLocalDate();
                if (value.startsWith("昨天")) {
                    date = date.minusDays(1);
                }
                value = date + value.substring(2);
            }
            if (value.matches("\\d{2}-\\d{2}( \\d{2}:\\d{2})?")) {
                LocalDate today = now.atZone(BEIJING).toLocalDate();
                LocalDate day = LocalDate.parse(today.getYear() + "-" + value.substring(0, 5));
                if (day.isAfter(today)) {
                    day = day.minusYears(1);
                }
                value = day + value.substring(5);
            }
            if (value.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
                return LocalDateTime.parse(value, OUTPUT_TIME)
                        .atZone(BEIJING)
                        .toInstant();
            }
            if (value.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")) {
                return LocalDateTime.parse(value, MINUTE_TIME)
                        .atZone(BEIJING)
                        .toInstant();
            }
            if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(value, DATE)
                        .atStartOfDay(BEIJING)
                        .toInstant();
            }
            if (value.matches("\\d{4}-\\d{2}-\\d{2}T.*")) {
                return OffsetDateTime.parse(value).toInstant();
            }
            return OffsetDateTime.parse(value, WEIBO_TIME).toInstant();
        } catch (java.time.DateTimeException | NumberFormatException | ArithmeticException ignored) {
            return Instant.MIN;
        }
    }

    private String postUrl(JsonNode mblog, String scheme) {
        String id = firstText(mblog, "idstr", "id", "bid");
        String fallback = id.isBlank() ? "" : "https://m.weibo.cn/detail/" + id;
        String value = safeUrl(scheme);
        if (value == null) {
            return fallback;
        }
        try {
            URI uri = URI.create(value);
            if (!"m.weibo.cn".equalsIgnoreCase(uri.getHost()) && !"weibo.com".equalsIgnoreCase(uri.getHost())) {
                return fallback;
            }
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null).toString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String safeUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(value.strip());
            if (value.startsWith("//") || value.startsWith("/")) {
                uri = URI.create("https://m.weibo.cn").resolve(uri);
            }
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                return null;
            }
            if (("weibo.cn".equalsIgnoreCase(uri.getHost()) || "www.weibo.cn".equalsIgnoreCase(uri.getHost()))
                    && "/sinaurl".equals(uri.getPath()) && uri.getRawQuery() != null) {
                for (String part : uri.getRawQuery().split("&")) {
                    if (part.startsWith("u=")) {
                        URI target = URI.create(URLDecoder.decode(part.substring(2), StandardCharsets.UTF_8));
                        if (("https".equalsIgnoreCase(target.getScheme()) || "http".equalsIgnoreCase(target.getScheme()))
                                && target.getHost() != null && target.getUserInfo() == null) {
                            return target.toString();
                        }
                    }
                }
            }
            return uri.toString();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private List<String> distinct(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }
}
