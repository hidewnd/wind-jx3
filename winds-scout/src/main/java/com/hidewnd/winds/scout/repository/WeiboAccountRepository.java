package com.hidewnd.winds.scout.repository;

import com.hidewnd.winds.scout.model.WeiboAccount;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 微博抓取账号的 MongoDB 访问接口。
 */
public interface WeiboAccountRepository extends MongoRepository<WeiboAccount, String> {
}
