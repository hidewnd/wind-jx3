package com.hidewnd.costing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.pattern.PathPatternParser;

@Configuration
public class CorsConfig {

    private static final String ALL = "*";
    private static final Long MAX_AGE = 18000L;

    @Bean
    public CorsWebFilter corsWebFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource(new PathPatternParser());
        CorsConfiguration configuration = new CorsConfiguration();
        // 添加允许的头
        configuration.addAllowedHeader(ALL);
        // 添加允许的方法
        configuration.addAllowedMethod(ALL);
        // 添加允许的主机
        configuration.addAllowedOriginPattern(ALL);
        // 是否允许携带Cookie等信息
        configuration.setAllowCredentials(true);
        // 预检请求的缓存时间（秒），即在这个时间段里，对于相同跨域请求不会再预检了
        configuration.setMaxAge(MAX_AGE);

        configuration.addExposedHeader(ALL);
        // 对哪些路径生效
        source.registerCorsConfiguration("/**", configuration);

        return new CorsWebFilter(source);
    }


}