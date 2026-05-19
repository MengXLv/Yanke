package com.yangke.forum.module.content.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yangke.forum.common.BusinessException;
import com.yangke.forum.common.Constants;
import com.yangke.forum.common.PageResult;
import com.yangke.forum.module.auth.entity.User;
import com.yangke.forum.module.auth.mapper.UserMapper;
import com.yangke.forum.module.content.dto.PostDTO;
import com.yangke.forum.module.content.dto.HotCacheData;
import com.yangke.forum.module.content.dto.PostDetailVO;
import com.yangke.forum.module.content.dto.PostVO;
import com.yangke.forum.module.content.entity.Post;
import com.yangke.forum.module.content.mapper.PostMapper;
import com.yangke.forum.module.content.filter.SensitiveWordFilter;
import com.yangke.forum.module.content.service.CategoryService;
import com.yangke.forum.module.content.service.PostService;
import com.yangke.forum.module.search.service.SearchService;
import com.yangke.forum.module.points.service.PointsService;
import com.yangke.forum.util.RedisKeyUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PostServiceImpl implements PostService {

    @Resource
    private PostMapper postMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SearchService searchService;

    @Resource
    private SensitiveWordFilter sensitiveWordFilter;

    @Resource
    private UserMapper userMapper;

    @Resource
    private PointsService pointsService;

    @Value("${forum.cache.redis.post-expire-seconds:1800}")
    private int redisPostExpire;

    @Resource
    private CategoryService categoryService;

    // ==================== Cache-Aside 读策略 ====================

    // Caffeine CacheManager 注入用于手动查本地缓存
    @Resource
    private org.springframework.cache.CacheManager cacheManager;

    @Resource
    private RedissonClient redissonClient;

    @org.springframework.context.annotation.Lazy
    @Resource
    private PostServiceImpl self; // 代理引用，@Async 内部调用需走代理（@Lazy 打破循环依赖）

    private static final String NULL_POST_MARKER = "NULL_POST";
    private static final int NULL_TTL_SECONDS = 60;
    private final Random random = new Random();

    @Override
    @Transactional(readOnly = true)
    public PostDetailVO getPostDetail(Long postId) {
        // 1. Caffeine 本地缓存
        org.springframework.cache.Cache caffeine = cacheManager.getCache("post");
        if (caffeine != null) {
            PostDetailVO fromCaffeine = caffeine.get(postId, PostDetailVO.class);
            if (fromCaffeine != null) {
                if (NULL_POST_MARKER.equals(fromCaffeine.getTitle())) {
                    throw new BusinessException(404, "帖子不存在");
                }
                return fromCaffeine;
            }
        }

        // 2. Redis 分布式缓存
        String cacheKey = Constants.CACHE_POST + ":" + postId;
        PostDetailVO fromRedis = (PostDetailVO) redisTemplate.opsForValue().get(cacheKey);
        if (fromRedis != null) {
            // 穿透防护：缓存了空值标记
            if (NULL_POST_MARKER.equals(fromRedis.getTitle())) {
                throw new BusinessException(404, "帖子不存在");
            }
            if (caffeine != null) caffeine.put(postId, fromRedis);
            return fromRedis;
        }

        // 2.5 热点预热缓存（逻辑过期）：HotPostScheduler 预热的 Top N 热帖
        String hotCacheKey = Constants.CACHE_HOT_POST + ":" + postId;
        HotCacheData hotData = (HotCacheData) redisTemplate.opsForValue().get(hotCacheKey);
        if (hotData != null && hotData.getData() != null) {
            if (hotData.getLogicalExpireTime() > System.currentTimeMillis()) {
                // 未逻辑过期 → 直接返回
                if (caffeine != null) caffeine.put(postId, hotData.getData());
                return hotData.getData();
            }
            // 已逻辑过期：返回旧值，后台异步刷新缓存（不阻塞当前请求）
            self.asyncRefreshHotCache(postId);
            if (caffeine != null) caffeine.put(postId, hotData.getData());
            return hotData.getData();
        }

        // 3. 击穿防护：Redisson 分布式锁（看门狗自动续期，防死锁）
        String lockKey = "lock:post:" + postId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // tryLock(waitTime, leaseTime, unit): 最多等5s，锁10s自动释放
            if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                try {
                    // 双重检查
                    PostDetailVO doubleCheck = (PostDetailVO) redisTemplate.opsForValue().get(cacheKey);
                    if (doubleCheck != null) {
                        if (NULL_POST_MARKER.equals(doubleCheck.getTitle())) {
                            throw new BusinessException(404, "帖子不存在");
                        }
                        if (caffeine != null) caffeine.put(postId, doubleCheck);
                        return doubleCheck;
                    }
                    return rebuildCache(postId, cacheKey, caffeine);
                } finally {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // 未获锁：自旋等待缓存就绪后降级查库
        for (int i = 0; i < 20; i++) {
            try { Thread.sleep(150); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            PostDetailVO retry = (PostDetailVO) redisTemplate.opsForValue().get(cacheKey);
            if (retry != null) {
                if (NULL_POST_MARKER.equals(retry.getTitle())) {
                    throw new BusinessException(404, "帖子不存在");
                }
                if (caffeine != null) caffeine.put(postId, retry);
                return retry;
            }
        }
        return rebuildCache(postId, cacheKey, caffeine);
    }

    private PostDetailVO rebuildCache(Long postId, String cacheKey,
                                       org.springframework.cache.Cache caffeine) {
        Post post = postMapper.selectById(postId);
        if (post == null || post.getDeleted() != 0) {
            // 穿透防护：缓存空值标记，防止恶意刷不存在的ID
            PostDetailVO nullMarker = new PostDetailVO();
            nullMarker.setId(postId);
            nullMarker.setTitle(NULL_POST_MARKER);
            int nullTtl = NULL_TTL_SECONDS + random.nextInt(30); // 60~90s 雪崩抖动
            redisTemplate.opsForValue().set(cacheKey, nullMarker, nullTtl, TimeUnit.SECONDS);
            if (caffeine != null) caffeine.put(postId, nullMarker);
            throw new BusinessException(404, "帖子不存在");
        }
        Map<Long, User> detailUserMap = batchLoadUsers(Collections.singletonList(post.getUserId()));
        PostDetailVO fromDb = toDetailVO(post, detailUserMap);
        // 雪崩防护：TTL 加 ±20% 随机抖动
        int jitter = random.nextInt(redisPostExpire / 5) - redisPostExpire / 10;
        int ttl = Math.max(60, redisPostExpire + jitter);
        redisTemplate.opsForValue().set(cacheKey, fromDb, ttl, TimeUnit.SECONDS);
        if (caffeine != null) caffeine.put(postId, fromDb);
        return fromDb;
    }

    // ==================== Cache-Aside 写策略 ====================

    @Override
    @Transactional
    public Long createPost(PostDTO dto, Long userId) {
        Post post = new Post();
        post.setUserId(userId);
        post.setTitle(dto.getTitle());
        post.setContent(dto.getContent());
        post.setCategoryId(dto.getCategoryId());

        boolean hasSensitive = sensitiveWordFilter.containsSensitive(dto.getTitle())
                || sensitiveWordFilter.containsSensitive(dto.getContent());
        post.setStatus(hasSensitive ? Constants.POST_STATUS_AUDIT : Constants.POST_STATUS_PUBLISHED);

        post.setViewCount(0);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setIsHot(0);
        post.setIsTop(0);
        postMapper.insert(post);

        // 事务外交给异步处理：积分 + ES 索引不阻塞也不回滚主写入
        self.afterCreatePost(post, hasSensitive);
        return post.getId();
    }

    /** 异步：积分奖励 + ES 索引（独立于事务，失败不影响帖子已写入） */
    @Async("esExecutor")
    public void afterCreatePost(Post post, boolean hasSensitive) {
        try { pointsService.awardPost(post.getId()); } catch (Exception ignored) {}
        if (!hasSensitive) searchService.indexPost(post);
    }

    @Override
    @Transactional
    @CacheEvict(value = "post", key = "#postId")
    public void updatePost(Long postId, PostDTO dto, Long userId) {
        Post post = postMapper.selectById(postId);
        if (post == null || !post.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权修改此帖子");
        }
        post.setTitle(dto.getTitle());
        post.setContent(dto.getContent());
        post.setCategoryId(dto.getCategoryId());

        boolean hasSensitive = sensitiveWordFilter.containsSensitive(dto.getTitle())
                || sensitiveWordFilter.containsSensitive(dto.getContent());
        if (hasSensitive) post.setStatus(Constants.POST_STATUS_AUDIT);
        postMapper.updateById(post);

        // 异步：缓存清理 + ES 同步（不阻塞事务提交）
        self.afterUpdatePost(postId, post, hasSensitive);
    }

    @Async("cacheExecutor")
    public void afterUpdatePost(Long postId, Post post, boolean hasSensitive) {
        redisTemplate.delete(Constants.CACHE_POST + ":" + postId);
        redisTemplate.delete(Constants.CACHE_HOT_POST + ":" + postId);
        if (!hasSensitive) searchService.updatePost(post);
    }

    @Override
    @Transactional
    @CacheEvict(value = "post", key = "#postId")
    public void deletePost(Long postId, Long userId) {
        Post post = postMapper.selectById(postId);
        if (post == null || !post.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权删除此帖子");
        }
        postMapper.deleteById(postId);
        self.afterDeletePost(postId);
    }

    @Async("cacheExecutor")
    public void afterDeletePost(Long postId) {
        redisTemplate.delete(Constants.CACHE_POST + ":" + postId);
        redisTemplate.delete(Constants.CACHE_HOT_POST + ":" + postId);
        searchService.deletePost(postId);
    }

    /**
     * 异步刷新热点缓存（逻辑过期后触发）
     *
     * 面试要点：
     * - tryLock(0, ...) = 非阻塞获取锁，拿不到说明已有线程在刷新，直接跳过
     * - 获取锁后二次检查逻辑过期时间，避免重复查询 DB
     * - 同时更新热点缓存（物理永不过期 + 新逻辑过期时间）和普通缓存（带 TTL）
     */
    @Async("cacheExecutor")
    public void asyncRefreshHotCache(Long postId) {
        String lockKey = "lock:hot:post:" + postId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (lock.tryLock(0, 10, TimeUnit.SECONDS)) {
                try {
                    String hotCacheKey = Constants.CACHE_HOT_POST + ":" + postId;
                    HotCacheData existing = (HotCacheData) redisTemplate.opsForValue().get(hotCacheKey);
                    if (existing != null && existing.getLogicalExpireTime() > System.currentTimeMillis()) {
                        return; // 已被其他线程刷新
                    }
                    Post post = postMapper.selectById(postId);
                    if (post == null || post.getDeleted() != 0) return;
                    Map<Long, User> detailUserMap = batchLoadUsers(Collections.singletonList(post.getUserId()));
                    PostDetailVO vo = toDetailVO(post, detailUserMap);
                    // 热点缓存：物理永不过期
                    long newExpire = System.currentTimeMillis() + 10 * 60 * 1000;
                    redisTemplate.opsForValue().set(hotCacheKey, new HotCacheData(vo, newExpire));
                    // 同步更新普通缓存
                    int jitter = random.nextInt(redisPostExpire / 5) - redisPostExpire / 10;
                    int ttl = Math.max(60, redisPostExpire + jitter);
                    redisTemplate.opsForValue().set(Constants.CACHE_POST + ":" + postId, vo, ttl, TimeUnit.SECONDS);
                    // 更新 Caffeine
                    var caffeine = cacheManager.getCache("post");
                    if (caffeine != null) caffeine.put(postId, vo);
                } finally {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== 列表查询 ====================

    @Override
    @Transactional(readOnly = true)
    public PageResult<PostVO> listPosts(int page, int size, Long categoryId, String orderBy) {
        LambdaQueryWrapper<Post> wrapper = Wrappers.<Post>lambdaQuery()
                .eq(Post::getStatus, Constants.POST_STATUS_PUBLISHED)
                .eq(categoryId != null, Post::getCategoryId, categoryId);

        if ("hot".equals(orderBy)) {
            wrapper.orderByDesc(Post::getIsHot, Post::getLikeCount);
        } else {
            wrapper.orderByDesc(Post::getIsTop, Post::getCreateTime);
        }

        IPage<Post> result = postMapper.selectPage(new Page<>(page, size), wrapper);
        Map<Long, User> userMap = batchLoadUsers(
                result.getRecords().stream().map(Post::getUserId).distinct().collect(Collectors.toList()));
        List<PostVO> records = result.getRecords().stream()
                .map(p -> toVO(p, userMap))
                .collect(Collectors.toList());

        return new PageResult<>(result.getTotal(), page, size, records);
    }

    // ==================== 热帖 (Redis ZSet) ====================

    @Override
    public List<PostVO> getHotPosts(int limit) {
        Set<ZSetOperations.TypedTuple<String>> hotSet =
                stringRedisTemplate.opsForZSet()
                        .reverseRangeByScoreWithScores(RedisKeyUtil.hotPostsKey(), 0,
                                Double.MAX_VALUE, 0, limit);

        if (hotSet == null || hotSet.isEmpty()) {
            // 兜底：取最新帖子
            LambdaQueryWrapper<Post> fallback = Wrappers.<Post>lambdaQuery()
                    .eq(Post::getStatus, Constants.POST_STATUS_PUBLISHED)
                    .orderByDesc(Post::getCreateTime);
            List<Post> fallbackPosts = postMapper.selectPage(
                    new Page<>(1, limit), fallback).getRecords();
            Map<Long, User> fbUserMap = batchLoadUsers(
                    fallbackPosts.stream().map(Post::getUserId).distinct().collect(Collectors.toList()));
            return fallbackPosts.stream().map(p -> toVO(p, fbUserMap)).collect(Collectors.toList());
        }

        List<Long> postIds = new ArrayList<>();
        Map<Long, Double> scoreMap = new HashMap<>();
        for (ZSetOperations.TypedTuple<String> tuple : hotSet) {
            Long postId = Long.valueOf(tuple.getValue());
            postIds.add(postId);
            scoreMap.put(postId, tuple.getScore());
        }

        List<Post> posts = postMapper.selectBatchIds(postIds);
        Map<Long, User> hotUserMap = batchLoadUsers(
                posts.stream().map(Post::getUserId).distinct().collect(Collectors.toList()));
        return posts.stream()
                .map(p -> {
                    PostVO vo = toVO(p, hotUserMap);
                    vo.setIsHot(1);
                    return vo;
                })
                .sorted((a, b) -> Double.compare(
                        scoreMap.getOrDefault(b.getId(), 0.0),
                        scoreMap.getOrDefault(a.getId(), 0.0)))
                .collect(Collectors.toList());
    }

    // ==================== 搜索 (ES) ====================

    @Override
    public PageResult<PostVO> search(String keyword, int page, int size) {
        return searchService.searchPosts(keyword, page, size);
    }

    // ==================== 浏览量 ====================

    @Override
    public void recordView(Long postId, String ip) {
        String viewKey = RedisKeyUtil.postViewKey(postId);
        // Set去重单用户重复浏览
        Long added = stringRedisTemplate.opsForSet().add(viewKey, ip);
        if (added != null && added > 0) {
            // 首次浏览，异步更新DB
            postMapper.incrementViewCount(postId);
            stringRedisTemplate.expire(viewKey, 1, TimeUnit.HOURS);
        }
    }

    // ==================== 草稿箱 ====================

    @Override
    public Long saveDraft(PostDTO dto, Long userId, Long draftId) {
        Post post;
        if (draftId != null) {
            post = postMapper.selectById(draftId);
            if (post == null || !post.getUserId().equals(userId)) {
                throw new BusinessException(403, "无权编辑此草稿");
            }
        } else {
            post = new Post();
            post.setUserId(userId);
            post.setViewCount(0);
            post.setLikeCount(0);
            post.setCommentCount(0);
            post.setIsHot(0);
            post.setIsTop(0);
        }
        post.setTitle(dto.getTitle());
        post.setContent(dto.getContent());
        post.setCategoryId(dto.getCategoryId());
        post.setStatus(Constants.POST_STATUS_DRAFT);
        if (draftId != null) {
            postMapper.updateById(post);
        } else {
            postMapper.insert(post);
        try { pointsService.awardPost(post.getId()); } catch (Exception ignored) {}
        }
        return post.getId();
    }

    @Override
    public PageResult<PostVO> listDrafts(Long userId, int page, int size) {
        LambdaQueryWrapper<Post> wrapper = Wrappers.<Post>lambdaQuery()
                .eq(Post::getUserId, userId)
                .eq(Post::getStatus, Constants.POST_STATUS_DRAFT)
                .orderByDesc(Post::getUpdateTime);
        IPage<Post> result = postMapper.selectPage(new Page<>(page, size), wrapper);
        Map<Long, User> draftUserMap = batchLoadUsers(
                result.getRecords().stream().map(Post::getUserId).distinct().collect(Collectors.toList()));
        List<PostVO> records = result.getRecords().stream()
                .map(p -> toVO(p, draftUserMap)).collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), page, size, records);
    }

    @Override
    public void publishDraft(Long draftId, Long userId) {
        Post post = postMapper.selectById(draftId);
        if (post == null || !post.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作此草稿");
        }
        // 敏感词检测
        boolean hasSensitive = sensitiveWordFilter.containsSensitive(post.getTitle())
                || sensitiveWordFilter.containsSensitive(post.getContent());
        post.setStatus(hasSensitive ? Constants.POST_STATUS_AUDIT : Constants.POST_STATUS_PUBLISHED);
        postMapper.updateById(post);
        if (!hasSensitive) {
            searchService.indexPost(post);
        }
    }

    // ==================== 转换方法 ====================

    private PostVO toVO(Post post, Map<Long, User> userMap) {
        PostVO vo = new PostVO();
        vo.setId(post.getId());
        vo.setUserId(post.getUserId());
        vo.setTitle(post.getTitle());
        vo.setSummary(post.getContent() != null && post.getContent().length() > 200
                ? post.getContent().substring(0, 200) + "..." : post.getContent());
        vo.setViewCount(post.getViewCount());
        vo.setLikeCount(post.getLikeCount());
        vo.setCommentCount(post.getCommentCount());
        vo.setIsHot(post.getIsHot());
        vo.setIsTop(post.getIsTop());
        vo.setCreateTime(post.getCreateTime());
        User author = userMap.get(post.getUserId());
        vo.setUsername(author != null ? author.getUsername() : null);
        vo.setUserAvatar(author != null ? author.getAvatar() : null);
        return vo;
    }

    private PostDetailVO toDetailVO(Post post, Map<Long, User> userMap) {
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
        return vo;
    }

    private Map<Long, User> batchLoadUsers(List<Long> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
    }
}
