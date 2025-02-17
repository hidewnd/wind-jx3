package com.hidewnd.aggregation.dao;

import com.hidewnd.aggregation.entity.LocustUser;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Component;

@Component
public interface LocustRepository extends MongoRepository<LocustUser, String> {

}
