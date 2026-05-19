package com.yangke.forum.module.social.service.impl;

import com.yangke.forum.common.BusinessException;
import com.yangke.forum.module.content.entity.Post;
import com.yangke.forum.module.content.mapper.PostMapper;
import com.yangke.forum.module.notification.entity.Notification;
import com.yangke.forum.module.notification.service.NotificationService;
import com.yangke.forum.module.points.service.PointsService;
import com.yangke.forum.module.social.service.SocialService;
import com.yangke.forum.util.RedisKeyUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Set;

/**
 * 基于 Redis Set/ZSet 实现社交关系维护
 *
 * Set:  点赞用户集合  → post:like:{postId}
 * ZSet: 关注列表      → user:follow:{userId}   (score=关注时间戳)
 * ZSet: 粉丝列表      → user:fans:{userId}     (score=关注时间戳)
 */
@Service
public class SocialServiceImpl implements SocialService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private PostMapper postMapper;

    @Resource
    private org.springframework.cache.CacheManager cacheManager;

    @Resource
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    @Resource
    private PointsService pointsService;

    @Resource
    private NotificationService notificationService;

    // ==================== 点赞 (Set) ====================

    @Override
    public boolean toggleLike(Long postId, Long userId) {
        String likeKey = RedisKeyUtil.postLikeKey(postId);
        String countKey = RedisKeyUtil.postLikeCountKey(postId);

        boolean result;
        Boolean alreadyLiked = stringRedisTemplate.opsForSet().isMember(likeKey, userId.toString());
        if (Boolean.TRUE.equals(alreadyLiked)) {
            stringRedisTemplate.opsForSet().remove(likeKey, userId.toString());
            stringRedisTemplate.opsForValue().decrement(countKey);
            postMapper.updateLikeCount(postId, -1);
            result = false;
        } else {
            stringRedisTemplate.opsForSet().add(likeKey, userId.toString());
            stringRedisTemplate.opsForValue().increment(countKey);
            postMapper.updateLikeCount(postId, 1);
            try { pointsService.awardLike(userId); } catch (Exception ignored) {}

            // 通知帖主（非本人点赞时）
            Post post = postMapper.selectById(postId);
            if (post != null && !post.getUserId().equals(userId)) {
                Notification notification = new Notification();
                notification.setSenderId(userId);
                notification.setReceiverId(post.getUserId());
                notification.setTargetId(postId);
                notification.setType(1); // 点赞
                notification.setContent("赞了你的帖子《" + post.getTitle() + "》");
                notificationService.sendAsync(notification);
            }
            result = true;
        }
        // 清除帖子缓存
        var caffeine = cacheManager.getCache("post");
        if (caffeine != null) caffeine.evict(postId);
        redisTemplate.delete("post:" + postId);
        return result;
    }

    @Override
    public boolean isLiked(Long postId, Long userId) {
        return Boolean.TRUE.equals(
                stringRedisTemplate.opsForSet()
                        .isMember(RedisKeyUtil.postLikeKey(postId), userId.toString()));
    }

    @Override
    public long getLikeCount(Long postId) {
        Long size = stringRedisTemplate.opsForSet().size(RedisKeyUtil.postLikeKey(postId));
        return size == null ? 0 : size;
    }

    @Override
    public Set<String> getLikeUsers(Long postId) {
        Set<String> users = stringRedisTemplate.opsForSet()
                .members(RedisKeyUtil.postLikeKey(postId));
        return users != null ? users : Collections.emptySet();
    }

    // ==================== 关注 (ZSet) ====================

    @Override
    public boolean toggleFollow(Long targetUserId, Long userId) {
        if (targetUserId.equals(userId)) {
            throw new BusinessException(400, "不能关注自己");
        }

        String followKey = RedisKeyUtil.userFollowKey(userId);
        String fansKey = RedisKeyUtil.userFansKey(targetUserId);
        long now = LocalDateTime.now().toEpochSecond(ZoneOffset.ofHours(8));

        Double score = stringRedisTemplate.opsForZSet().score(followKey, targetUserId.toString());
        if (score != null) {
            // 取消关注
            stringRedisTemplate.opsForZSet().remove(followKey, targetUserId.toString());
            stringRedisTemplate.opsForZSet().remove(fansKey, userId.toString());
            return false;
        } else {
            // 关注
            stringRedisTemplate.opsForZSet().add(followKey, targetUserId.toString(), now);
            stringRedisTemplate.opsForZSet().add(fansKey, userId.toString(), now);

            // 通知被关注者
            Notification notification = new Notification();
            notification.setSenderId(userId);
            notification.setReceiverId(targetUserId);
            notification.setTargetId(targetUserId);
            notification.setType(3); // 关注
            notification.setContent("关注了你");
            notificationService.sendAsync(notification);

            return true;
        }
    }

    @Override
    public boolean isFollowing(Long targetUserId, Long userId) {
        Double score = stringRedisTemplate.opsForZSet()
                .score(RedisKeyUtil.userFollowKey(userId), targetUserId.toString());
        return score != null;
    }

    @Override
    public long getFollowCount(Long userId) {
        Long size = stringRedisTemplate.opsForZSet().size(RedisKeyUtil.userFollowKey(userId));
        return size == null ? 0 : size;
    }

    @Override
    public long getFansCount(Long userId) {
        Long size = stringRedisTemplate.opsForZSet().size(RedisKeyUtil.userFansKey(userId));
        return size == null ? 0 : size;
    }

    @Override
    public Set<String> getFollowingIds(Long userId) {
        Set<String> ids = stringRedisTemplate.opsForZSet()
                .range(RedisKeyUtil.userFollowKey(userId), 0, -1);
        return ids != null ? ids : Collections.emptySet();
    }

    @Override
    public Set<String> getFansIds(Long userId) {
        Set<String> ids = stringRedisTemplate.opsForZSet()
                .range(RedisKeyUtil.userFansKey(userId), 0, -1);
        return ids != null ? ids : Collections.emptySet();
    }

    @Override
    public long getCommonFollowCount(Long userId1, Long userId2) {
        String key1 = RedisKeyUtil.userFollowKey(userId1);
        String key2 = RedisKeyUtil.userFollowKey(userId2);
        String tempKey = "temp:common_follow:" + userId1 + ":" + userId2;

        stringRedisTemplate.opsForZSet().intersectAndStore(key1, key2, tempKey);
        Long count = stringRedisTemplate.opsForZSet().size(tempKey);
        stringRedisTemplate.delete(tempKey);

        return count == null ? 0 : count;
    }
}
