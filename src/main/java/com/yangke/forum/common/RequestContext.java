package com.yangke.forum.common;

import javax.servlet.http.HttpServletRequest;

public final class RequestContext {

    private RequestContext() {}

    /**
     * 从请求中提取 userId，未登录（游客）时抛出异常
     */
    public static Long requireLogin(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return userId;
    }

    /**
     * 允许游客访问，可能返回 null
     */
    public static Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }
}
