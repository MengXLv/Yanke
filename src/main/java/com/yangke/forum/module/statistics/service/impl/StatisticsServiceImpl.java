package com.yangke.forum.module.statistics.service.impl;

import com.yangke.forum.module.statistics.service.StatisticsService;
import com.yangke.forum.util.RedisKeyUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 轻量级数据统计服务
 *
 * UV: Redis HyperLogLog  — 12KB内存可统计百万级独立用户
 * DAU: Redis Bitmap      — 按用户ID的bit位标记活跃状态
 */
@Service
public class StatisticsServiceImpl implements StatisticsService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ==================== UV (HyperLogLog) ====================

    @Override
    public void recordUV(Long userId, String ip) {
        // 使用IP作为去重依据（也可用 userId + ip 组合）
        stringRedisTemplate.opsForHyperLogLog()
                .add(RedisKeyUtil.uvKey(), ip);
    }

    @Override
    public long getTodayUV() {
        return getUV(LocalDate.now());
    }

    @Override
    public long getUV(LocalDate date) {
        Long count = stringRedisTemplate.opsForHyperLogLog()
                .size(RedisKeyUtil.uvKey(date));
        return count != null ? count : 0;
    }

    @Override
    public Map<String, Long> getUVTrend(int days) {
        Map<String, Long> trend = new LinkedHashMap<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            trend.put(date.format(DATE_FMT), getUV(date));
        }
        return trend;
    }

    // ==================== DAU (Bitmap) ====================

    @Override
    public void recordDAU(Long userId) {
        // Bitmap: 将 userId 作为 offset 位设置为1
        stringRedisTemplate.opsForValue()
                .setBit(RedisKeyUtil.dauKey(), userId, true);
    }

    @Override
    public long getTodayDAU() {
        return getDAU(LocalDate.now());
    }

    @Override
    public long getDAU(LocalDate date) {
        return retrieveBitCount(RedisKeyUtil.dauKey(date));
    }

    private long retrieveBitCount(String key) {
        Long count = null;
        try {
            count = (Long) stringRedisTemplate.execute(
                    (org.springframework.data.redis.connection.RedisConnection connection) ->
                            connection.bitCount(key.getBytes()),
                    true
            );
        } catch (Exception e) {
            // fallback
        }
        return count != null ? count : 0;
    }

    @Override
    public Map<String, Long> getDAUTrend(int days) {
        Map<String, Long> trend = new LinkedHashMap<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            trend.put(date.format(DATE_FMT), getDAU(date));
        }
        return trend;
    }
}
