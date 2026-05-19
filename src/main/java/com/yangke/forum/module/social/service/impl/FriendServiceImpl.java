package com.yangke.forum.module.social.service.impl;

import com.yangke.forum.common.PageResult;
import com.yangke.forum.module.auth.dto.UserVO;
import com.yangke.forum.module.auth.entity.User;
import com.yangke.forum.module.auth.mapper.UserMapper;
import com.yangke.forum.module.social.service.FriendService;
import com.yangke.forum.util.RedisKeyUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 好友 = 互相关注（关注 ∩ 粉丝）
 */
@Service
public class FriendServiceImpl implements FriendService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserMapper userMapper;

    @Override
    public List<Long> getFriendIds(Long userId) {
        String followKey = RedisKeyUtil.userFollowKey(userId);
        String fansKey = RedisKeyUtil.userFansKey(userId);
        String tempKey = "temp:friend:" + userId;

        stringRedisTemplate.opsForZSet().intersectAndStore(followKey, fansKey, tempKey);
        Set<String> ids = stringRedisTemplate.opsForZSet().range(tempKey, 0, -1);
        stringRedisTemplate.delete(tempKey);

        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        return ids.stream().map(Long::valueOf).collect(Collectors.toList());
    }

    @Override
    public PageResult<UserVO> getFriends(Long userId, int page, int size) {
        String followKey = RedisKeyUtil.userFollowKey(userId);
        String fansKey = RedisKeyUtil.userFansKey(userId);
        String tempKey = "temp:friend:" + userId;

        // ZSet交集写入临时key
        stringRedisTemplate.opsForZSet().intersectAndStore(followKey, fansKey, tempKey);

        // Redis直接分页，不加载全部到内存
        Long total = stringRedisTemplate.opsForZSet().zCard(tempKey);
        if (total == null || total == 0) {
            stringRedisTemplate.delete(tempKey);
            return new PageResult<>(0L, page, size, Collections.emptyList());
        }

        int start = (page - 1) * size;
        int end = start + size - 1;
        Set<String> pageIds = stringRedisTemplate.opsForZSet().range(tempKey, start, end);
        stringRedisTemplate.delete(tempKey);

        if (pageIds == null || pageIds.isEmpty())
            return new PageResult<>(total, page, size, Collections.emptyList());

        List<Long> uidList = pageIds.stream().map(Long::valueOf).collect(Collectors.toList());
        List<User> users = userMapper.selectBatchIds(uidList);
        // 保持ZSet排序（按关注时间倒序）
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        List<UserVO> records = uidList.stream().map(uid -> {
            UserVO vo = new UserVO();
            User u = userMap.get(uid);
            vo.setId(uid);
            vo.setUsername(u != null ? u.getUsername() : null);
            vo.setAvatar(u != null ? u.getAvatar() : null);
            vo.setBio(u != null ? u.getBio() : null);
            return vo;
        }).collect(Collectors.toList());

        return new PageResult<>(total, page, size, records);
    }

    @Override
    public boolean isFriend(Long userId1, Long userId2) {
        // 直接检查双向关注，避免ZSet交集运算
        String followKey = RedisKeyUtil.userFollowKey(userId1);
        String fansKey = RedisKeyUtil.userFansKey(userId1);
        Double inFollow = stringRedisTemplate.opsForZSet().score(followKey, userId2.toString());
        Double inFans = stringRedisTemplate.opsForZSet().score(fansKey, userId2.toString());
        return inFollow != null && inFans != null;
    }

    @Override
    public long getFriendCount(Long userId) {
        String followKey = RedisKeyUtil.userFollowKey(userId);
        String fansKey = RedisKeyUtil.userFansKey(userId);
        String tempKey = "temp:friend:count:" + userId;

        stringRedisTemplate.opsForZSet().intersectAndStore(followKey, fansKey, tempKey);
        Long count = stringRedisTemplate.opsForZSet().zCard(tempKey);
        stringRedisTemplate.delete(tempKey);
        return count != null ? count : 0;
    }
}
