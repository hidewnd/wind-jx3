package com.hidewnd.winds.scout.service;

import com.hidewnd.winds.scout.dto.BloggerCreateRequest;
import com.hidewnd.winds.scout.dto.BloggerResponse;
import com.hidewnd.winds.scout.model.WeiboPost;
import java.util.List;
import java.util.Set;

/** 按 Token 管理微博订阅及缓存优先的最新推文查询。 */
public interface WeiboSubscriptionService {
    /** 返回当前 Token 的订阅，不暴露任何 Token。 */
    List<BloggerResponse> listBloggers(String token);
    /** 返回已订阅博主详情，未订阅时返回 404。 */
    BloggerResponse getBlogger(String token, String uid);
    /** 以 UID 或微博全称订阅；已有博主的共享信息保持不变。 */
    BloggerResponse subscribe(String token, BloggerCreateRequest request);
    /** 幂等取消调用者的订阅，保留博主与历史推文。 */
    void unsubscribe(String token, String uid);
    /** UID 或库中别称查询；仅无记录时请求微博并入库，不推送。 */
    WeiboPost latest(String query);
    /** 仅供服务端推文分发获取当前订阅者，不用于对外响应。 */
    Set<String> subscriberTokens(String uid);
}
