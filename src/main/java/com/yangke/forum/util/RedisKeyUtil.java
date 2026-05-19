package com.yangke.forum.util;

import com.yangke.forum.common.Constants;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class RedisKeyUtil {

    private RedisKeyUtil() {}

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ==================== 认证相关 ====================
    public static String captchaKey(String uuid) {
        return Constants.REDIS_KEY_CAPTCHA + uuid;
    }

    public static String tokenKey(String token) {
        return Constants.REDIS_KEY_TOKEN + token;
    }

    // ==================== 社交相关 ====================
    public static String postLikeKey(Long postId) {
        return Constants.REDIS_KEY_POST_LIKE + postId;
    }

    public static String postLikeCountKey(Long postId) {
        return Constants.REDIS_KEY_POST_LIKE_COUNT + postId;
    }

    public static String userFollowKey(Long userId) {
        return Constants.REDIS_KEY_USER_FOLLOW + userId;
    }

    public static String userFansKey(Long userId) {
        return Constants.REDIS_KEY_USER_FANS + userId;
    }

    // ==================== 统计相关 ====================
    public static String uvKey(LocalDate date) {
        return Constants.REDIS_KEY_UV + date.format(DATE_FMT);
    }

    public static String uvKey() {
        return Constants.REDIS_KEY_UV + LocalDate.now().format(DATE_FMT);
    }

    public static String dauKey(LocalDate date) {
        return Constants.REDIS_KEY_DAU + date.format(DATE_FMT);
    }

    public static String dauKey() {
        return Constants.REDIS_KEY_DAU + LocalDate.now().format(DATE_FMT);
    }

    // ==================== 热度相关 ====================
    public static String hotPostsKey() {
        return Constants.REDIS_KEY_HOT_POSTS;
    }

    // ==================== 浏览量 ====================
    public static String postViewKey(Long postId) {
        return Constants.REDIS_KEY_POST_VIEW + postId;
    }
}
