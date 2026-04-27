package com.example.instagramclone.infrastructure.messaging;

import com.example.instagramclone.domain.notification.dto.NotificationResponse;
import com.example.instagramclone.infrastructure.messaging.dto.NotificationBroadcastMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

/**
 * Day 24: 알림 실시간 메시지를 Redis 채널로 발행한다.
 *
 * <p>{@link com.example.instagramclone.domain.notification.application.NotificationEventHandler}
 * 가 직접 RedisTemplate 을 알지 않도록 Publisher 를 분리했다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationMessagePublisher {

    private final RedisTemplate<String, Object> pubSubRedisTemplate;
    private final ChannelTopic notificationChannelTopic;

    public void publish(Long receiverId, NotificationResponse payload) {
        NotificationBroadcastMessage envelope =
                new NotificationBroadcastMessage(receiverId, payload);
        pubSubRedisTemplate.convertAndSend(notificationChannelTopic.getTopic(), envelope);
        log.debug("[Notification Publish] receiverId={}, type={}",
                receiverId, payload.type());
    }
}
