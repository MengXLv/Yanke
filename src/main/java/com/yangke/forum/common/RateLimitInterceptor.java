package com.yangke.forum.common;

import com.yangke.forum.util.IpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;

/**
 * 分布式限流拦截器：检测 @RateLimit 注解 → Redis Lua 滑动窗口判断
 *
 * 面试要点：
 * - 拦截器方式比 AOP 更稳定（不依赖代理创建，直接拦截 DispatcherServlet 分发）
 * - 通过 HandlerMethod 反射获取控制器方法上的 @RateLimit 注解
 * - 滑动窗口：ZSet 记录每笔请求时间戳，ZREMRANGEBYSCORE 清理过期，ZCARD 计数
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    @Resource
    private RateLimiter rateLimiter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Method method = handlerMethod.getMethod();
        RateLimit annotation = method.getAnnotation(RateLimit.class);
        if (annotation == null) {
            return true;
        }

        String identity;
        if (annotation.by() == IdentifyBy.USER) {
            Long userId = (Long) request.getAttribute("userId");
            if (userId == null) {
                sendError(response, 401, "请先登录");
                return false;
            }
            identity = "u" + userId;
        } else {
            identity = "ip" + IpUtil.getClientIp(request);
        }

        if (!rateLimiter.tryAcquire(annotation.prefix(), identity, annotation.max(), annotation.window())) {
            sendError(response, 429, annotation.message());
            return false;
        }

        return true;
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}
