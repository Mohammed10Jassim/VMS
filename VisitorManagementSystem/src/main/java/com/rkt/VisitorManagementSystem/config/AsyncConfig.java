package com.rkt.VisitorManagementSystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name="mailTaskExecutor")
    public ThreadPoolTaskExecutor mailTaskExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(4);
        exec.setMaxPoolSize(10);
        exec.setQueueCapacity(200);
        exec.setThreadNamePrefix("mail-exec-");
        exec.initialize();
        return exec;
    }

}
