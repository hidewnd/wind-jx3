package com.hidewnd.winds.scout.repository;

import com.hidewnd.winds.scout.model.WeiboBlogger;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * 微博监控博主的 MongoDB 访问接口。
 */
public interface WeiboBloggerRepository extends MongoRepository<WeiboBlogger, String> {

    /** 仅有订阅 Token 的博主参与轮询；历史 enabled 不再作为全局开关。 */
    @Query("{'tokens.0': {$exists: true}}")
    List<WeiboBlogger> findSubscribed();

    /** 查询当前 Token 的订阅。 */
    List<WeiboBlogger> findByTokensContaining(String token);

    /** 详情查询在数据库边界限定订阅者。 */
    Optional<WeiboBlogger> findByUidAndTokensContaining(String uid, String token);

    /** 别称精确匹配，可返回多个 UID，由业务层拒绝歧义。 */
    List<WeiboBlogger> findByAliasesContaining(String alias);
}
