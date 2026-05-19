package com.yangke.forum.module.statistics.service;

import java.time.LocalDate;
import java.util.Map;

public interface StatisticsService {

    /**
     * 记录UV (HyperLogLog)
     */
    void recordUV(Long userId, String ip);

    /**
     * 记录DAU (Bitmap)
     */
    void recordDAU(Long userId);

    /**
     * 获取今日UV
     */
    long getTodayUV();

    /**
     * 获取指定日期UV
     */
    long getUV(LocalDate date);

    /**
     * 获取今日DAU
     */
    long getTodayDAU();

    /**
     * 获取指定日期DAU
     */
    long getDAU(LocalDate date);

    /**
     * 获取近N天UV/DAU趋势
     */
    Map<String, Long> getUVTrend(int days);

    Map<String, Long> getDAUTrend(int days);
}
