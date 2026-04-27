package com.example.instagramclone.infrastructure.messaging;

import com.example.instagramclone.infrastructure.messaging.dto.NotificationBroadcastMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Day 24: 알림 채널 구독자.
 *
 * <p>DM 리스너와 동일한 패턴으로 알림 메시지도 모든 서버에 브로드캐스트되어,
 * 수신자가 어느 서버에 붙어 있든 자기 서버의 SimpMessagingTemplate 으로 전달된다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationMessageListener implements MessageListener {

    private static final String NOTIFICATION_QUEUE = "/queue/notifications";

    private final RedisMessageListenerContainer listenerContainer;
    private final ChannelTopic notificationChannelTopic;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @PostConstruct
    public void subscribe() {
        listenerContainer.addMessageListener(this, notificationChannelTopic);
        log.info("[Notification Listener] 구독 시작: channel={}",
                notificationChannelTopic.getTopic());
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            NotificationBroadcastMessage envelope = objectMapper.readValue(
                    message.getBody(), NotificationBroadcastMessage.class);

            messagingTemplate.convertAndSendToUser(
                    String.valueOf(envelope.receiverId()),
                    NOTIFICATION_QUEUE,
                    envelope.payload()
            );
        } catch (Exception e) {
            log.error("[Notification Listener] 처리 실패: {}", e.getMessage(), e);
        }
    }
}
