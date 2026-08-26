package com.hidewnd.winds.bot.huangli.repository;

import com.hidewnd.winds.bot.huangli.model.HuangliInfo;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HuangliRepository extends MongoRepository<HuangliInfo, String> {
}
