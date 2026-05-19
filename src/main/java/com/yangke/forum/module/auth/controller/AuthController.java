package com.yangke.forum.module.auth.controller;

import com.yangke.forum.common.IdentifyBy;
import com.yangke.forum.common.RateLimit;
import com.yangke.forum.common.Result;
import com.yangke.forum.module.auth.dto.LoginDTO;
import com.yangke.forum.module.auth.dto.RegisterDTO;
import com.yangke.forum.module.auth.dto.UserVO;
import com.yangke.forum.module.auth.service.AuthService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.IOException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Resource
    private AuthService authService;

    /**
     * 获取验证码图片
     */
    @GetMapping("/captcha")
    public Result<String> captcha(@RequestParam String uuid) throws IOException {
        String base64 = authService.generateCaptcha(uuid);
        return Result.ok(base64);
    }

    /**
     * 用户注册
     */
    @RateLimit(prefix = "auth:register", max = 3, window = 60, by = IdentifyBy.IP, message = "注册太频繁，请稍后再试")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        authService.register(dto);
        return Result.ok();
    }

    /**
     * 邮件激活
     */
    @GetMapping("/activate/{code}")
    public Result<Void> activate(@PathVariable("code") String code) {
        authService.activate(code);
        return Result.ok();
    }

    /**
     * 用户登录
     */
    @RateLimit(prefix = "auth:login", max = 5, window = 60, by = IdentifyBy.IP, message = "登录太频繁，请稍后再试")
    @PostMapping("/login")
    public Result<UserVO> login(@Valid @RequestBody LoginDTO dto) {
        UserVO vo = authService.login(dto);
        return Result.ok(vo);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public Result<UserVO> me(HttpServletRequest request) {
        UserVO user = (UserVO) request.getAttribute("currentUser");
        return Result.ok(user);
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            authService.logout(token);
        }
        return Result.ok();
    }
}
