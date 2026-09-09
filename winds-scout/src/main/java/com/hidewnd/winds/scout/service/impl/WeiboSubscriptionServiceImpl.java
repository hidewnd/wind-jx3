package com.hidewnd.winds.scout.service.impl;

import com.hidewnd.winds.scout.dto.BloggerCreateRequest;
import com.hidewnd.winds.scout.dto.BloggerResponse;
import com.hidewnd.winds.scout.exception.ScoutApiException;
import com.hidewnd.winds.scout.exception.WeiboCookieUpdateException;
import com.hidewnd.winds.scout.model.WeiboAccount;
import com.hidewnd.winds.scout.model.WeiboBlogger;
import com.hidewnd.winds.scout.model.WeiboPost;
import com.hidewnd.winds.scout.repository.WeiboBloggerRepository;
import com.hidewnd.winds.scout.repository.WeiboPostRepository;
import com.hidewnd.winds.scout.repository.WeiboSubscriptionRepository;
import com.hidewnd.winds.scout.service.WeiboAccountPoolService;
import com.hidewnd.winds.scout.service.WeiboFetchService;
import com.hidewnd.winds.scout.service.WeiboSubscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 订阅关系与按需查询的业务入口，数据库异常不归因于抓取账号。 */
@Service
public class WeiboSubscriptionServiceImpl implements WeiboSubscriptionService {
    private final WeiboBloggerRepository bloggers;
    private final WeiboSubscriptionRepository subscriptions;
    private final WeiboPostRepository posts;
    private final WeiboAccountPoolService pool;
    private final WeiboFetchService fetch;
    private final Clock clock;

    public WeiboSubscriptionServiceImpl(WeiboBloggerRepository bloggers, WeiboSubscriptionRepository subscriptions,
            WeiboPostRepository posts, WeiboAccountPoolService pool, WeiboFetchService fetch, Clock clock) {
        this.bloggers = bloggers;
        this.subscriptions = subscriptions;
        this.posts = posts;
        this.pool = pool;
        this.fetch = fetch;
        this.clock = clock;
    }

    @Override
    public List<BloggerResponse> listBloggers(String token) {
        return bloggers.findByTokensContaining(token).stream().map(BloggerResponse::from).toList();
    }

    @Override
    public BloggerResponse getBlogger(String token, String uid) {
        return BloggerResponse.from(bloggers.findByUidAndTokensContaining(uid, token)
                .orElseThrow(() -> new ScoutApiException(HttpStatus.NOT_FOUND, "未订阅该微博博主")));
    }

    @Override
    public BloggerResponse subscribe(String token, BloggerCreateRequest request) {
        String uid = request.uid();
        if (uid == null) {
            WeiboAccount account = pool.chooseAccount()
                    .orElseThrow(() -> new ScoutApiException(HttpStatus.SERVICE_UNAVAILABLE, "没有可用的微博账号"));
            Optional<String> found;
            try {
                found = fetch.findUidByScreenName(request.screenName(), account);
            } catch (ScoutApiException exception) {
                // 匹配歧义是查询结果，不应停用正常账号。
                pool.recordSuccess(account.getId());
                throw exception;
            } catch (WeiboCookieUpdateException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                pool.recordFailure(account.getId(), exception);
                throw new ScoutApiException(HttpStatus.BAD_GATEWAY, "微博用户查询失败");
            }
            pool.recordSuccess(account.getId());
            uid = found.orElseThrow(() -> new ScoutApiException(HttpStatus.NOT_FOUND, "未找到全称匹配的微博博主，请使用UID"));
        }
        return BloggerResponse.from(subscriptions.subscribe(uid, token, request.screenName(), request.aliases(), clock.instant()));
    }

    @Override
    public void unsubscribe(String token, String uid) {
        subscriptions.unsubscribe(uid, token, clock.instant());
    }

    @Override
    public Set<String> subscriberTokens(String uid) {
        return bloggers.findById(uid).map(WeiboBlogger::getTokens)
                .map(Set::copyOf).orElseGet(Set::of);
    }

    @Override
    public WeiboPost latest(String query) {
        if (query == null || query.isBlank()) {
            throw new ScoutApiException(HttpStatus.BAD_REQUEST, "UID或别称不能为空");
        }
        String uid = query;
        String screenName = "";
        if (!query.matches("\\d+")) {
            List<WeiboBlogger> matches = bloggers.findByAliasesContaining(query);
            if (matches.isEmpty()) {
                throw new ScoutApiException(HttpStatus.NOT_FOUND, "微博别称不存在");
            }
            if (matches.size() != 1) {
                throw new ScoutApiException(HttpStatus.CONFLICT, "别称对应多个博主，请使用UID");
            }
            uid = matches.getFirst().getUid();
            screenName = matches.getFirst().getScreenName();
        }
        Optional<WeiboPost> cached = posts.findLatest(uid);
        if (cached.isPresent()) {
            return cached.get();
        }
        WeiboAccount account = pool.chooseAccount()
                .orElseThrow(() -> new ScoutApiException(HttpStatus.SERVICE_UNAVAILABLE, "没有可用的微博账号"));
        Optional<WeiboPost> latest;
        try {
            latest = fetch.fetchLatest(uid, screenName, account);
        } catch (WeiboCookieUpdateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            pool.recordFailure(account.getId(), exception);
            throw new ScoutApiException(HttpStatus.BAD_GATEWAY, "微博推文查询失败");
        }
        pool.recordSuccess(account.getId());
        WeiboPost post = latest.orElseThrow(() -> new ScoutApiException(HttpStatus.NOT_FOUND, "暂无有效微博推文"));
        // 按需补录只供查询，不产生监听事件；入库失败必须向调用者报告失败。
        posts.saveOrSync(post, true);
        return post;
    }
}
