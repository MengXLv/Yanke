package com.yangke.forum.module.auth.service.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.yangke.forum.common.BusinessException;
import com.yangke.forum.common.Constants;
import com.yangke.forum.module.auth.dto.LoginDTO;
import com.yangke.forum.module.auth.dto.RegisterDTO;
import com.yangke.forum.module.auth.dto.UserVO;
import com.yangke.forum.module.auth.entity.User;
import com.yangke.forum.module.auth.mapper.UserMapper;
import com.yangke.forum.module.auth.service.AuthService;
import com.yangke.forum.module.notification.service.NotificationService;
import com.yangke.forum.util.PasswordUtil;
import com.yangke.forum.util.RedisKeyUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Service
public class AuthServiceImpl implements AuthService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private JavaMailSender mailSender;

    @Resource
    private NotificationService notificationService;

    @Resource
    private com.yangke.forum.module.points.service.PointsService pointsService;

    @Value("${forum.captcha.expire-seconds:120}")
    private int captchaExpireSeconds;

    @Value("${forum.activation.expire-hours:24}")
    private int activationExpireHours;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${forum.dev-mode:true}")
    private boolean devMode;

    @Override
    public String generateCaptcha(String uuid) throws IOException {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(120, 40, 4, 20);
        String code = captcha.getCode();

        // 验证码存入Redis，有效期2分钟
        stringRedisTemplate.opsForValue().set(
                RedisKeyUtil.captchaKey(uuid), code,
                captchaExpireSeconds, TimeUnit.SECONDS);

        // 返回Base64编码的图片
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            captcha.write(baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        }
    }

    @Override
    @Transactional
    public void register(RegisterDTO dto) {
        // 1. 校验验证码
        String cachedCaptcha = stringRedisTemplate.opsForValue()
                .get(RedisKeyUtil.captchaKey(dto.getCaptchaKey()));
        if (StrUtil.isEmpty(cachedCaptcha) || !cachedCaptcha.equalsIgnoreCase(dto.getCaptcha())) {
            throw new BusinessException(400, "验证码错误或已过期");
        }
        // 校验通过，删除验证码
        stringRedisTemplate.delete(RedisKeyUtil.captchaKey(dto.getCaptchaKey()));

        // 2. 校验用户名/邮箱是否已注册
        User existUser = userMapper.selectByAccount(dto.getUsername());
        if (existUser != null) {
            throw new BusinessException(400, "用户名已被注册");
        }
        existUser = userMapper.selectByAccount(dto.getEmail());
        if (existUser != null) {
            throw new BusinessException(400, "邮箱已被注册");
        }

        // 3. BCrypt 加密密码
        String encryptedPassword = PasswordUtil.encode(dto.getPassword());

        // 4. 创建用户
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setPassword(encryptedPassword);
        user.setSalt(null); // BCrypt 无需单独存盐
        user.setRole(Constants.ROLE_USER);

        // 开发模式：直接激活；生产模式：邮件激活
        if (devMode || mailUsername.isEmpty() || "your-email@qq.com".equals(mailUsername)) {
            user.setStatus(Constants.USER_STATUS_ACTIVE);
            user.setActivationCode(null);
        } else {
            user.setStatus(Constants.USER_STATUS_INACTIVE);
            user.setActivationCode(IdUtil.fastSimpleUUID());
        }
        userMapper.insert(user);

        // 注册积分奖励
        try { pointsService.awardRegister(user.getId()); } catch (Exception ignored) {}

        // 5. 发送激活邮件（仅生产模式）
        if (user.getActivationCode() != null) {
            sendActivationEmail(user);
        }
    }

    @Override
    public void activate(String activationCode) {
        User user = userMapper.selectByActivationCode(activationCode);
        if (user == null) {
            throw new BusinessException(400, "激活链接无效");
        }
        if (user.getStatus() == Constants.USER_STATUS_ACTIVE) {
            throw new BusinessException(400, "账户已激活，请直接登录");
        }
        user.setStatus(Constants.USER_STATUS_ACTIVE);
        user.setActivationCode(null);
        userMapper.updateById(user);
    }

    @Override
    public UserVO login(LoginDTO dto) {
        // 1. 校验验证码
        String cachedCaptcha = stringRedisTemplate.opsForValue()
                .get(RedisKeyUtil.captchaKey(dto.getCaptchaKey()));
        if (StrUtil.isEmpty(cachedCaptcha) || !cachedCaptcha.equalsIgnoreCase(dto.getCaptcha())) {
            throw new BusinessException(400, "验证码错误或已过期");
        }
        stringRedisTemplate.delete(RedisKeyUtil.captchaKey(dto.getCaptchaKey()));

        // 2. 查询用户
        User user = userMapper.selectByAccount(dto.getAccount());
        if (user == null) {
            throw new BusinessException(400, "用户名或密码错误");
        }
        if (user.getStatus() == Constants.USER_STATUS_INACTIVE) {
            throw new BusinessException(400, "账户未激活，请先激活邮箱");
        }
        if (user.getStatus() == Constants.USER_STATUS_BANNED) {
            throw new BusinessException(400, "账户已被封禁");
        }

        // 3. 密码验证（兼容BCrypt和旧MD5）
        if (!PasswordUtil.matches(dto.getPassword(), user.getSalt(), user.getPassword())) {
            throw new BusinessException(400, "用户名或密码错误");
        }

        // 旧MD5密码自动迁移到BCrypt
        if (PasswordUtil.isLegacy(user.getPassword())) {
            user.setPassword(PasswordUtil.encode(dto.getPassword()));
            user.setSalt(null);
            userMapper.updateById(user);
        }

        // 4. 生成Token，存入Redis（有效期7天）
        String token = IdUtil.fastSimpleUUID();
        stringRedisTemplate.opsForValue().set(
                RedisKeyUtil.tokenKey(token), user.getId().toString(),
                7, TimeUnit.DAYS);

        return buildUserVO(user, token);
    }

    @Override
    public UserVO getUserByToken(String token) {
        String userIdStr = stringRedisTemplate.opsForValue()
                .get(RedisKeyUtil.tokenKey(token));
        if (StrUtil.isEmpty(userIdStr)) {
            return null;
        }
        User user = userMapper.selectById(Long.valueOf(userIdStr));
        if (user == null || user.getStatus() != Constants.USER_STATUS_ACTIVE) {
            return null;
        }
        // 续期Token
        stringRedisTemplate.expire(RedisKeyUtil.tokenKey(token), 7, TimeUnit.DAYS);

        return buildUserVO(user, token);
    }

    @Override
    public void logout(String token) {
        stringRedisTemplate.delete(RedisKeyUtil.tokenKey(token));
    }

    private void sendActivationEmail(User user) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(user.getEmail());
            helper.setSubject("【羊羊网】账户激活邮件");
            String activationUrl = "http://localhost:8080/api/auth/activate/" + user.getActivationCode();
            String content = String.format(
                    "<h3>欢迎注册羊羊网论坛</h3>" +
                    "<p>请点击以下链接激活您的账户（%d小时内有效）：</p>" +
                    "<a href='%s'>%s</a>",
                    activationExpireHours, activationUrl, activationUrl);
            helper.setText(content, true);
            mailSender.send(message);
        } catch (Exception e) {
            throw new BusinessException("激活邮件发送失败，请稍后重试");
        }
    }

    private UserVO buildUserVO(User user, String token) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setBio(user.getBio());
        vo.setRole(user.getRole());
        vo.setToken(token);
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
