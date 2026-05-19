package com.yangke.forum.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordUtilTest {

    @Test
    void bcryptEncodeAndVerify() {
        String encoded = PasswordUtil.encode("123456");
        assertTrue(encoded.startsWith("$2a$"));
        assertFalse(PasswordUtil.isLegacy(encoded), "BCrypt hash is not legacy");
        assertTrue(PasswordUtil.matches("123456", null, encoded));
        assertFalse(PasswordUtil.matches("wrong", null, encoded));
    }

    @Test
    void legacyMd5Verify() {
        String salt = MD5Util.generateSalt();
        String encoded = MD5Util.md5WithSalt("hello", salt);
        assertFalse(encoded.startsWith("$2a$"));
        assertTrue(PasswordUtil.matches("hello", salt, encoded));
        assertFalse(PasswordUtil.matches("wrong", salt, encoded));
    }

    @Test
    void differentPasswordsProduceDifferentHashes() {
        String h1 = PasswordUtil.encode("123456");
        String h2 = PasswordUtil.encode("123456");
        assertNotEquals(h1, h2, "BCrypt should salt each hash uniquely");
    }
}
