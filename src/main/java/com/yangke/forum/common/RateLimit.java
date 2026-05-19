package com.yangke.forum.common;

import java.lang.annotation.*;

/**
 * 分布式限流注解
 *
 * 面试要点：声明式限流，AOP 自动解析用户ID/IP，无需在每个 Controller 里手写 tryAcquire
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /** Redis key 前缀，如 "post:create" */
    String prefix();

    /** 窗口内最大请求数 */
    int max();

    /** 窗口大小（秒） */
    int window() default 60;

    /** 限流维度：按用户ID 还是按客户端IP */
    IdentifyBy by() default IdentifyBy.USER;

    /** 触发限流时返回的提示信息 */
    String message() default "操作太频繁，请稍后再试";
}
