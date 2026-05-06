package com._project4.project4.common;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class CommandExecutorConfig {

    @Bean(name = "commandExecutor")
    public Executor commandExecutor(
            @Value("${robot.command.executor.core-pool-size:2}") int corePoolSize,
            @Value("${robot.command.executor.max-pool-size:8}") int maxPoolSize,
            @Value("${robot.command.executor.queue-capacity:200}") int queueCapacity,
            @Value("${robot.command.executor.keep-alive-seconds:60}") int keepAliveSeconds
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("robot-command-");
        executor.setCorePoolSize(Math.max(1, corePoolSize));
        executor.setMaxPoolSize(Math.max(corePoolSize, maxPoolSize));
        executor.setQueueCapacity(Math.max(1, queueCapacity));
        executor.setKeepAliveSeconds(Math.max(10, keepAliveSeconds));
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}