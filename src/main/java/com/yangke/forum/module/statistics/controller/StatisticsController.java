package com.yangke.forum.module.statistics.controller;

import com.yangke.forum.common.Result;
import com.yangke.forum.module.statistics.service.StatisticsService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/admin/statistics")
public class StatisticsController {

    @Resource
    private StatisticsService statisticsService;

    @GetMapping("/uv/today")
    public Result<Long> todayUV() {
        return Result.ok(statisticsService.getTodayUV());
    }

    @GetMapping("/dau/today")
    public Result<Long> todayDAU() {
        return Result.ok(statisticsService.getTodayDAU());
    }

    @GetMapping("/uv/trend")
    public Result<?> uvTrend(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(statisticsService.getUVTrend(days));
    }

    @GetMapping("/dau/trend")
    public Result<?> dauTrend(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(statisticsService.getDAUTrend(days));
    }
}
