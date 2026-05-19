package com.yangke.forum.util;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class MD5Util {

    private MD5Util() {}

    private static final int SALT_LENGTH = 16;
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    /**
     * 生成随机盐值
     */
    public static String generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * MD5 + 盐值 加密
     */
    public static String md5WithSalt(String plainText, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(salt.getBytes());
            byte[] digest = md.digest(plainText.getBytes("UTF-8"));
            return bytesToHex(digest);
        } catch (Exception e) {
            throw new RuntimeException("MD5 encryption error", e);
        }
    }

    /**
     * 验证密码
     */
    public static boolean verify(String plainText, String salt, String encrypted) {
        return md5WithSalt(plainText, salt).equals(encrypted);
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hex[i * 2] = HEX_CHARS[v >>> 4];
            hex[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hex);
    }
}
