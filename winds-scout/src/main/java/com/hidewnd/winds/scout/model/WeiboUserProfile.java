package com.hidewnd.winds.scout.model;

/** 微博主页接口返回的博主公开资料，与抓取账号凭据无关。 */
public record WeiboUserProfile(String screenName, String avatar) {
}
