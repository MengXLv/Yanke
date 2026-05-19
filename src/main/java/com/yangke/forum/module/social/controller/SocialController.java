package com.yangke.forum.module.social.controller;

import com.yangke.forum.common.PageResult;
import com.yangke.forum.common.RequestContext;
import com.yangke.forum.common.Result;
import com.yangke.forum.module.auth.dto.UserVO;
import com.yangke.forum.module.auth.entity.User;
import com.yangke.forum.module.auth.mapper.UserMapper;
import com.yangke.forum.module.social.service.FriendService;
import com.yangke.forum.module.social.service.SocialService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Map;

@RestController
@RequestMapping("/api/social")
public class SocialController {

    @Resource
    private SocialService socialService;

    @Resource
    private FriendService friendService;

    @Resource
    private UserMapper userMapper;

    @PostMapping("/like/{postId}")
    public Result<Map<String, Object>> toggleLike(@PathVariable Long postId,
                                                   HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        boolean liked = socialService.toggleLike(postId, userId);
        long count = socialService.getLikeCount(postId);
        return Result.ok(Map.of("liked", liked, "likeCount", count));
    }

    @GetMapping("/like/{postId}/status")
    public Result<Boolean> isLiked(@PathVariable Long postId, HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        return Result.ok(socialService.isLiked(postId, userId));
    }

    @GetMapping("/like/{postId}/users")
    public Result<List<Map<String, Object>>> likeUsers(@PathVariable Long postId) {
        Set<String> ids = socialService.getLikeUsers(postId);
        if (ids.isEmpty()) return Result.ok(Collections.emptyList());
        List<Long> uidList = ids.stream().map(Long::valueOf).collect(Collectors.toList());
        Map<Long, User> userMap = userMapper.selectBatchIds(uidList).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        List<Map<String, Object>> result = uidList.stream().map(uid -> {
            User user = userMap.get(uid);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("userId", uid);
            entry.put("username", user != null ? user.getUsername() : null);
            entry.put("avatar", user != null ? user.getAvatar() : null);
            return entry;
        }).collect(Collectors.toList());
        return Result.ok(result);
    }

    @PostMapping("/follow/{targetUserId}")
    public Result<Map<String, Object>> toggleFollow(@PathVariable Long targetUserId,
                                                     HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        boolean following = socialService.toggleFollow(targetUserId, userId);
        long fansCount = socialService.getFansCount(targetUserId);
        return Result.ok(Map.of("following", following, "fansCount", fansCount));
    }

    @GetMapping("/follow/{targetUserId}/status")
    public Result<Boolean> isFollowing(@PathVariable Long targetUserId,
                                        HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        return Result.ok(socialService.isFollowing(targetUserId, userId));
    }

    @GetMapping("/stats/{userId}")
    public Result<Map<String, Object>> userStats(@PathVariable Long userId) {
        return Result.ok(Map.of(
                "followCount", socialService.getFollowCount(userId),
                "fansCount", socialService.getFansCount(userId),
                "friendCount", friendService.getFriendCount(userId)
        ));
    }

    // ==================== 好友 ====================

    @GetMapping("/friends")
    public Result<PageResult<UserVO>> friends(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        return Result.ok(friendService.getFriends(userId, page, size));
    }

    @GetMapping("/friend/{targetUserId}/status")
    public Result<Boolean> isFriend(@PathVariable Long targetUserId, HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        return Result.ok(friendService.isFriend(userId, targetUserId));
    }

    /** 关注列表（含用户名） */
    @GetMapping("/following/{userId}")
    public Result<List<Map<String, Object>>> followingList(@PathVariable Long userId) {
        Set<String> ids = socialService.getFollowingIds(userId);
        List<Map<String, Object>> result = buildUserList(ids);
        return Result.ok(result);
    }

    /** 粉丝列表（含用户名） */
    @GetMapping("/fans/{userId}")
    public Result<List<Map<String, Object>>> fansList(@PathVariable Long userId) {
        Set<String> ids = socialService.getFansIds(userId);
        List<Map<String, Object>> result = buildUserList(ids);
        return Result.ok(result);
    }

    private List<Map<String, Object>> buildUserList(Set<String> ids) {
        if (ids.isEmpty()) return Collections.emptyList();
        List<Long> uidList = ids.stream().map(Long::valueOf).collect(Collectors.toList());
        Map<Long, User> userMap = userMapper.selectBatchIds(uidList).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        return uidList.stream().map(uid -> {
            User user = userMap.get(uid);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("userId", uid);
            entry.put("username", user != null ? user.getUsername() : null);
            entry.put("avatar", user != null ? user.getAvatar() : null);
            return entry;
        }).collect(Collectors.toList());
    }
}
