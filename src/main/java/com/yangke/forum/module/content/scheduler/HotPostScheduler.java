package com.yangke.forum.module.content.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yangke.forum.common.Constants;
import com.yangke.forum.module.auth.entity.User;
import com.yangke.forum.module.auth.mapper.UserMapper;
import com.yangke.forum.module.content.dto.HotCacheData;
import com.yangke.forum.module.content.dto.PostDetailVO;
import com.yangke.forum.module.content.entity.Post;
import com.yangke.forum.module.content.mapper.PostMapper;
import com.yangke.forum.module.content.service.CategoryService;
import com.yangke.forum.util.RedisKeyUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 定时刷新热帖 ZSet：每 N 分钟从数据库拉取高赞帖子，按热度公式更新分数
 *
 * 热度公式：score = likeCount * 2 + commentCount * 1 + viewCount * 0.1
 */
@Slf4j
@Component
public class HotPostScheduler {

    @Resource
    private PostMapper postMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private UserMapper userMapper;

    @Resource
    private CategoryService categoryService;

    @Value("${forum.hot.threshold:50}")
    private int hotThreshold;

    @Value("${forum.hot.fallback-count:10}")
    private int fallbackCount;

    private static final int PRELOAD_COUNT = 10;
    private final Random random = new Random();

    @Scheduled(fixedDelayString = "${forum.hot.refresh-ms:300000}", initialDelay = 30000)
    public void refreshHotPosts() {
        // 查询点赞数 >= 阈值的帖子
        LambdaQueryWrapper<Post> wrapper = Wrappers.<Post>lambdaQuery()
                .ge(Post::getLikeCount, hotThreshold)
                .eq(Post::getStatus, Constants.POST_STATUS_PUBLISHED)
                .orderByDesc(Post::getLikeCount);
        List<Post> posts = postMapper.selectPage(
                new Page<>(1, 200), wrapper).getRecords();

        // 不足时：用最新高互动帖子兜底
        if (posts.size() < fallbackCount) {
            LambdaQueryWrapper<Post> fallback = Wrappers.<Post>lambdaQuery()
                    .eq(Post::getStatus, Constants.POST_STATUS_PUBLISHED)
                    .gt(Post::getCommentCount, 0)
                    .orderByDesc(Post::getCreateTime);
            posts = postMapper.selectPage(
                    new Page<>(1, fallbackCount), fallback).getRecords();
        }

        if (posts.isEmpty()) return;

        String hotKey = RedisKeyUtil.hotPostsKey();

        // 清空旧数据
        Set<String> oldMembers = stringRedisTemplate.opsForZSet().range(hotKey, 0, -1);
        if (oldMembers != null && !oldMembers.isEmpty()) {
            stringRedisTemplate.opsForZSet().remove(hotKey, oldMembers.toArray(new String[0]));
        }

        // 写入新热帖排行
        for (Post post : posts) {
            double score = post.getLikeCount() * 2.0
                    + post.getCommentCount() * 1.0
                    + post.getViewCount() * 0.1;
            stringRedisTemplate.opsForZSet().add(hotKey, post.getId().toString(), score);
        }

        log.debug("Hot posts refreshed: {} posts updated", posts.size());

        // 热点预热：将 Top N 热帖提前加载到 Redis（逻辑过期策略）
        int preloadLimit = Math.min(PRELOAD_COUNT, posts.size());
        List<Post> topPosts = posts.subList(0, preloadLimit);
        Set<Long> userIds = topPosts.stream().map(Post::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap;
        if (!userIds.isEmpty()) {
            userMap = userMapper.selectBatchIds(new ArrayList<>(userIds))
                    .stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        } else {
            userMap = Collections.emptyMap();
        }

        int preloaded = 0;
        long logicalExpire = System.currentTimeMillis() + 10 * 60 * 1000; // 10 分钟后逻辑过期
        for (Post post : topPosts) {
            String hotCacheKey = Constants.CACHE_HOT_POST + ":" + post.getId();
            PostDetailVO vo = new PostDetailVO();
            vo.setId(post.getId());
            vo.setUserId(post.getUserId());
            vo.setTitle(post.getTitle());
            vo.setContent(post.getContent());
            vo.setCategoryId(post.getCategoryId());
            vo.setViewCount(post.getViewCount());
            vo.setLikeCount(post.getLikeCount());
            vo.setCommentCount(post.getCommentCount());
            vo.setIsHot(post.getIsHot());
            vo.setIsTop(post.getIsTop());
            vo.setCreateTime(post.getCreateTime());
            vo.setUpdateTime(post.getUpdateTime());
            User author = userMap.get(post.getUserId());
            vo.setUsername(author != null ? author.getUsername() : null);
            vo.setUserAvatar(author != null ? author.getAvatar() : null);
            vo.setCategoryName(categoryService.getCategoryName(post.getCategoryId()));

            // 物理永不过期，仅靠 logicalExpireTime 判断是否需异步刷新
            redisTemplate.opsForValue().set(hotCacheKey, new HotCacheData(vo, logicalExpire));
            preloaded++;
        }
        if (preloaded > 0) {
            log.info("Hot post cache preloaded: {} posts (logical expire +10min)", preloaded);
        }
    }
}
