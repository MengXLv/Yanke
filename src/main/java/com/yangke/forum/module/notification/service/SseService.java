package com.yangke.forum.module.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SseService {

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    /** 客户端建立SSE长连接 */
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(0L); // 0 = no timeout
        emitters.put(userId, emitter);

        // 发送初始连接确认
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException ignored) {}

        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError(e -> emitters.remove(userId));

        return emitter;
    }

    /** 推送事件给指定用户 */
    public void push(Long userId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                emitters.remove(userId);
            }
        }
    }

    /** 检查用户是否在线 */
    public boolean isOnline(Long userId) {
        return emitters.containsKey(userId);
    }
}
