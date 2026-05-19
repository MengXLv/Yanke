package com.yangke.forum.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置
 *
 * 用途：
 * - esExecutor: Elasticsearch 索引/更新/删除（不阻塞主事务）
 * - notifyExecutor: Kafka 通知投递 + SSE 推送
 * - cacheExecutor: Redis/Caffeine 缓存清理
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public ThreadPoolTaskExecutor getAsyncExecutor() {
        return executor("async-default", 5, 20, 100);
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                log.error("Async method [{}.{}] failed", method.getDeclaringClass().getSimpleName(),
                        method.getName(), ex);
    }

    @Bean("esExecutor")
    public ThreadPoolTaskExecutor esExecutor() {
        return executor("es", 3, 10, 200);
    }

    @Bean("notifyExecutor")
    public ThreadPoolTaskExecutor notifyExecutor() {
        return executor("notify", 3, 10, 500);
    }

    @Bean("cacheExecutor")
    public ThreadPoolTaskExecutor cacheExecutor() {
        return executor("cache", 2, 5, 200);
    }

    private ThreadPoolTaskExecutor executor(String name, int core, int max, int queueSize) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("forum-" + name + "-");
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queueSize);
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);

        // 拒绝策略：队列满 → 调用者线程执行（降级，不丢任务）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                if (!e.isShutdown()) {
                    log.warn("ThreadPool [{}] queue full ({}), running in caller thread",
                            name, e.getQueue().size());
                }
                super.rejectedExecution(r, e);
            }
        });

        executor.initialize();
        return executor;
    }
}
