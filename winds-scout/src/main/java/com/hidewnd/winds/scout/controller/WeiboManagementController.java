package com.hidewnd.winds.scout.controller;

import com.hidewnd.winds.common.base.response.R;
import com.hidewnd.winds.scout.dto.AccountCreateRequest;
import com.hidewnd.winds.scout.dto.AccountCredentialsRequest;
import com.hidewnd.winds.scout.dto.AccountResponse;
import com.hidewnd.winds.scout.dto.AccountStatusRequest;
import com.hidewnd.winds.scout.dto.BloggerCreateRequest;
import com.hidewnd.winds.scout.dto.BloggerResponse;
import com.hidewnd.winds.scout.service.WeiboManagementService;
import com.hidewnd.winds.scout.service.WeiboSubscriptionService;
import com.hidewnd.winds.scout.model.WeiboPost;
import com.hidewnd.winds.scout.config.ScoutManagementInterceptor;
import com.hidewnd.winds.scout.config.WeiboAccountManagement;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 微博监控管理接口，仅负责 HTTP 参数校验、业务调用和响应封装。
 */
@Tag(name = "微博监控管理", description = "按 Token 订阅博主、查询推文及管理微博抓取账号")
@SecurityScheme(
        name = "Authorization",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "Token",
        description = "有效 Token；账号池接口额外要求 scout.weibo.manage 权限")
@SecurityRequirement(name = "Authorization")
@RestController
@RequestMapping("/scout/weibo")
public class WeiboManagementController {

    private final WeiboManagementService service;

    private final WeiboSubscriptionService subscriptions;

    public WeiboManagementController(WeiboManagementService service, WeiboSubscriptionService subscriptions) {
        this.service = service;
        this.subscriptions = subscriptions;
    }

    // Token 身份只能从鉴权拦截器读取，接口不能操作其他 Token 的订阅。

    @Operation(summary = "查看当前Token订阅的博主")
    @GetMapping("/bloggers")
    public R<List<BloggerResponse>> listBloggers(
            @Parameter(hidden = true) @RequestAttribute(ScoutManagementInterceptor.TOKEN_ATTRIBUTE) String token) {
        return R.successByObj(subscriptions.listBloggers(token));
    }

    @Operation(summary = "订阅微博博主", description = "传UID或仅传screenName全称；重复订阅幂等，共享名称和别称仅首次建档时写入")
    @PostMapping("/bloggers")
    public R<BloggerResponse> createBlogger(
            @Parameter(hidden = true) @RequestAttribute(ScoutManagementInterceptor.TOKEN_ATTRIBUTE) String token,
            @Valid @RequestBody BloggerCreateRequest request) {
        return R.successByObj(subscriptions.subscribe(token, request));
    }

    @Operation(summary = "查看已订阅的博主详情")
    @GetMapping("/bloggers/{uid}")
    public R<BloggerResponse> getBlogger(
            @Parameter(hidden = true) @RequestAttribute(ScoutManagementInterceptor.TOKEN_ATTRIBUTE) String token,
            @Parameter(description = "微博UID") @PathVariable("uid") String uid) {
        return R.successByObj(subscriptions.getBlogger(token, uid));
    }

    @Operation(summary = "取消订阅", description = "仅移除当前Token；重复取消幂等，保留博主信息和历史推文")
    @DeleteMapping("/bloggers/{uid}")
    public R<Void> deleteBlogger(
            @Parameter(hidden = true) @RequestAttribute(ScoutManagementInterceptor.TOKEN_ATTRIBUTE) String token,
            @Parameter(description = "微博UID") @PathVariable("uid") String uid) {
        subscriptions.unsubscribe(token, uid);
        return R.success("取消订阅成功");
    }

    @Operation(summary = "查询最新推文", description = "无需订阅，UID或库中别称精确查询；优先返回库中最新记录，无记录时抓取入库，不触发推送")
    @GetMapping("/posts/latest")
    public R<WeiboPost> latestPost(@Parameter(description = "微博UID或系统记录的别称", required = true)
                                  @RequestParam("query") String query) {
        return R.successByObj(subscriptions.latest(query));
    }

    // 抓取账号管理接口

    @WeiboAccountManagement
    @Operation(summary = "查询抓取账号列表", description = "响应不会返回 Cookie、XSRF Token 和最近错误详情")
    @GetMapping("/accounts")
    public R<List<AccountResponse>> listAccounts() {
        return R.successByObj(service.listAccounts());
    }

    @WeiboAccountManagement
    @Operation(summary = "创建抓取账号")
    @PostMapping("/accounts")
    public R<AccountResponse> createAccount(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "抓取账号 ID 和包含 XSRF-TOKEN 的 Cookie", required = true)
            @Valid @RequestBody AccountCreateRequest request) {
        return R.successByObj(service.createAccount(request));
    }

    @WeiboAccountManagement
    @Operation(summary = "查询抓取账号详情")
    @GetMapping("/accounts/{id}")
    public R<AccountResponse> getAccount(
            @Parameter(description = "抓取账号 ID", example = "account-1") @PathVariable("id") String id) {
        return R.successByObj(service.getAccount(id));
    }

    @WeiboAccountManagement
    @Operation(summary = "更新抓取账号凭据", description = "更新后会恢复账号并清除累计失败状态")
    @PutMapping("/accounts/{id}/credentials")
    public R<AccountResponse> updateCredentials(
            @Parameter(description = "抓取账号 ID", example = "account-1") @PathVariable("id") String id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "包含 XSRF-TOKEN 的新 Cookie", required = true)
            @Valid @RequestBody AccountCredentialsRequest request) {
        return R.successByObj(service.updateCredentials(id, request));
    }

    @WeiboAccountManagement
    @Operation(summary = "修改抓取账号状态")
    @PatchMapping("/accounts/{id}/status")
    public R<AccountResponse> setAccountStatus(
            @Parameter(description = "抓取账号 ID", example = "account-1") @PathVariable("id") String id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "抓取账号状态", required = true)
            @Valid @RequestBody AccountStatusRequest request) {
        return R.successByObj(service.setAccountStatus(id, request.status()));
    }

    @WeiboAccountManagement
    @Operation(summary = "删除抓取账号")
    @DeleteMapping("/accounts/{id}")
    public R<Void> deleteAccount(
            @Parameter(description = "抓取账号 ID", example = "account-1") @PathVariable("id") String id) {
        service.deleteAccount(id);
        return R.success("删除成功");
    }
}
