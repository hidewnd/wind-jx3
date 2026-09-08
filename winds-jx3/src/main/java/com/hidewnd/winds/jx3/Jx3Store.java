package com.hidewnd.winds.jx3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/** 每次变化作为一个不可变修订写入；状态和事件同文档原子保存，不依赖 MongoDB 事务部署。 */
public class Jx3Store {
    private static final String COLLECTION = "jx3_records";
    private final MongoTemplate mongo;
    private final ObjectMapper mapper;
    private boolean indexed;

    public Jx3Store(MongoTemplate mongo, ObjectMapper mapper) {
        this.mongo = mongo;
        this.mapper = mapper;
    }

    private synchronized void ensureIndex() {
        if (!indexed) {
            mongo.indexOps(COLLECTION).ensureIndex(new Index().on("key", Sort.Direction.ASC).on("revision", Sort.Direction.DESC).unique());
            indexed = true;
        }
    }

    private Document latest(String key) {
        ensureIndex();
        return mongo.findOne(Query.query(Criteria.where("key").is(key)).with(Sort.by(Sort.Direction.DESC, "revision")), Document.class, COLLECTION);
    }

    public ObjectNode load(String key) {
        Document record = latest(key);
        return record == null ? null : mapper.valueToTree(record.get("state"));
    }

    public boolean save(String key, ObjectNode state, Jx3Event event) {
        Document previous = latest(key);
        Document storedState = Document.parse(state.toString());
        if (previous != null && previous.get("state").equals(storedState)) return false;
        long revision = previous == null ? 1 : ((Number) previous.get("revision")).longValue() + 1;
        Document record = new Document("_id", key + ":" + revision).append("key", key).append("revision", revision)
                .append("state", storedState).append("recordedAt", Jx3Event.time(java.time.Instant.now()))
                .append("event", event == null ? null : Document.parse(mapper.valueToTree(event).toString()));
        mongo.insert(record, COLLECTION);
        return true;
    }
}
