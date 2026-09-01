package com.hidewnd.winds;


import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author hidewnd
 * @date 2026/8/26 09:39
 */
@Slf4j
@EnableAsync(proxyTargetClass = true)
@EnableScheduling
@SpringBootApplication
public class WindsApplication {

    public static void main(String[] args) {
        SpringApplication.run(WindsApplication.class, args);
        log.info("启动成功 (/≧▽≦)/");
    }
}
