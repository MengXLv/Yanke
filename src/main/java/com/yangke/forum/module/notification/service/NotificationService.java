package com.yangke.forum.module.notification.service;

import com.yangke.forum.module.notification.entity.Notification;
import java.util.List;

public interface NotificationService {

    /**
     * 异步发送通知（通过Kafka）
     */
    void sendAsync(Notification notification);

    /**
     * 获取用户未读通知列表
     */
    List<Notification> getUnreadNotifications(Long userId, int page, int size);

    /**
     * 获取用户全部通知
     */
    List<Notification> getAllNotifications(Long userId, int page, int size);

    /**
     * 未读数量
     */
    long getUnreadCount(Long userId);

    /**
     * 全部标记已读
     */
    void markAllRead(Long userId);

    /**
     * 单条标记已读
     */
    void markRead(Long notificationId);
}
