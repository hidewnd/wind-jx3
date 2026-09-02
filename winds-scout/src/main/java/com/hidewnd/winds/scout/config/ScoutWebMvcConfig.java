package com.hidewnd.winds.scout.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Scout 管理接口的 Spring MVC 配置，仅为微博管理路径注册鉴权拦截器。
 */
@Configuration
public class ScoutWebMvcConfig implements WebMvcConfigurer {

    private final ScoutManagementInterceptor interceptor;

    public ScoutWebMvcConfig(ScoutManagementInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/scout/weibo/**");
    }
}
