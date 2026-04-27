package com.example.instagramclone.infrastructure.messaging;

import com.example.instagramclone.domain.dm.dto.DirectMessageResponse;
import com.example.instagramclone.infrastructure.messaging.dto.DmBroadcastMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

/**
 * Day 24: DM 실시간 메시지를 Redis 채널로 발행한다.
 *
 * <p>DirectMessageService 가 직접 RedisTemplate 을 알지 않도록 Publisher 를 분리했다.
 * 테스트 시에는 이 빈만 목킹하면 된다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DmMessagePublisher {

    private final RedisTemplate<String, Object> pubSubRedisTemplate;
    private final ChannelTopic dmChannelTopic;

    public void publish(Long recipientId, DirectMessageResponse payload) {
        DmBroadcastMessage envelope = new DmBroadcastMessage(recipientId, payload);
        pubSubRedisTemplate.convertAndSend(dmChannelTopic.getTopic(), envelope);
        log.debug("[DM Publish] recipientId={}, messageId={}",
                recipientId, payload.messageId());
    }
}
