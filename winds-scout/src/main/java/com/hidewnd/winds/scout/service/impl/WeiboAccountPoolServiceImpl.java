package com.hidewnd.winds.scout.service.impl;

import com.hidewnd.winds.scout.model.WeiboAccount;
import com.hidewnd.winds.scout.repository.WeiboAccountRepository;
import com.hidewnd.winds.scout.service.WeiboAccountPoolService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 微博抓取账号池服务实现，负责账号选择、失败退避和自动恢复。
 */
@Service
public class WeiboAccountPoolServiceImpl implements WeiboAccountPoolService {

    private final WeiboAccountRepository repository;
    private final Clock clock;

    public WeiboAccountPoolServiceImpl(WeiboAccountRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public Optional<WeiboAccount> chooseAccount() {
        Instant now = clock.instant();
        List<WeiboAccount> usable = repository.findAll().stream()
                .filter(account -> StringUtils.hasText(account.getCookie())
                        && StringUtils.hasText(account.getXsrfToken()))
                .filter(account -> recoverIfDue(account, now))
                .toList();
        if (usable.isEmpty()) {
            return Optional.empty();
        }
        WeiboAccount account = usable.get(ThreadLocalRandom.current().nextInt(usable.size()));
        account.setRequestCount(account.getRequestCount() + 1);
        account.setLastUsedAt(now);
        account.setUpdatedAt(now);
        return Optional.of(repository.save(account));
    }

    @Override
    public void recordSuccess(String accountId) {
        repository.findById(accountId).ifPresent(account -> {
            account.setStatus("active");
            account.setRecoverAt(null);
            account.setUpdatedAt(clock.instant());
            repository.save(account);
        });
    }

    @Override
    public void recordFailure(String accountId, Exception exception) {
        repository.findById(accountId).ifPresent(account -> {
            Instant now = clock.instant();
            int failCount = account.getFailCount() + 1;
            account.setStatus("fail");
            account.setFailCount(failCount);
            account.setLastErrorAt(now);
            account.setLastErrorMessage(exception.getClass().getSimpleName());
            account.setRecoverAt(switch (failCount) {
                case 1 -> now.plus(Duration.ofHours(1));
                case 2 -> now.plus(Duration.ofHours(3));
                default -> null;
            });
            account.setUpdatedAt(now);
            repository.save(account);
        });
    }

    private boolean recoverIfDue(WeiboAccount account, Instant now) {
        if (account.getStatus() == null || "active".equals(account.getStatus())) {
            return true;
        }
        if (account.getRecoverAt() == null || account.getRecoverAt().isAfter(now)) {
            return false;
        }
        account.setStatus("active");
        account.setRecoverAt(null);
        account.setUpdatedAt(now);
        repository.save(account);
        return true;
    }
}
