package com.hidewnd.winds.jx3.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hidewnd.winds.jx3.model.Article;
import com.hidewnd.winds.jx3.model.PatchInfo;
import com.hidewnd.winds.jx3.model.PatchManifest;
import com.hidewnd.winds.jx3.parser.ArticleParser;
import com.hidewnd.winds.jx3.parser.PatchManifestParser;
import com.hidewnd.winds.jx3.support.Jx3Time;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 只访问官方固定来源；清单条件请求缓存由此客户端持有，失败响应不会替换有效缓存。 */
public class OfficialClient implements AutoCloseable {
    private static final String API = "https://jx3.xoyo.com/api.php?op=search_api&";
    private static final URI PATCH_BASE =
            URI.create("https://jx3hdv4qq-autoupdate.xoyocdn.com/jx3hd_v4/zhcn_hd/");
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final Map<URI, CachedResponse> cache = new ConcurrentHashMap<>();

    public OfficialClient(ObjectMapper mapper) {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                mapper);
    }

    OfficialClient(HttpClient http, ObjectMapper mapper) {
        this.http = http;
        this.mapper = mapper;
    }

    public PatchManifest fetchPatchManifest() {
        return PatchManifestParser.parse(
                new String(
                        get(PATCH_BASE.resolve("autoupdateentry.txt"), true),
                        StandardCharsets.UTF_8));
    }

    public String fetchServerList() {
        byte[] content =
                get(
                        URI.create(
                                "https://jx3comm.xoyocdn.com/jx3hd/zhcn_hd/serverlist/serverlist.ini"),
                        true);
        try {
            // 官网清单为 GBK；必须按源编码解码，不能依赖 Ubuntu/Docker 默认 UTF-8。
            return Charset.forName("GBK").newDecoder().decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalStateException("官方区服清单不是有效 GBK 数据", exception);
        }
    }

    public PatchInfo fetchPatchInfo(String fileName) {
        if (!fileName.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("非法补丁文件名");
        }
        HttpResponse<byte[]> response =
                request(
                        HttpRequest.newBuilder(PATCH_BASE.resolve(fileName))
                                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                                .timeout(Duration.ofSeconds(10))
                                .build());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("补丁尚不可用，HTTP " + response.statusCode());
        }
        long length =
                response.headers()
                        .firstValueAsLong("Content-Length")
                        .orElseThrow(() -> new IllegalStateException("补丁缺少大小"));
        if (length <= 0) {
            throw new IllegalStateException("补丁大小无效");
        }
        String modified =
                response.headers()
                        .firstValue("Last-Modified")
                        .orElseThrow(() -> new IllegalStateException("补丁缺少官方文件更新时间"));
        try {
            Instant updated =
                    ZonedDateTime.parse(
                                    modified, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                            .toInstant();
            return new PatchInfo(length, updated);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalStateException("补丁文件更新时间无效", exception);
        }
    }

    /** 分页回看至上轮扫描前或七日边界；按发布时间排序，置顶条目不作为停止依据。 */
    public List<Article> fetchArticles(boolean maintenance, Instant since) {
        List<Article> articles = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int page = 1; page <= 100; page++) {
            String query =
                    maintenance
                            ? "action=get_customer_article_list&game=jx3&order=auto"
                            : "action=get_article_list&catid=2458&order_by=inputtime&sort_by=desc";
            JsonNode data = json(URI.create(API + query + "&num=30&page=" + page));
            JsonNode list = data.path("list");
            if (!list.isArray() && !list.isObject()) {
                throw new IllegalStateException("文章列表格式变化");
            }
            if (list.isEmpty()) {
                return articles;
            }
            boolean reachedBoundary = false;
            int newItems = 0;
            for (JsonNode item : list) {
                String id = item.path("id").asText();
                if (!id.matches("\\d+")) {
                    throw new IllegalStateException("文章 ID 无效");
                }
                if (!seen.add(id)) {
                    continue;
                }
                newItems++;
                String published = ArticleParser.parseTimestamp(item.get("inputtime"));
                if (published == null) {
                    published = ArticleParser.parseTimestamp(item.get("asktime"));
                }
                boolean old =
                        since != null
                                && published != null
                                && Jx3Time.parse(published).isBefore(since);
                if (old && !item.path("top").asText("0").equals("1")) {
                    reachedBoundary = true;
                }
                String title = ArticleParser.toPlainText(item.path("title").asText());
                boolean isMaintenance =
                        title.matches(".*(?:维护|开服|停服).*")
                                && !title.matches(".*(?:测试服|体验服|缘起|国际服|热线|客服系统).*");
                if (maintenance && (!isMaintenance || old)) {
                    continue;
                }
                if (!maintenance && isMaintenance) {
                    continue;
                }
                String detailQuery =
                        maintenance
                                ? "action=get_customer_article_detail&game=jx3&kid=" + id
                                : "action=get_article_detail&catid=2458&id=" + id;
                JsonNode detail = json(URI.create(API + detailQuery));
                if (!maintenance) {
                    detail = detail.isArray() && !detail.isEmpty() ? detail.get(0) : null;
                }
                if (detail == null || !detail.isObject() || !detail.hasNonNull("content")) {
                    throw new IllegalStateException("文章详情缺失");
                }
                ObjectNode merged = ((ObjectNode) item).deepCopy();
                merged.setAll((ObjectNode) detail);
                // 详情中的空时间不能覆盖列表已经提供的有效官方发布时间。
                for (String field : List.of("inputtime", "asktime")) {
                    if (ArticleParser.parseTimestamp(merged.get(field)) == null
                            && ArticleParser.parseTimestamp(item.get(field)) != null) {
                        merged.set(field, item.get(field));
                    }
                }
                articles.add(
                        ArticleParser.parse(merged, detail.get("content").asText(), maintenance));
            }
            if (since == null || reachedBoundary || list.size() < 30) {
                return articles;
            }
            if (newItems == 0) {
                throw new IllegalStateException("官网分页重复，停止本轮采集");
            }
        }
        throw new IllegalStateException("官网分页超过上限，本轮不更新基线");
    }

    private JsonNode json(URI uri) {
        try {
            JsonNode root = mapper.readTree(get(uri, false));
            if (root.path("code").asInt() != 1 || !root.hasNonNull("data")) {
                throw new IllegalStateException("官网接口返回失败");
            }
            return root.get("data");
        } catch (IOException exception) {
            throw new IllegalStateException("官网 JSON 解析失败", exception);
        }
    }

    byte[] get(URI uri, boolean conditional) {
        CachedResponse previous = conditional ? cache.get(uri) : null;
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET();
        if (previous != null) {
            if (previous.etag() != null) {
                builder.header("If-None-Match", previous.etag());
            }
            if (previous.modified() != null) {
                builder.header("If-Modified-Since", previous.modified());
            }
        }
        HttpResponse<byte[]> response = request(builder.build());
        if (response.statusCode() == 304 && previous != null) {
            return previous.body();
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("官网请求失败，HTTP " + response.statusCode());
        }
        if (conditional) {
            cache.put(
                    uri,
                    new CachedResponse(
                            response.body(),
                            response.headers().firstValue("ETag").orElse(null),
                            response.headers().firstValue("Last-Modified").orElse(null)));
        }
        return response.body();
    }

    private HttpResponse<byte[]> request(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("官网采集已中断", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("官网连接失败", exception);
        }
    }

    @Override
    public void close() {
        http.close();
    }

    private record CachedResponse(byte[] body, String etag, String modified) {}
}
