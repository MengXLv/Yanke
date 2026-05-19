package com.yangke.forum.module.social.service;

public interface SocialService {

    /**
     * 点赞/取消点赞帖子
     * @return true-点赞 false-取消点赞
     */
    boolean toggleLike(Long postId, Long userId);

    /**
     * 是否已点赞
     */
    boolean isLiked(Long postId, Long userId);

    /**
     * 获取点赞数
     */
    long getLikeCount(Long postId);

    /**
     * 获取点赞用户ID列表
     */
    java.util.Set<String> getLikeUsers(Long postId);

    /**
     * 关注/取消关注用户
     * @return true-关注 false-取消关注
     */
    boolean toggleFollow(Long targetUserId, Long userId);

    /**
     * 是否已关注
     */
    boolean isFollowing(Long targetUserId, Long userId);

    /**
     * 获取关注数
     */
    long getFollowCount(Long userId);

    /**
     * 获取粉丝数
     */
    long getFansCount(Long userId);

    /**
     * 关注用户ID列表
     */
    java.util.Set<String> getFollowingIds(Long userId);

    /**
     * 粉丝用户ID列表
     */
    java.util.Set<String> getFansIds(Long userId);

    /**
     * 获取共同关注列表（交集）
     */
    long getCommonFollowCount(Long userId1, Long userId2);
}
