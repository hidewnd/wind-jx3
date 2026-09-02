package com.hidewnd.winds.scout.service.impl;

import com.hidewnd.winds.scout.dto.AccountCreateRequest;
import com.hidewnd.winds.scout.dto.AccountCredentialsRequest;
import com.hidewnd.winds.scout.dto.AccountResponse;
import com.hidewnd.winds.scout.dto.BloggerCreateRequest;
import com.hidewnd.winds.scout.dto.BloggerResponse;
import com.hidewnd.winds.scout.dto.BloggerUpdateRequest;
import com.hidewnd.winds.scout.exception.ScoutApiException;
import com.hidewnd.winds.scout.model.WeiboAccount;
import com.hidewnd.winds.scout.model.WeiboBlogger;
import com.hidewnd.winds.scout.repository.WeiboAccountRepository;
import com.hidewnd.winds.scout.repository.WeiboBloggerRepository;
import com.hidewnd.winds.scout.service.WeiboManagementService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 微博监控管理服务实现，维护博主配置和抓取账号状态。
 */
@Service
public class WeiboManagementServiceImpl implements WeiboManagementService {

    private final WeiboBloggerRepository bloggerRepository;
    private final WeiboAccountRepository accountRepository;
    private final WeiboCookieStore cookieStore;
    private final Clock clock;

    public WeiboManagementServiceImpl(
            WeiboBloggerRepository bloggerRepository,
            WeiboAccountRepository accountRepository,
            WeiboCookieStore cookieStore,
            Clock clock) {
        this.bloggerRepository = bloggerRepository;
        this.accountRepository = accountRepository;
        this.cookieStore = cookieStore;
        this.clock = clock;
    }

    @Override
    public List<BloggerResponse> listBloggers() {
        return bloggerRepository.findAll().stream().map(BloggerResponse::from).toList();
    }

    @Override
    public BloggerResponse getBlogger(String uid) {
        return BloggerResponse.from(requireBlogger(uid));
    }

    @Override
    public BloggerResponse createBlogger(BloggerCreateRequest request) {
        String uid = request.uid();
        Instant now = clock.instant();
        WeiboBlogger blogger = new WeiboBlogger(
                uid, uid,
                request.screenName() == null ? "" : request.screenName(),
                request.aliases() == null ? List.of() : request.aliases(),
                request.enabled() == null || request.enabled(), 60, List.of(), now, now);
        try {
            return BloggerResponse.from(bloggerRepository.insert(blogger));
        } catch (DuplicateKeyException exception) {
            throw new ScoutApiException(HttpStatus.CONFLICT, "微博博主已存在");
        }
    }

    @Override
    public BloggerResponse updateBlogger(String uid, BloggerUpdateRequest request) {
        WeiboBlogger blogger = requireBlogger(uid);
        blogger.setScreenName(request.screenName() == null ? "" : request.screenName());
        blogger.setAliases(request.aliases() == null ? List.of() : request.aliases());
        if (request.enabled() != null) {
            blogger.setEnabled(request.enabled());
        }
        blogger.setUpdatedAt(clock.instant());
        return BloggerResponse.from(bloggerRepository.save(blogger));
    }

    @Override
    public BloggerResponse setBloggerStatus(String uid, boolean enabled) {
        WeiboBlogger blogger = requireBlogger(uid);
        blogger.setEnabled(enabled);
        blogger.setUpdatedAt(clock.instant());
        return BloggerResponse.from(bloggerRepository.save(blogger));
    }

    @Override
    public void deleteBlogger(String uid) {
        requireBlogger(uid);
        bloggerRepository.deleteById(uid);
    }

    @Override
    public List<AccountResponse> listAccounts() {
        return accountRepository.findAll().stream().map(AccountResponse::from).toList();
    }

    @Override
    public AccountResponse getAccount(String id) {
        return AccountResponse.from(requireAccount(id));
    }

    @Override
    public AccountResponse createAccount(AccountCreateRequest request) {
        String id = request.id();
        Instant now = clock.instant();
        WeiboAccount account = new WeiboAccount(
                id, request.cookie(), "", "active", 0, 0,
                null, "", null, null, now, now);
        replaceCredentials(account, request.cookie());
        try {
            return AccountResponse.from(accountRepository.insert(account));
        } catch (DuplicateKeyException exception) {
            throw new ScoutApiException(HttpStatus.CONFLICT, "微博账号已存在");
        }
    }

    @Override
    public AccountResponse updateCredentials(String id, AccountCredentialsRequest request) {
        WeiboAccount account = requireAccount(id);
        replaceCredentials(account, request.cookie());
        account.setStatus("active");
        account.setFailCount(0);
        account.setLastErrorAt(null);
        account.setLastErrorMessage("");
        account.setRecoverAt(null);
        account.setUpdatedAt(clock.instant());
        return AccountResponse.from(accountRepository.save(account));
    }

    @Override
    public AccountResponse setAccountStatus(String id, String status) {
        WeiboAccount account = requireAccount(id);
        account.setStatus(status);
        account.setRecoverAt(null);
        account.setUpdatedAt(clock.instant());
        return AccountResponse.from(accountRepository.save(account));
    }

    @Override
    public void deleteAccount(String id) {
        requireAccount(id);
        accountRepository.deleteById(id);
    }

    private WeiboBlogger requireBlogger(String uid) {
        return bloggerRepository.findById(uid)
                .orElseThrow(() -> new ScoutApiException(HttpStatus.NOT_FOUND, "微博博主不存在"));
    }

    private WeiboAccount requireAccount(String id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ScoutApiException(HttpStatus.NOT_FOUND, "微博账号不存在"));
    }

    private void replaceCredentials(WeiboAccount account, String cookie) {
        cookieStore.replaceSeed(account, cookie);
        if (!StringUtils.hasText(account.getXsrfToken())) {
            throw new ScoutApiException(HttpStatus.BAD_REQUEST, "Cookie中缺少有效的XSRF-TOKEN");
        }
    }

}
