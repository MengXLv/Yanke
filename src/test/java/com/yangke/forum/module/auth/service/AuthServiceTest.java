package com.yangke.forum.module.auth.service;

import com.yangke.forum.BaseTest;
import com.yangke.forum.common.BusinessException;
import com.yangke.forum.module.auth.dto.LoginDTO;
import com.yangke.forum.module.auth.dto.RegisterDTO;
import com.yangke.forum.module.auth.dto.UserVO;
import com.yangke.forum.module.auth.entity.User;
import com.yangke.forum.module.auth.mapper.UserMapper;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
class AuthServiceTest extends BaseTest {

    @Resource private AuthService authService;
    @Resource private UserMapper userMapper;
    @Resource private StringRedisTemplate stringRedisTemplate;

    private String captchaUuid;

    @BeforeEach
    void setUpCaptcha() throws Exception {
        captchaUuid = "test-" + System.nanoTime();
        authService.generateCaptcha(captchaUuid);
    }

    @Test
    void registerSuccess() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("newuser");
        dto.setEmail("new@test.com");
        dto.setPassword("123456");
        dto.setCaptcha(getCode());
        dto.setCaptchaKey(captchaUuid);

        authService.register(dto);
        User user = userMapper.selectByAccount("newuser");
        assertNotNull(user);
        assertTrue(user.getPassword().startsWith("$2a$"), "Should use BCrypt");
    }

    @Test
    void registerDuplicateUsername() {
        registerUser("dup", "dup1@test.com");

        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("dup");
        dto.setEmail("dup2@test.com");
        dto.setPassword("123456");
        dto.setCaptcha(getCode());
        dto.setCaptchaKey(captchaUuid);

        assertThrows(BusinessException.class, () -> authService.register(dto));
    }

    @Test
    void loginWithCorrectPassword() {
        registerUser("logintest", "login@test.com");

        String newUuid = "login-" + System.nanoTime();
        try { authService.generateCaptcha(newUuid); } catch (Exception ignored) {}

        LoginDTO dto = new LoginDTO();
        dto.setAccount("logintest");
        dto.setPassword("123456");
        dto.setCaptcha(getCodeFor(newUuid));
        dto.setCaptchaKey(newUuid);

        UserVO vo = authService.login(dto);
        assertNotNull(vo);
        assertEquals("logintest", vo.getUsername());
        assertNotNull(vo.getToken());
    }

    @Test
    void loginWithWrongPassword() {
        registerUser("wrongpw", "wrong@test.com");

        String uuid = "wrong-" + System.nanoTime();
        try { authService.generateCaptcha(uuid); } catch (Exception ignored) {}

        LoginDTO dto = new LoginDTO();
        dto.setAccount("wrongpw");
        dto.setPassword("badpassword");
        dto.setCaptcha(getCodeFor(uuid));
        dto.setCaptchaKey(uuid);

        assertThrows(BusinessException.class, () -> authService.login(dto));
    }

    @Test
    void logoutInvalidatesToken() {
        registerUser("logouttest", "logout@test.com");
        String uuid = "logout-" + System.nanoTime();
        try { authService.generateCaptcha(uuid); } catch (Exception ignored) {}

        LoginDTO dto = new LoginDTO();
        dto.setAccount("logouttest");
        dto.setPassword("123456");
        dto.setCaptcha(getCodeFor(uuid));
        dto.setCaptchaKey(uuid);
        UserVO vo = authService.login(dto);

        authService.logout(vo.getToken());
        UserVO afterLogout = authService.getUserByToken(vo.getToken());
        assertNull(afterLogout);
    }

    private void registerUser(String username, String email) {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername(username);
        dto.setEmail(email);
        dto.setPassword("123456");
        dto.setCaptcha(getCode());
        dto.setCaptchaKey(captchaUuid);
        authService.register(dto);
    }

    private String getCode() {
        return stringRedisTemplate.opsForValue().get("captcha:" + captchaUuid);
    }

    private String getCodeFor(String uuid) {
        return stringRedisTemplate.opsForValue().get("captcha:" + uuid);
    }
}
