package com.hidewnd.winds.scout.repository;

import com.hidewnd.winds.scout.model.WeiboPost;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;

/**
 * 微博内容和分布式轮询租约的 MongoDB 访问组件。
 */
@Repository
public class WeiboPostRepository {

    private static final String COLLECTION = "weibo_posts";
    private static final String LOCK_COLLECTION = "weibo_monitor_lock";
    private static final String POLL_LOCK_ID = "weibo-poll";
    private static final Duration POLL_LEASE = Duration.ofMinutes(6);

    private final MongoTemplate mongoTemplate;

    public WeiboPostRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * 判断指定博主是否已有微博基线记录。
     *
     * @param uid 微博 UID
     * @return 已存在微博记录时返回 true
     */
    public boolean hasPosts(String uid) {
        return mongoTemplate.exists(Query.query(Criteria.where("uid").is(uid)), COLLECTION);
    }

    /** 按发布时间读取最新监听记录，字段映射复用 WeiboPost 的 Mongo 注解。 */
    public Optional<WeiboPost> findLatest(String uid) {
        Query query = Query.query(Criteria.where("uid").is(uid))
                .with(Sort.by(Sort.Order.desc("date"), Sort.Order.desc("_id"))).limit(1);
        Document document = mongoTemplate.findOne(query, Document.class, COLLECTION);
        if (document == null) {
            return Optional.empty();
        }
        // 旧记录只有平铺转发字段，读取时恢复结构，不批量改写历史文档。
        if (document.get("retweet") == null && Boolean.TRUE.equals(document.getBoolean("is_retweet"))) {
            Document retweet = new Document();
            String[][] fields = {{"title", "screenName"}, {"content", "content"}, {"raw_content", "rawContent"},
                    {"source", "source"}, {"region_name", "regionName"}, {"reposts_count", "repostsCount"},
                    {"comments_count", "commentsCount"}, {"attitudes_count", "attitudesCount"},
                    {"imgs", "images"}, {"video_cover_imgs", "videoCoverImages"}};
            for (String[] field : fields) {
                retweet.put(field[1], document.get("retweet_" + field[0]));
            }
            retweet.put("truncated", false);
            retweet.put("unavailable", false);
            document.put("retweet", retweet);
        }
        document.putIfAbsent("truncated", false);
        return Optional.of(mongoTemplate.getConverter().read(WeiboPost.class, document));
    }

    /**
     * 按微博 ID 原子新增或同步内容，并仅在首次写入时设置推送标记。
     *
     * @param post   微博内容
     * @param noPush 首次写入时是否禁止推送
     * @return 本次操作是否新增了微博记录
     */
    public boolean saveOrSync(WeiboPost post, boolean noPush) {
        Update update = new Update()
                .set("title", post.screenName())
                .set("content", post.content())
                .set("raw_content", post.rawContent())
                .set("source", post.source())
                .set("region_name", post.regionName())
                .set("reposts_count", post.repostsCount())
                .set("comments_count", post.commentsCount())
                .set("attitudes_count", post.attitudesCount())
                .set("imgs", post.images())
                .set("topics", post.topics())
                .set("video_cover_imgs", post.videoCoverImages())
                .set("media", post.media())
                .set("links", post.links())
                .set("article", post.article())
                .set("truncated", post.truncated())
                .set("date", post.publishedAt())
                .set("url", post.url())
                .setOnInsert("uid", post.uid())
                .setOnInsert("no_push", noPush)
                .setOnInsert("sent_bots", new ArrayList<>())
                .setOnInsert("created_at", Instant.now());
        if (post.retweet() != null) {
            update.set("is_retweet", true)
                    .set("retweet_title", post.retweet().screenName())
                    .set("retweet_content", post.retweet().content())
                    .set("retweet_raw_content", post.retweet().rawContent())
                    .set("retweet_source", post.retweet().source())
                    .set("retweet_region_name", post.retweet().regionName())
                    .set("retweet_reposts_count", post.retweet().repostsCount())
                    .set("retweet_comments_count", post.retweet().commentsCount())
                    .set("retweet_attitudes_count", post.retweet().attitudesCount())
                    .set("retweet_imgs", post.retweet().images())
                    .set("retweet_video_cover_imgs", post.retweet().videoCoverImages())
                    .set("retweet", post.retweet());
        } else {
            update.unset("is_retweet")
                    .unset("retweet_title")
                    .unset("retweet_content")
                    .unset("retweet_raw_content")
                    .unset("retweet_source")
                    .unset("retweet_region_name")
                    .unset("retweet_reposts_count")
                    .unset("retweet_comments_count")
                    .unset("retweet_attitudes_count")
                    .unset("retweet_imgs")
                    .unset("retweet_video_cover_imgs")
                    .unset("retweet");
        }
        UpdateResult result = mongoTemplate.upsert(
                Query.query(Criteria.where("_id").is(post.weiboId())), update, COLLECTION);
        return result.getUpsertedId() != null;
    }

    /**
     * 获取或续期当前实例的微博轮询租约。
     *
     * @param owner 当前轮询实例标识
     * @param now   当前时间
     * @return 成功持有租约时返回 true
     */
    public boolean tryAcquirePollLease(String owner, Instant now) {
        Criteria available = new Criteria().orOperator(
                Criteria.where("owner").is(owner),
                Criteria.where("lease_until").lte(now),
                Criteria.where("lease_until").exists(false));
        Query query = Query.query(Criteria.where("_id").is(POLL_LOCK_ID).andOperator(available));
        Update update = new Update()
                .set("owner", owner)
                .set("lease_until", now.plus(POLL_LEASE))
                .set("updated_at", now)
                .setOnInsert("_id", POLL_LOCK_ID);
        try {
            Document lock = mongoTemplate.findAndModify(
                    query,
                    update,
                    FindAndModifyOptions.options().upsert(true).returnNew(true),
                    Document.class,
                    LOCK_COLLECTION);
            return lock != null && owner.equals(lock.getString("owner"));
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }
}
