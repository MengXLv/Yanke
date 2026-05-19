package com.yangke.forum.module.notification.consumer;

import com.yangke.forum.common.Constants;
import com.yangke.forum.module.notification.entity.Notification;
import com.yangke.forum.module.notification.entity.NotificationMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Kafka消费者：异步消费通知消息并持久化到数据库
 */
@Slf4j
@Component
public class NotificationConsumer {

    @Resource
    private NotificationMapper notificationMapper;

    @KafkaListener(
            topics = Constants.TOPIC_NOTIFICATION,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "manualCommitContainerFactory"
    )
    public void consume(ConsumerRecord<String, Notification> record, Acknowledgment ack) {
        try {
            Notification notification = record.value();
            if (notification.getReceiverId() == null) {
                log.warn("Notification dropped: receiverId is null, type={}", notification.getType());
                ack.acknowledge();
                return;
            }
            notificationMapper.insert(notification);
            log.debug("Notification saved: type={}, receiver={}", notification.getType(),
                    notification.getReceiverId());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to persist notification, will retry", e);
            // 不ack，消息会重新投递
        }
    }
}
