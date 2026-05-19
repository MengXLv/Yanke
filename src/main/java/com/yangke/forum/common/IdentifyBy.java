package com.yangke.forum.common;

/**
 * 限流维度枚举
 */
public enum IdentifyBy {
    /** 按登录用户ID限流 */
    USER,
    /** 按客户端IP限流 */
    IP
}
