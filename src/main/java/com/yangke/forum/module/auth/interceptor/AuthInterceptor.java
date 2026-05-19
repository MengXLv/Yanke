package com.yangke.forum.module.auth.interceptor;

import cn.hutool.json.JSONUtil;
import com.yangke.forum.common.Constants;
import com.yangke.forum.common.Result;
import com.yangke.forum.module.auth.dto.UserVO;
import com.yangke.forum.module.auth.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 认证拦截器：游客/登录态动态隔离。
 * 白名单路径在 WebMvcConfig 中配置。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Resource
    private AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // OPTIONS 预检请求放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        } else {
            token = request.getParameter("token");
        }

        if (token != null && !token.isEmpty()) {
            UserVO user = authService.getUserByToken(token);
            if (user != null) {
                request.setAttribute("currentUser", user);
                request.setAttribute("userRole", user.getRole());
                request.setAttribute("userId", user.getId());
                return true;
            }
        }

        // 未登录 → 游客态
        request.setAttribute("currentUser", null);
        request.setAttribute("userRole", Constants.ROLE_GUEST);
        request.setAttribute("userId", null);
        return true;
    }
}
