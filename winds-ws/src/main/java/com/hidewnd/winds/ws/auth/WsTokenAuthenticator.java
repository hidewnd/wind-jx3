package com.hidewnd.winds.ws.auth;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class WsTokenAuthenticator {

    static final String TOKEN_COLLECTION = "ws_auth_token";

    private final MongoTemplate mongoTemplate;

    public WsTokenAuthenticator(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public boolean isAuthorized(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        Query query = Query.query(Criteria.where("token").is(token).and("status").is(1));
        return mongoTemplate.exists(query, TOKEN_COLLECTION);
    }
}
