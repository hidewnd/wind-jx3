package com.hidewnd.winds.scout.controller;

import com.hidewnd.winds.common.base.response.R;
import com.hidewnd.winds.scout.dto.AccountCreateRequest;
import com.hidewnd.winds.scout.dto.AccountCredentialsRequest;
import com.hidewnd.winds.scout.dto.AccountResponse;
import com.hidewnd.winds.scout.dto.AccountStatusRequest;
import com.hidewnd.winds.scout.dto.BloggerCreateRequest;
import com.hidewnd.winds.scout.dto.BloggerResponse;
import com.hidewnd.winds.scout.dto.BloggerStatusRequest;
import com.hidewnd.winds.scout.dto.BloggerUpdateRequest;
import com.hidewnd.winds.scout.service.WeiboManagementService;
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
@Tag(name = "微博监控管理", description = "管理监控博主和微博抓取账号")
@SecurityScheme(
        name = "Authorization",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "Token",
        description = "具有 scout.weibo.manage 权限的管理令牌")
@SecurityRequirement(name = "Authorization")
@RestController
@RequestMapping("/scout/weibo")
public class WeiboManagementController {

    private final WeiboManagementService service;

    public WeiboManagementController(WeiboManagementService service) {
        this.service = service;
    }

    // 博主管理接口

    @Operation(summary = "查询监控博主列表")
    @GetMapping("/bloggers")
    public R<List<BloggerResponse>> listBloggers() {
        return R.successByObj(service.listBloggers());
    }

    @Operation(summary = "创建监控博主")
    @PostMapping("/bloggers")
    public R<BloggerResponse> createBlogger(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "博主创建参数", required = true)
            @Valid @RequestBody BloggerCreateRequest request) {
        return R.successByObj(service.createBlogger(request));
    }

    @Operation(summary = "查询监控博主详情")
    @GetMapping("/bloggers/{uid}")
    public R<BloggerResponse> getBlogger(
            @Parameter(description = "微博 UID", example = "1761587065")
            @PathVariable String uid) {
        return R.successByObj(service.getBlogger(uid));
    }

    @Operation(summary = "更新监控博主配置", description = "不会修改 groups 和 frequency 字段")
    @PutMapping("/bloggers/{uid}")
    public R<BloggerResponse> updateBlogger(
            @Parameter(description = "微博 UID", example = "1761587065") @PathVariable String uid,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "博主更新参数", required = true)
            @Valid @RequestBody BloggerUpdateRequest request) {
        return R.successByObj(service.updateBlogger(uid, request));
    }

    @Operation(summary = "修改监控博主状态")
    @PatchMapping("/bloggers/{uid}/status")
    public R<BloggerResponse> setBloggerStatus(
            @Parameter(description = "微博 UID", example = "1761587065") @PathVariable String uid,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "博主启用状态", required = true)
            @Valid @RequestBody BloggerStatusRequest request) {
        return R.successByObj(service.setBloggerStatus(uid, request.enabled()));
    }

    @Operation(summary = "删除监控博主")
    @DeleteMapping("/bloggers/{uid}")
    public R<Void> deleteBlogger(
            @Parameter(description = "微博 UID", example = "1761587065") @PathVariable String uid) {
        service.deleteBlogger(uid);
        return R.success("删除成功");
    }

    // 抓取账号管理接口

    @Operation(summary = "查询抓取账号列表", description = "响应不会返回 Cookie、XSRF Token 和最近错误详情")
    @GetMapping("/accounts")
    public R<List<AccountResponse>> listAccounts() {
        return R.successByObj(service.listAccounts());
    }

    @Operation(summary = "创建抓取账号")
    @PostMapping("/accounts")
    public R<AccountResponse> createAccount(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "抓取账号 ID 和包含 XSRF-TOKEN 的 Cookie", required = true)
            @Valid @RequestBody AccountCreateRequest request) {
        return R.successByObj(service.createAccount(request));
    }

    @Operation(summary = "查询抓取账号详情")
    @GetMapping("/accounts/{id}")
    public R<AccountResponse> getAccount(
            @Parameter(description = "抓取账号 ID", example = "account-1") @PathVariable String id) {
        return R.successByObj(service.getAccount(id));
    }

    @Operation(summary = "更新抓取账号凭据", description = "更新后会恢复账号并清除累计失败状态")
    @PutMapping("/accounts/{id}/credentials")
    public R<AccountResponse> updateCredentials(
            @Parameter(description = "抓取账号 ID", example = "account-1") @PathVariable String id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "包含 XSRF-TOKEN 的新 Cookie", required = true)
            @Valid @RequestBody AccountCredentialsRequest request) {
        return R.successByObj(service.updateCredentials(id, request));
    }

    @Operation(summary = "修改抓取账号状态")
    @PatchMapping("/accounts/{id}/status")
    public R<AccountResponse> setAccountStatus(
            @Parameter(description = "抓取账号 ID", example = "account-1") @PathVariable String id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "抓取账号状态", required = true)
            @Valid @RequestBody AccountStatusRequest request) {
        return R.successByObj(service.setAccountStatus(id, request.status()));
    }

    @Operation(summary = "删除抓取账号")
    @DeleteMapping("/accounts/{id}")
    public R<Void> deleteAccount(
            @Parameter(description = "抓取账号 ID", example = "account-1") @PathVariable String id) {
        service.deleteAccount(id);
        return R.success("删除成功");
    }
}
