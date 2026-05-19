package com.yangke.forum.config;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.NetworkInterface;

/**
 * 分布式雪花算法 ID 生成器（64位）
 *
 * 结构：1位保留 | 41位毫秒时间戳（69年）| 10位工作机器 | 12位序列号
 *
 * 面试要点：
 * - 全局唯一且趋势递增，适合 MySQL InnoDB 聚簇索引
 * - 不依赖外部服务，比 UUID 更省空间，比自增 ID 更适合分库分表
 * - 时钟回拨：用 lastTimestamp 检测，回拨 < 5ms 则等待，否则抛异常
 */
@Component
public class SnowflakeIdGenerator {

    /** 起始时间戳 2026-01-01 00:00:00 */
    private static final long START_EPOCH = 1735689600000L;

    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;  // 1023
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;     // 4095

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    private final long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator() {
        this.workerId = generateWorkerId();
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();

        // 时钟回拨检测
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset <= 5) {
                // 回拨 < 5ms，等待追上
                try { Thread.sleep(offset + 1); } catch (InterruptedException ignored) {}
                timestamp = System.currentTimeMillis();
            }
            if (timestamp < lastTimestamp) {
                throw new IllegalStateException(
                    "时钟回拨 " + (lastTimestamp - timestamp) + "ms，拒绝生成 ID");
            }
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // 当前毫秒序列号用完，等下一毫秒
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;
        return ((timestamp - START_EPOCH) << TIMESTAMP_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long tilNextMillis(long lastTimestamp) {
        long now = System.currentTimeMillis();
        while (now <= lastTimestamp) now = System.currentTimeMillis();
        return now;
    }

    /** 基于 MAC 地址后两字节生成机器 ID */
    private long generateWorkerId() {
        try {
            InetAddress ip = InetAddress.getLocalHost();
            NetworkInterface ni = NetworkInterface.getByInetAddress(ip);
            if (ni != null) {
                byte[] mac = ni.getHardwareAddress();
                if (mac != null) {
                    return ((mac[mac.length - 2] & 0xFF) << 8 | (mac[mac.length - 1] & 0xFF)) & MAX_WORKER_ID;
                }
            }
        } catch (Exception ignored) {}
        return Thread.currentThread().getId() & MAX_WORKER_ID;
    }
}
