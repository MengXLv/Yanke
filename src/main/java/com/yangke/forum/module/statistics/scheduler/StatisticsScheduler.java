package com.yangke.forum.module.statistics.scheduler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yangke.forum.module.auth.entity.User;
import com.yangke.forum.module.auth.mapper.UserMapper;
import com.yangke.forum.module.content.entity.Post;
import com.yangke.forum.module.content.mapper.PostMapper;
import com.yangke.forum.module.statistics.entity.Statistics;
import com.yangke.forum.module.statistics.mapper.StatisticsMapper;
import com.yangke.forum.module.statistics.service.StatisticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDate;

@Slf4j
@Component
public class StatisticsScheduler {

    @Resource
    private StatisticsService statisticsService;

    @Resource
    private StatisticsMapper statisticsMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private PostMapper postMapper;

    /** 每日凌晨1点持久化前一日统计数据 */
    @Scheduled(cron = "0 0 1 * * ?")
    public void dailyStatisticsReport() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        // 检查是否已存在
        Statistics existing = statisticsMapper.findByDate(yesterday);
        if (existing != null) {
            log.debug("Statistics for {} already persisted, skipping", yesterday);
            return;
        }

        long uv = statisticsService.getUV(yesterday);
        long dau = statisticsService.getDAU(yesterday);

        // 统计昨日新增
        int newUsers = Math.toIntExact(userMapper.selectCount(
                Wrappers.<User>lambdaQuery()
                        .ge(User::getCreateTime, yesterday.atStartOfDay())
                        .lt(User::getCreateTime, yesterday.plusDays(1).atStartOfDay())));
        int newPosts = Math.toIntExact(postMapper.selectCount(
                Wrappers.<Post>lambdaQuery()
                        .ge(Post::getCreateTime, yesterday.atStartOfDay())
                        .lt(Post::getCreateTime, yesterday.plusDays(1).atStartOfDay())));

        Statistics stats = new Statistics();
        stats.setStatDate(yesterday);
        stats.setUv(uv);
        stats.setDau(dau);
        stats.setNewUsers(newUsers);
        stats.setNewPosts(newPosts);
        statisticsMapper.insert(stats);

        log.info("Daily statistics persisted for {}: UV={}, DAU={}, newUsers={}, newPosts={}",
                yesterday, uv, dau, newUsers, newPosts);
    }
}
