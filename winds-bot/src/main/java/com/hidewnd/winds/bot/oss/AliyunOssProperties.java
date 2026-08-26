package com.hidewnd.winds.bot.oss;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "aliyun.oss")
public class AliyunOssProperties {

    private String endpoint;
    private String region;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucket;
    private String publicUrl;
}
