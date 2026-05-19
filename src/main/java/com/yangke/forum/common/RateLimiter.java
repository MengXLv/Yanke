package com.yangke.forum.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

/**
 * 分布式滑动窗口限流器（Redis ZSet + Lua 原子脚本）
 *
 * 面试要点：
 * - 滑动窗口 vs 固定窗口：固定窗口存在边界突发（window边界两侧请求集中通过），
 *   滑动窗口记录每次请求的时间戳，窗口随时间平滑移动，精度更高
 * - Lua 原子性：ZREMRANGEBYSCORE + ZADD + ZCARD + EXPIRE 四条命令在 Redis 单线程中
 *   原子执行，杜绝竞态条件
 * - ZSet 按时间戳排序，ZREMRANGEBYSCORE 清理过期记录，ZCARD 统计窗口内请求数
 */
@Slf4j
@Component
public class RateLimiter {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String RATE_KEY_PREFIX = "rate:";

    private final DefaultRedisScript<Long> script;

    public RateLimiter() {
        // Lua 5.1+ 脚本：原子地检查并记录滑动窗口请求
        String lua =
            "local now = tonumber(ARGV[3])\n" +
            "local windowStart = now - tonumber(ARGV[2]) * 1000\n" +
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, windowStart)\n" +
            "local count = redis.call('ZCARD', KEYS[1])\n" +
            "if count < tonumber(ARGV[1]) then\n" +
            "  redis.call('ZADD', KEYS[1], now, ARGV[3] .. ':' .. ARGV[4])\n" +
            "  redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]) + 1)\n" +
            "  return 1\n" +
            "else\n" +
            "  return 0\n" +
            "end";
        script = new DefaultRedisScript<>(lua, Long.class);
    }

    /**
     * @param prefix   业务标识（如 "post:create"）
     * @param identity 用户ID或IP（如 "u123" 或 "ip192.168.1.1"）
     * @param max      窗口内最大请求数
     * @param seconds  窗口大小（秒）
     * @return true if allowed, false if rate limited
     */
    public boolean tryAcquire(String prefix, String identity, int max, int seconds) {
        String key = RATE_KEY_PREFIX + prefix + ":" + identity;
        long now = System.currentTimeMillis();
        String uniqueSuffix = now + ":" + Thread.currentThread().getId();
        List<String> keys = Collections.singletonList(key);
        Long result = stringRedisTemplate.execute(script, keys,
                String.valueOf(max), String.valueOf(seconds), String.valueOf(now), uniqueSuffix);
        boolean allowed = result != null && result == 1L;
        if (!allowed) {
            log.warn("Rate limit hit: key={}, max={}/{}, now={}",
                    key, max, seconds, now);
        }
        return allowed;
    }
}
