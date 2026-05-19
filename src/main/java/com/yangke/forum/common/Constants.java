package com.yangke.forum.common;

public final class Constants {

    private Constants() {}

    // 用户状态
    public static final int USER_STATUS_INACTIVE = 0;
    public static final int USER_STATUS_ACTIVE = 1;
    public static final int USER_STATUS_BANNED = 2;

    // 角色
    public static final String ROLE_GUEST = "guest";
    public static final String ROLE_USER = "user";
    public static final String ROLE_MODERATOR = "moderator";
    public static final String ROLE_ADMIN = "admin";

    // 帖子状态
    public static final int POST_STATUS_DRAFT = 0;
    public static final int POST_STATUS_PUBLISHED = 1;
    public static final int POST_STATUS_AUDIT = 2;
    public static final int POST_STATUS_BLOCKED = 3;

    // Kafka Topic
    public static final String TOPIC_NOTIFICATION = "forum-notification";
    public static final String TOPIC_POST_EVENT = "forum-post-event";

    // Redis Key 前缀
    public static final String REDIS_KEY_CAPTCHA = "captcha:";
    public static final String REDIS_KEY_TOKEN = "token:";
    public static final String REDIS_KEY_POST_LIKE = "post:like:";
    public static final String REDIS_KEY_POST_LIKE_COUNT = "post:like:count:";
    public static final String REDIS_KEY_USER_FOLLOW = "user:follow:";
    public static final String REDIS_KEY_USER_FANS = "user:fans:";
    public static final String REDIS_KEY_HOT_POSTS = "hot:posts";
    public static final String REDIS_KEY_UV = "uv:";
    public static final String REDIS_KEY_DAU = "dau:";
    public static final String REDIS_KEY_POST_VIEW = "post:view:";

    // 缓存常量
    public static final String CACHE_POST = "post";
    public static final String CACHE_POST_LIST = "post:list";
    public static final String CACHE_USER = "user";
    public static final String CACHE_HOT_POSTS = "hot:posts";
    public static final String CACHE_HOT_POST = "hot:cache:post";
}
