package com.yangke.forum.module.auth.service;

import com.yangke.forum.module.auth.dto.LoginDTO;
import com.yangke.forum.module.auth.dto.RegisterDTO;
import com.yangke.forum.module.auth.dto.UserVO;

import java.io.IOException;

public interface AuthService {

    /**
     * 生成验证码图片，返回Base64
     */
    String generateCaptcha(String uuid) throws IOException;

    /**
     * 用户注册 → 发送激活邮件
     */
    void register(RegisterDTO dto);

    /**
     * 邮件激活账户
     */
    void activate(String activationCode);

    /**
     * 用户登录，返回Token
     */
    UserVO login(LoginDTO dto);

    /**
     * 根据Token获取用户
     */
    UserVO getUserByToken(String token);

    /**
     * 退出登录
     */
    void logout(String token);
}
