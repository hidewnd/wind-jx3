package com.hidewnd.winds.scout.service;

import com.hidewnd.winds.scout.dto.AccountCreateRequest;
import com.hidewnd.winds.scout.dto.AccountCredentialsRequest;
import com.hidewnd.winds.scout.dto.AccountResponse;

import java.util.List;

/**
 * 微博抓取账号管理服务。
 */
public interface WeiboManagementService {

    /**
     * 查询全部微博抓取账号，不返回登录凭据。
     *
     * @return 账号列表
     */
    List<AccountResponse> listAccounts();

    /**
     * 查询指定微博抓取账号，不返回登录凭据。
     *
     * @param id 账号 ID
     * @return 账号信息
     */
    AccountResponse getAccount(String id);

    /**
     * 创建微博抓取账号。
     *
     * @param request 账号及登录凭据
     * @return 创建后的账号信息
     */
    AccountResponse createAccount(AccountCreateRequest request);

    /**
     * 更新账号登录凭据，并恢复账号可用状态和失败计数。
     *
     * @param id      账号 ID
     * @param request 新的登录凭据
     * @return 更新后的账号信息
     */
    AccountResponse updateCredentials(String id, AccountCredentialsRequest request);

    /**
     * 修改微博抓取账号状态。
     *
     * @param id     账号 ID
     * @param status 账号状态
     * @return 更新后的账号信息
     */
    AccountResponse setAccountStatus(String id, String status);

    /**
     * 删除指定微博抓取账号。
     *
     * @param id 账号 ID
     */
    void deleteAccount(String id);
}
