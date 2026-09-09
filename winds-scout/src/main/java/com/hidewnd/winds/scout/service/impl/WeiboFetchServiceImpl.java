package com.hidewnd.winds.scout.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hidewnd.winds.scout.exception.WeiboCookieUpdateException;
import com.hidewnd.winds.scout.exception.WeiboAccountInvalidException;
import com.hidewnd.winds.scout.exception.ScoutApiException;
import com.hidewnd.winds.scout.model.WeiboAccount;
import com.hidewnd.winds.scout.model.WeiboPost;
import com.hidewnd.winds.scout.repository.WeiboAccountCookieRepository;
import com.hidewnd.winds.scout.service.WeiboFetchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 微博移动端接口抓取服务实现，负责请求最新微博并补全长文本。
 */
@Service
@Slf4j
public class WeiboFetchServiceImpl implements WeiboFetchService {

    private static final String API_URL = "https://m.weibo.cn/api/container/getIndex";
    private static final String USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1";

    private final RestClient restClient;
    private final WeiboResponseParser parser;
    private final WeiboCookieStore cookieStore;
    private final WeiboAccountCookieRepository cookieRepository;

    public WeiboFetchServiceImpl(
            @Qualifier("weiboRestClient") RestClient restClient,
            WeiboResponseParser parser,
            WeiboCookieStore cookieStore,
            WeiboAccountCookieRepository cookieRepository) {
        this.restClient = restClient;
        this.parser = parser;
        this.cookieStore = cookieStore;
        this.cookieRepository = cookieRepository;
    }

    @Override
    public Optional<String> findUidByScreenName(String screenName, WeiboAccount account) {
        if (screenName == null || screenName.isBlank()) {
            throw new IllegalArgumentException("微博博主全称不能为空");
        }
        URI uri = UriComponentsBuilder.fromHttpUrl(API_URL)
                .queryParam("containerid", "{container}").queryParam("page_type", "searchall")
                .encode().buildAndExpand("100103type=3&q=" + screenName + "&t=0").toUri();
        JsonNode response = getJson(uri, account, headers -> {
            headers.set(HttpHeaders.ACCEPT, "application/json");
            headers.set(HttpHeaders.REFERER, "https://m.weibo.cn/");
            headers.set("x-requested-with", "XMLHttpRequest");
        });
        if (response == null || response.path("ok").asInt() != 1
                || !response.path("data").path("cards").isArray()) {
            throw new IllegalStateException("微博用户搜索响应异常");
        }
        Set<String> matches = new LinkedHashSet<>();
        // 搜索结果可能把同一用户放在多个卡片中；只接受全称完全相等的用户，不猜测相似名称。
        for (JsonNode user : response.path("data").path("cards").findValues("user")) {
            if (screenName.equals(user.path("screen_name").asText())) {
                String uid = user.path("id").asText("");
                if (!uid.matches("\\d+")) {
                    throw new IllegalStateException("微博用户搜索返回无效UID");
                }
                matches.add(uid);
            }
        }
        if (matches.size() > 1) {
            throw new ScoutApiException(HttpStatus.CONFLICT, "存在多个同名微博博主，请使用UID");
        }
        return matches.stream().findFirst();
    }

    @Override
    public Optional<WeiboPost> fetchLatest(String uid, String fallbackScreenName, WeiboAccount account) {
        if (uid == null || !uid.matches("\\d+")) {
            throw new IllegalArgumentException("微博UID仅支持数字");
        }
        URI uri = URI.create(API_URL + "?type=uid&value=" + uid + "&containerid=107603" + uid);
        JsonNode response = getJson(uri, account, headers -> {
            headers.set(HttpHeaders.ACCEPT, "*/*");
            headers.set(HttpHeaders.REFERER, "https://m.weibo.cn/u/" + uid);
            headers.set("x-requested-with", "XMLHttpRequest");
        });
        if (response == null) {
            throw new IllegalStateException("微博接口返回空响应");
        }
        if (response.path("ok").asInt() != 1) {
            String message = response.path("msg").asText("");
            if (message.isBlank() || "这里还没有内容".equals(message)) {
                return Optional.empty();
            }
            throw new IllegalStateException("微博接口返回异常");
        }
        Optional<ObjectNode> latestCard = parser.selectLatestCard(response);
        if (latestCard.isEmpty()) {
            return Optional.empty();
        }
        JsonNode mblog = latestCard.get().path("mblog");
        // 与解析器的转发深度一致，原文和每层转发分别补全，避免媒体串到外层微博。
        for (int depth = 0; depth <= 5 && mblog.isObject(); depth++) {
            completeLongText(mblog, account);
            completeArticle(mblog, account);
            mblog = mblog.path("retweeted_status");
        }
        return Optional.of(parser.parseCard(latestCard.get(), uid)).map(post -> {
            if (!post.screenName().isBlank() || fallbackScreenName == null) {
                return post;
            }
            return new WeiboPost(
                    post.uid(), fallbackScreenName, post.weiboId(), post.publishedAt(), post.content(),
                    post.rawContent(), post.source(), post.regionName(), post.repostsCount(), post.commentsCount(),
                    post.attitudesCount(), post.url(),
                    post.images(), post.topics(), post.videoCoverImages(), post.retweet(),
                    post.media(), post.links(), post.article(), post.truncated());
        });
    }

    private void completeLongText(JsonNode mblog, WeiboAccount account) {
        if (!(mblog instanceof ObjectNode objectNode)
                || !WeiboResponseParser.needsExtendedText(mblog)) {
            return;
        }
        String id = mblog.path("idstr").asText(mblog.path("id").asText(""));
        if (!id.matches("\\d+")) {
            log.warn("微博长文无法补全：微博 ID 无效");
            return;
        }
        JsonNode response;
        try {
            response = getJson(
                    URI.create("https://m.weibo.cn/statuses/extend?id=" + id),
                    account,
                    headers -> headers.set(HttpHeaders.ACCEPT, "*/*"));
        } catch (RestClientException exception) {
            log.warn("微博长文请求失败，weiboId={}，异常类型={}", id, exception.getClass().getSimpleName());
            return;
        }
        if (response == null || response.path("ok").asInt() != 1) {
            log.warn("微博长文响应无效，weiboId={}", mblog.path("id").asText());
            return;
        }
        JsonNode data = response.path("data");
        for (String field : new String[]{"longTextContent", "fullText", "text", "content"}) {
            String content = data.path(field).asText("");
            if (!content.isBlank()) {
                objectNode.put("text", content);
                ObjectNode longText = objectNode.path("longText") instanceof ObjectNode existing
                        ? existing : objectNode.putObject("longText");
                longText.put("longTextContent", content);
                // 全文接口可能同时返回完整配图、视频及短链映射，只合并有值的已知内容字段。
                for (String metadata : new String[]{"pics", "pic_ids", "pic_infos", "page_info",
                        "mix_media_info", "url_struct", "topic_struct"}) {
                    JsonNode value = data.path(metadata);
                    if (value.isContainerNode() && !value.isEmpty()) {
                        objectNode.set(metadata, value);
                    }
                }
                return;
            }
        }
        log.warn("微博长文响应缺少正文，weiboId={}", id);
    }

    private void completeArticle(JsonNode mblog, WeiboAccount account) {
        if (!(mblog instanceof ObjectNode objectNode)
                || !"article".equals(mblog.path("page_info").path("type").asText())
                || !mblog.path("article").path("content").asText("").isBlank()) {
            return;
        }
        String pageUrl = mblog.path("page_info").path("page_url").asText("");
        Matcher match = Pattern.compile("(?:[?&]id=|/id/)(\\d+)(?:[&#/?]|$)").matcher(pageUrl);
        if (!match.find()) {
            log.warn("微博文章链接缺少文章 ID，保留卡片摘要");
            return;
        }
        String id = match.group(1);
        try {
            // 仅从卡片提取数字 ID；请求地址固定，绝不携带 Cookie 跟随任意卡片链接。
            JsonNode response = getJson(URI.create("https://card.weibo.com/article/m/aj/detail?id=" + id), account,
                    headers -> headers.set(HttpHeaders.REFERER, "https://card.weibo.com/article/m/show/id/" + id));
            if (response != null && response.path("data").isObject()
                    && !response.path("data").path("content").asText("").isBlank()) {
                objectNode.set("article", response.path("data"));
            } else {
                log.warn("微博文章响应无完整正文，articleId={}，保留卡片摘要", id);
            }
        } catch (RestClientException exception) {
            log.warn("微博文章请求失败，articleId={}，异常类型={}", id, exception.getClass().getSimpleName());
        }
    }

    private JsonNode getJson(URI uri, WeiboAccount account, Consumer<HttpHeaders> headersCustomizer) {
        String cookieHeader = cookieStore.buildCookieHeader(account, uri);
        String xsrfToken = cookieStore.getXsrfToken(account, uri);
        try {
            ResponseEntity<JsonNode> response = restClient.get()
                    .uri(uri)
                    .headers(headers -> {
                        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
                        if (!cookieHeader.isBlank()) {
                            headers.set(HttpHeaders.COOKIE, cookieHeader);
                        }
                        if (!xsrfToken.isBlank()) {
                            headers.set("x-xsrf-token", xsrfToken);
                        }
                        headersCustomizer.accept(headers);
                    })
                    .retrieve()
                    .toEntity(JsonNode.class);
            JsonNode body = response.getBody();
            boolean invalidAccount = false;
            // 列表和长文共用登录失效判定，防止长文降级逻辑把失效账号记为成功。
            if (body != null && body.path("ok").asInt() != 1) {
                String message = body.path("msg").asText("");
                invalidAccount = message.contains("请先登录") || message.contains("请重新登录")
                        || message.contains("登录失效") || message.contains("登录已过期")
                        || message.contains("未登录");
            }
            updateResponseState(account, uri, response.getHeaders(), invalidAccount);
            return body;
        } catch (RestClientResponseException exception) {
            updateResponseState(account, uri, exception.getResponseHeaders(), exception.getStatusCode().value() == 401);
            throw exception;
        }
    }

    private void updateResponseState(WeiboAccount account, URI uri, HttpHeaders responseHeaders, boolean invalidAccount) {
        WeiboCookieUpdateException cookieFailure = null;
        try {
            if (responseHeaders != null
                    && cookieStore.applyResponse(account, uri, responseHeaders.get(HttpHeaders.SET_COOKIE))) {
                cookieRepository.save(account);
            }
        } catch (RuntimeException exception) {
            cookieFailure = new WeiboCookieUpdateException("微博Cookie状态保存失败", exception);
        }
        // 已明确失效时仍必须触发停用；Cookie 保存故障附在异常上，避免丢失故障信息。
        if (invalidAccount) {
            WeiboAccountInvalidException invalid = new WeiboAccountInvalidException();
            if (cookieFailure != null) {
                invalid.addSuppressed(cookieFailure);
            }
            throw invalid;
        }
        if (cookieFailure != null) {
            throw cookieFailure;
        }
    }
}
