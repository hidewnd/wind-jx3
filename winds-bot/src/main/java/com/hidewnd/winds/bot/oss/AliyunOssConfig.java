package com.hidewnd.winds.bot.oss;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(AliyunOssProperties.class)
public class AliyunOssConfig {

    @Bean(destroyMethod = "shutdown")
    public OSS ossClient(AliyunOssProperties properties) {
        Assert.hasText(properties.getEndpoint(), "aliyun.oss.endpoint 不能为空");
        Assert.hasText(properties.getRegion(), "aliyun.oss.region 不能为空");
        Assert.hasText(properties.getAccessKeyId(), "aliyun.oss.access-key-id 不能为空");
        Assert.hasText(properties.getAccessKeySecret(), "aliyun.oss.access-key-secret 不能为空");

        ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
        configuration.setSignatureVersion(SignVersion.V4);
        return OSSClientBuilder.create()
                .endpoint(properties.getEndpoint())
                .credentialsProvider(new DefaultCredentialProvider(
                        properties.getAccessKeyId(), properties.getAccessKeySecret()))
                .clientConfiguration(configuration)
                .region(properties.getRegion())
                .build();
    }

    @Bean
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }
}
