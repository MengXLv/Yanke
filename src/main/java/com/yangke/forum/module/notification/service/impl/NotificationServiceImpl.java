package com.yangke.forum.module.notification.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yangke.forum.common.Constants;
import com.yangke.forum.common.BusinessException;
import com.yangke.forum.module.notification.entity.Notification;
import com.yangke.forum.module.notification.entity.NotificationMapper;
import com.yangke.forum.module.notification.service.NotificationService;
import com.yangke.forum.module.notification.service.WebSocketPushService;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFutureCallback;

import javax.annotation.Resource;
import java.util.List;

@Service
public class NotificationServiceImpl implements NotificationService {

    @Resource
    private NotificationMapper notificationMapper;

    @Resource
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Resource
    private WebSocketPushService wsPushService;

    @Override
    public void sendAsync(Notification notification) {
        // 实时推送（不等Kafka消费）
        wsPushService.pushNotification(notification);

        // 通过Kafka异步持久化
        kafkaTemplate.send(Constants.TOPIC_NOTIFICATION,
                String.valueOf(notification.getReceiverId()), notification)
                .addCallback(new ListenableFutureCallback<SendResult<String, Object>>() {
                    @Override
                    public void onSuccess(SendResult<String, Object> result) {
                        // 发送成功，无需处理
                    }

                    @Override
                    public void onFailure(Throwable ex) {
                        // 发送失败，降级为直接写入数据库
                        notificationMapper.insert(notification);
                    }
                });
    }

    @Override
    public List<Notification> getUnreadNotifications(Long userId, int page, int size) {
        LambdaQueryWrapper<Notification> wrapper = Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getReceiverId, userId)
                .eq(Notification::getIsRead, 0)
                .orderByDesc(Notification::getCreateTime);
        IPage<Notification> result = notificationMapper.selectPage(new Page<>(page, size), wrapper);
        return result.getRecords();
    }

    @Override
    public List<Notification> getAllNotifications(Long userId, int page, int size) {
        LambdaQueryWrapper<Notification> wrapper = Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getReceiverId, userId)
                .orderByDesc(Notification::getCreateTime);
        IPage<Notification> result = notificationMapper.selectPage(new Page<>(page, size), wrapper);
        return result.getRecords();
    }

    @Override
    public long getUnreadCount(Long userId) {
        return notificationMapper.selectCount(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getReceiverId, userId)
                .eq(Notification::getIsRead, 0));
    }

    @Override
    public void markAllRead(Long userId) {
        notificationMapper.markAllRead(userId);
    }

    @Override
    public void markRead(Long notificationId) {
        Notification notification = new Notification();
        notification.setId(notificationId);
        notification.setIsRead(1);
        notificationMapper.updateById(notification);
    }
}
