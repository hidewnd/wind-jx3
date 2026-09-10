package com.hidewnd.winds.congfig;

import org.springframework.boot.web.embedded.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class SpringBootConfig {

    /** 全应用共用虚拟线程执行器，底层资源由 Spring 统一关闭。 */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService applicationExecutorService() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    // taskExecutor 别名确保未指定名称的 @Async 也使用全局执行器，而非默认回退实现。
    @Bean({"asyncTaskExecutor", "taskExecutor"})
    public AsyncTaskExecutor asyncTaskExecutor(
            @Qualifier("applicationExecutorService") ExecutorService executor) {
        return new TaskExecutorAdapter(executor);
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        // 兼容现有同步定时任务；剑三的网络采集只提交到异步执行器，不占用调度线程。
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("winds-scheduler-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    @Bean
    public TomcatProtocolHandlerCustomizer<?> protocolHandlerVirtualThreadExecutorCustomizer(
            @Qualifier("applicationExecutorService") ExecutorService executor) {
        return protocolHandler -> protocolHandler.setExecutor(executor);
    }
}
