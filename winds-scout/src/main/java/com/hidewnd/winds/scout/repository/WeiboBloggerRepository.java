package com.hidewnd.winds.scout.repository;

import com.hidewnd.winds.scout.model.WeiboBlogger;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * 微博监控博主的 MongoDB 访问接口。
 */
public interface WeiboBloggerRepository extends MongoRepository<WeiboBlogger, String> {

    /**
     * 查询 enabled 不等于指定值的博主，用于兼容未设置 enabled 的历史记录。
     *
     * @param enabled 需要排除的启用状态
     * @return 符合条件的博主列表
     */
    List<WeiboBlogger> findByEnabledNot(Boolean enabled);
}
