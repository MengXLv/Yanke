package com.yangke.forum.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@EnableCaching
@Configuration
public class CacheConfig {

    @Value("${forum.cache.caffeine.post-expire-seconds:300}")
    private int postExpireSeconds;

    @Value("${forum.cache.caffeine.hot-posts-expire-seconds:60}")
    private int hotPostsExpireSeconds;

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(Arrays.asList(
                buildCache("post", postExpireSeconds),
                buildCache("postList", 120),
                buildCache("user", 600),
                buildCache("hotPosts", hotPostsExpireSeconds),
                buildCache("category", 3600)
        ));
        return cacheManager;
    }

    private CaffeineCache buildCache(String name, int expireSeconds) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .expireAfterWrite(expireSeconds, TimeUnit.SECONDS)
                .maximumSize(1000)
                .recordStats()
                .build());
    }
}
