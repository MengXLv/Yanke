package com.yangke.forum.module.notification.service;

import com.yangke.forum.module.message.entity.Message;
import com.yangke.forum.module.notification.entity.Notification;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * SSE 实时推送：通知、私信（用HashMap允许null值，因实体可能未入库）
 */
@Service
public class WebSocketPushService {

    @Resource
    private SseService sseService;

    public void pushNotification(Notification notification) {
        if (notification.getReceiverId() != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("id", notification.getId());
            data.put("content", notification.getContent());
            if (notification.getCreateTime() != null) {
                data.put("createTime", notification.getCreateTime().toString());
            }
            sseService.push(notification.getReceiverId(), "notification", data);
        }
    }

    public void pushMessage(Message message) {
        if (message.getReceiverId() != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("id", message.getId());
            data.put("senderId", message.getSenderId());
            data.put("content", message.getContent());
            if (message.getCreateTime() != null) {
                data.put("createTime", message.getCreateTime().toString());
            }
            sseService.push(message.getReceiverId(), "message", data);
        }
    }
}
