package com.hidewnd.winds.scout.config;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 从 MongoDB 有效令牌记录中校验 Scout 微博管理权限。
 */
@Component
public class ScoutManagementTokenAuthenticator {

    private static final String TOKEN_COLLECTION = "ws_auth_token";
    private static final String REQUIRED_SCOPE = "scout.weibo.manage";

    private final MongoTemplate mongoTemplate;

    public ScoutManagementTokenAuthenticator(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * 校验令牌有效性及 {@code scout.weibo.manage} 权限。
     *
     * @param token Bearer Token
     * @return 区分已授权、无效令牌和权限不足的鉴权结果
     */
    public ScoutAuthorization authorize(String token) {
        if (!StringUtils.hasText(token)) {
            return ScoutAuthorization.INVALID;
        }
        Query query = Query.query(Criteria.where("token").is(token).and("status").is(1));
        Document document = mongoTemplate.findOne(query, Document.class, TOKEN_COLLECTION);
        if (document == null) {
            return ScoutAuthorization.INVALID;
        }
        Object scopes = document.get("scopes");
        return scopes instanceof List<?> values && values.contains(REQUIRED_SCOPE)
                ? ScoutAuthorization.AUTHORIZED
                : ScoutAuthorization.FORBIDDEN;
    }
}
