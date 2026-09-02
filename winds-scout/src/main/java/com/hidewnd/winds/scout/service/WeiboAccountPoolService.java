package com.hidewnd.winds.scout.service;

import com.hidewnd.winds.scout.model.WeiboAccount;

import java.util.Optional;

/**
 * 微博抓取账号池服务。
 */
public interface WeiboAccountPoolService {

    /**
     * 从账号池中随机选择一个凭据完整且当前可用的账号，并记录本次使用。
     *
     * @return 可用账号；没有可用账号时返回空
     */
    Optional<WeiboAccount> chooseAccount();

    /**
     * 记录账号请求成功，并恢复账号的可用状态。
     *
     * @param accountId 账号 ID
     */
    void recordSuccess(String accountId);

    /**
     * 记录账号请求失败，并根据连续失败次数设置自动恢复时间或停用账号。
     *
     * @param accountId 账号 ID
     * @param exception 本次请求异常
     */
    void recordFailure(String accountId, Exception exception);
}
