package com.hidewnd.winds.scout.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hidewnd.winds.scout.model.WeiboAccount;
import com.hidewnd.winds.scout.model.WeiboPost;
import com.hidewnd.winds.scout.service.WeiboFetchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

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

    public WeiboFetchServiceImpl(
            @Qualifier("weiboRestClient") RestClient restClient,
            WeiboResponseParser parser) {
        this.restClient = restClient;
        this.parser = parser;
    }

    @Override
    public Optional<WeiboPost> fetchLatest(String uid, String fallbackScreenName, WeiboAccount account) {
        if (uid == null || !uid.matches("\\d+")) {
            throw new IllegalArgumentException("微博UID仅支持数字");
        }
        JsonNode response = restClient.get()
                .uri(API_URL + "?type=uid&value=" + uid + "&containerid=107603" + uid)
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .header(HttpHeaders.ACCEPT, "*/*")
                .header(HttpHeaders.REFERER, "https://m.weibo.cn/u/" + uid)
                .header(HttpHeaders.COOKIE, account.getCookie())
                .header("x-xsrf-token", account.getXsrfToken())
                .header("x-requested-with", "XMLHttpRequest")
                .retrieve()
                .body(JsonNode.class);
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
            response = restClient.get()
                    .uri("https://m.weibo.cn/statuses/extend?id=" + mblog.path("id").asText())
                    .header(HttpHeaders.USER_AGENT, USER_AGENT)
                    .header(HttpHeaders.COOKIE, account.getCookie())
                    .header("x-xsrf-token", account.getXsrfToken())
                    .retrieve()
                    .body(JsonNode.class);
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
}
