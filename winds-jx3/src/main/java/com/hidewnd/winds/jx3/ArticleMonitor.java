package com.hidewnd.winds.jx3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.ApplicationEventPublisher;
import java.time.*;
import java.util.UUID;

/** 新闻和维护分别拥有首轮基线，任一来源异常不影响另一个来源。 */
public class ArticleMonitor {
    private final boolean maintenance;
    private final OfficialClient client;
    private final Jx3Store store;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper mapper;
    private final Clock clock;
    private Instant lastSuccess;

    public ArticleMonitor(boolean maintenance, OfficialClient client, Jx3Store store, ApplicationEventPublisher publisher, ObjectMapper mapper, Clock clock) {
        this.maintenance = maintenance; this.client = client; this.store = store; this.publisher = publisher; this.mapper = mapper; this.clock = clock;
    }

    public void poll() {
        Instant now = clock.instant();
        Instant since = maintenance ? now.minus(Duration.ofDays(7)) : lastSuccess == null ? null : lastSuccess.minusSeconds(120);
        var articles = client.articles(maintenance, since);
        for (var article : articles) {
            String key = (maintenance ? "maintenance:" : "news:") + article.articleId();
            ObjectNode state = mapper.valueToTree(article);
            ObjectNode previous = store.load(key);
            if (previous != null) {
                ObjectNode businessBefore = previous.deepCopy();
                ObjectNode businessNow = state.deepCopy();
                businessBefore.remove("updatedAt");
                businessNow.remove("updatedAt");
                if (businessBefore.equals(businessNow)) continue;
            }
            Jx3Event event = lastSuccess == null ? null : Jx3Event.article(UUID.randomUUID().toString(), now, article, previous == null);
            boolean inserted = store.save(key, state, event);
            if (inserted && event != null) publisher.publishEvent(event);
        }
        lastSuccess = now;
    }
}
