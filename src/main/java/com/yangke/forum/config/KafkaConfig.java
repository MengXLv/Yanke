package com.yangke.forum.config;

import com.yangke.forum.common.Constants;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic notificationTopic() {
        return new NewTopic(Constants.TOPIC_NOTIFICATION, 4, (short) 1);
    }

    @Bean
    public NewTopic postEventTopic() {
        return new NewTopic(Constants.TOPIC_POST_EVENT, 4, (short) 1);
    }

    /**
     * 手动提交偏移量的监听容器工厂
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> manualCommitContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}
