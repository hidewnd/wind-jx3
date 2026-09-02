package com.hidewnd.winds.scout.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hidewnd.winds.scout.exception.WeiboCookieUpdateException;
import com.hidewnd.winds.scout.model.WeiboAccount;
import com.hidewnd.winds.scout.model.WeiboPost;
import com.hidewnd.winds.scout.repository.WeiboAccountCookieRepository;
import com.hidewnd.winds.scout.service.WeiboFetchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.Optional;
import java.util.function.Consumer;

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
        completeLongText(mblog, account);
        completeLongText(mblog.path("retweeted_status"), account);
        return Optional.of(parser.parseCard(latestCard.get(), uid)).map(post -> {
            if (!post.screenName().isBlank() || fallbackScreenName == null) {
                return post;
            }
            return new WeiboPost(
                    post.uid(), fallbackScreenName, post.weiboId(), post.publishedAt(), post.content(),
                    post.rawContent(), post.source(), post.regionName(), post.repostsCount(), post.commentsCount(),
                    post.attitudesCount(), post.url(),
                    post.images(), post.topics(), post.videoCoverImages(), post.retweet());
        });
    }

    private void completeLongText(JsonNode mblog, WeiboAccount account) {
        if (!(mblog instanceof ObjectNode objectNode)
                || !needsExtendedText(mblog)
                || mblog.path("id").asText("").isBlank()) {
            return;
        }
        JsonNode response;
        try {
            response = getJson(
                    URI.create("https://m.weibo.cn/statuses/extend?id=" + mblog.path("id").asText()),
                    account,
                    headers -> headers.set(HttpHeaders.ACCEPT, "*/*"));
        } catch (RestClientException exception) {
            log.warn("微博长文请求失败，weiboId={}", mblog.path("id").asText(), exception);
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
                break;
            }
        }
    }

    private boolean needsExtendedText(JsonNode mblog) {
        String text = mblog.path("text").asText("");
        return mblog.path("isLongText").asBoolean(false)
                || mblog.path("isLongText").asInt(0) == 1
                || mblog.path("is_long_text").asBoolean(false)
                || text.contains("展开全文")
                || text.endsWith("...全文")
                || text.endsWith("…全文");
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
            updateCookies(account, uri, response.getHeaders());
            return response.getBody();
        } catch (RestClientResponseException exception) {
            updateCookies(account, uri, exception.getResponseHeaders());
            throw exception;
        }
    }

    private void updateCookies(WeiboAccount account, URI uri, HttpHeaders responseHeaders) {
        try {
            if (responseHeaders != null
                    && cookieStore.applyResponse(account, uri, responseHeaders.get(HttpHeaders.SET_COOKIE))) {
                cookieRepository.save(account);
            }
        } catch (RuntimeException exception) {
            throw new WeiboCookieUpdateException("微博Cookie状态保存失败", exception);
        }
    }
}
