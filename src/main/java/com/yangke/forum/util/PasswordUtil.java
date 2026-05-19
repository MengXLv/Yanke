package com.yangke.forum.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码工具：BCrypt 主方案，兼容旧 MD5+salt 的平滑迁移
 */
public class PasswordUtil {

    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** 注册时加密 */
    public static String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    /** 登录验证（自动兼容BCrypt和旧MD5+salt） */
    public static boolean matches(String rawPassword, String salt, String encodedPassword) {
        // BCrypt 的哈希以 $2a$ 开头
        if (encodedPassword.startsWith("$2a$")) {
            return encoder.matches(rawPassword, encodedPassword);
        }
        // 旧MD5+salt兼容
        if (salt != null && !salt.isEmpty()) {
            return MD5Util.verify(rawPassword, salt, encodedPassword);
        }
        return false;
    }

    /** 是否为旧MD5格式，需要迁移到BCrypt */
    public static boolean isLegacy(String encodedPassword) {
        return !encodedPassword.startsWith("$2a$");
    }
}
