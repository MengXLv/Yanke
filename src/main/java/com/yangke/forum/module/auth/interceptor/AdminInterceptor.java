package com.yangke.forum.module.auth.interceptor;

import cn.hutool.json.JSONUtil;
import com.yangke.forum.common.Constants;
import com.yangke.forum.common.Result;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 管理员权限拦截器：拦截 /api/admin/**，仅 admin/mod 角色允许访问。
 */
@Component
public class AdminInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String role = (String) request.getAttribute("userRole");

        if (Constants.ROLE_ADMIN.equals(role) || Constants.ROLE_MODERATOR.equals(role)) {
            return true;
        }

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSONUtil.toJsonStr(Result.fail(403, "无权限访问")));
        return false;
    }
}
