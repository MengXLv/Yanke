package com.yangke.forum.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 客户端配置
 *
 * 面试要点：
 * - RLock 可重入锁，看门狗（watchdog）自动续期 30s，防止业务执行超时锁被释放
 * - 对比手写 SETNX：Redisson 内置 Lua 解锁校验（只能解自己的锁）、自动续期、公平锁、红锁(RedLock)
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort)
                .setConnectionPoolSize(32)
                .setConnectionMinimumIdleSize(8);
        return Redisson.create(config);
    }
}
