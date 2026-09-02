package com.hidewnd.winds.congfig;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.DbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

/**
 * MongoDB 统一序列化配置。
 */
@Configuration(proxyBeanMethods = false)
public class MongoConfig {

    @Bean
    public MappingMongoConverter mappingMongoConverter(
            ObjectProvider<MongoDatabaseFactory> factory,
            MongoMappingContext mappingContext,
            MongoCustomConversions customConversions) {
        MongoDatabaseFactory databaseFactory = factory.getIfAvailable();
        DbRefResolver dbRefResolver = databaseFactory == null
                ? NoOpDbRefResolver.INSTANCE
                : new DefaultDbRefResolver(databaseFactory);
        MappingMongoConverter converter = new MappingMongoConverter(dbRefResolver, mappingContext);
        converter.setCustomConversions(customConversions);
        // 不持久化 Java 类型信息，Mongo 文档只保留稳定的业务字段。
        converter.setTypeMapper(new DefaultMongoTypeMapper(null));
        return converter;
    }
}
