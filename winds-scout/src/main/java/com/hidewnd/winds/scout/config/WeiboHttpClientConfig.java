package com.hidewnd.winds.scout.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 微博移动端接口客户端配置，统一设置连接和读取超时。
 */
@Configuration
public class WeiboHttpClientConfig {

    /**
     * 创建微博专用 RestClient。
     *
     * @param builder Spring 提供的 RestClient 构建器
     * @return 配置超时后的微博 HTTP 客户端
     */
    @Bean("weiboRestClient")
    public RestClient weiboRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10_000);
        requestFactory.setReadTimeout(15_000);
        return builder.requestFactory(requestFactory).build();
    }
}
