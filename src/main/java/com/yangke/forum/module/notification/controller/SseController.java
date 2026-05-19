package com.yangke.forum.module.notification.controller;

import com.yangke.forum.common.BusinessException;
import com.yangke.forum.common.Constants;
import com.yangke.forum.module.notification.service.SseService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/sse")
public class SseController {

    @Resource
    private SseService sseService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @GetMapping("/connect")
    public SseEmitter connect(@RequestParam("token") String token) {
        String userIdStr = stringRedisTemplate.opsForValue().get("token:" + token);
        if (userIdStr == null) throw new BusinessException(401, "请先登录");
        Long userId = Long.valueOf(userIdStr);
        return sseService.subscribe(userId);
    }
}
