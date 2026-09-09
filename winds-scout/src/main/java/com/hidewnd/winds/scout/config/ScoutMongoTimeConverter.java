package com.hidewnd.winds.scout.config;

import org.springframework.data.mongodb.core.convert.MongoConversionContext;
import org.springframework.data.mongodb.core.convert.MongoValueConverter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 微博持久化的 Mongo 时间边界：以北京时间字符串存储，业务层保留 Instant。
 */
public class ScoutMongoTimeConverter implements MongoValueConverter<Instant, String> {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZONE);

    @Override
    public Instant read(String value, MongoConversionContext context) {
        return LocalDateTime.parse(value, FORMATTER).atZone(ZONE).toInstant();
    }

    @Override
    public String write(Instant value, MongoConversionContext context) {
        return FORMATTER.format(value);
    }
}
