package com.hidewnd.winds.scout.service.impl;

import com.hidewnd.winds.scout.dto.AccountCreateRequest;
import com.hidewnd.winds.scout.dto.AccountCredentialsRequest;
import com.hidewnd.winds.scout.dto.AccountResponse;
import com.hidewnd.winds.scout.exception.ScoutApiException;
import com.hidewnd.winds.scout.model.WeiboAccount;
import com.hidewnd.winds.scout.repository.WeiboAccountRepository;
import com.hidewnd.winds.scout.service.WeiboManagementService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 微博抓取账号管理服务实现，维护账号凭据和状态。
 */
@Service
public class WeiboManagementServiceImpl implements WeiboManagementService {

    private final WeiboAccountRepository accountRepository;
    private final WeiboCookieStore cookieStore;
    private final Clock clock;

    public WeiboManagementServiceImpl(
            WeiboAccountRepository accountRepository,
            WeiboCookieStore cookieStore,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.cookieStore = cookieStore;
        this.clock = clock;
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
