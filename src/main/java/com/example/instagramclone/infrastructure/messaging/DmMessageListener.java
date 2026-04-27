package com.example.instagramclone.infrastructure.messaging;

import com.example.instagramclone.infrastructure.messaging.dto.DmBroadcastMessage;
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
 * Day 24: DM 채널 구독자.
 *
 * <p>Redis 에 publish 된 {@link DmBroadcastMessage} 를 받아 자기 서버의
 * {@link SimpMessagingTemplate} 으로 해당 사용자의 개인 큐에 Push 한다.
 *
 * <p>핵심: 카리나가 <b>이 서버에 연결돼 있지 않으면</b> convertAndSendToUser 는
 * 세션이 없는 걸 확인하고 조용히 무시한다. 즉, "모든 서버가 일단 받되,
 * 자기 메모리에 세션이 있는 서버만 실제 전달"이 자동으로 이뤄진다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DmMessageListener implements MessageListener {

    private static final String USER_DM_DESTINATION = "/queue/dm";

    private final RedisMessageListenerContainer listenerContainer;
    private final ChannelTopic dmChannelTopic;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 빈 초기화 시 컨테이너에 자기 자신을 등록한다.
     * → 애플리케이션 시작과 동시에 SUBSCRIBE 가 발동한다.
     */
    @PostConstruct
    public void subscribe() {
        listenerContainer.addMessageListener(this, dmChannelTopic);
        log.info("[DM Listener] 구독 시작: channel={}", dmChannelTopic.getTopic());
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            DmBroadcastMessage envelope = objectMapper.readValue(
                    message.getBody(), DmBroadcastMessage.class);

            // 자기 서버의 세션에만 전달. 세션 없으면 SimpMessagingTemplate 이 조용히 무시.
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(envelope.recipientId()),
                    USER_DM_DESTINATION,
                    envelope.payload()
            );

            log.debug("[DM Listener] 전달 시도 recipientId={}, messageId={}",
                    envelope.recipientId(), envelope.payload().messageId());

        } catch (Exception e) {
            // 메시지 1건 역직렬화·전달 실패가 전체 구독을 끊지 않도록 로깅만 한다.
            log.error("[DM Listener] 메시지 처리 실패: {}", e.getMessage(), e);
        }
    }
}
