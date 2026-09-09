package com.hidewnd.winds.scout.repository;

import com.hidewnd.winds.scout.model.WeiboBlogger;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/** 博主订阅的原子写入，避免多个 Token 的读改写相互覆盖。 */
@Repository
public class WeiboSubscriptionRepository {
    private final MongoTemplate mongoTemplate;

    public WeiboSubscriptionRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** 首次插入初始化公共信息，后续订阅只追加当前 Token。 */
    public WeiboBlogger subscribe(String uid, String token, String screenName, List<String> aliases, Instant now) {
        Query query = Query.query(Criteria.where("_id").is(uid));
        Update update = new Update().addToSet("tokens", token).set("updated_at", now)
                .setOnInsert("uid", uid)
                .setOnInsert("screen_name", screenName == null ? "" : screenName)
                .setOnInsert("aliases", aliases == null ? List.of() : aliases)
                .setOnInsert("created_at", now);
        WeiboBlogger blogger;
        try {
            blogger = mongoTemplate.findAndModify(query, update,
                    FindAndModifyOptions.options().upsert(true).returnNew(true), WeiboBlogger.class);
        } catch (DuplicateKeyException exception) {
            // 并发首次订阅可能争用同一个 UID；在胜出者记录上追加，不覆盖公共配置。
            blogger = mongoTemplate.findAndModify(query, update,
                    FindAndModifyOptions.options().returnNew(true), WeiboBlogger.class);
        }
        if (blogger == null) {
            throw new IllegalStateException("微博订阅保存失败");
        }
        return blogger;
    }

    /** 只移除调用者，保留其他订阅及博主历史资料；重复取消不产生新记录。 */
    public void unsubscribe(String uid, String token, Instant now) {
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(uid).and("tokens").is(token)),
                new Update().pull("tokens", token).set("updated_at", now), WeiboBlogger.class);
    }
}
