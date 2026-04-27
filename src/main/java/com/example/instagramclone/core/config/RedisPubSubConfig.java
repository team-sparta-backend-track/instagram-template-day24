package com.example.instagramclone.core.config;

import com.example.instagramclone.core.constant.RedisKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Day 24: Redis Pub/Sub 인프라 설정.
 *
 * <p>역할 분리:
 * <ul>
 *   <li>{@link RedisTemplate} — 발행(publish) 쪽. 이미 다른 용도(Write-Back 등)로 있는 템플릿과
 *       구분하려고 Pub/Sub 전용으로 문자열 키 + JSON 값 직렬화를 명시한다.</li>
 *   <li>{@link RedisMessageListenerContainer} — 구독(subscribe) 쪽 총괄 관리자.
 *       개별 리스너는 각 도메인 패키지에서 등록한다 (infrastructure/messaging/*).</li>
 *   <li>{@link ChannelTopic} 빈 — 채널별 토픽을 상수처럼 주입받아 쓰기 위함.</li>
 * </ul>
 *
 * <p><b>Spring Boot 4.x 마이그레이션 노트</b>: Spring Boot 4.0 부터 자동 구성되는 {@code ObjectMapper}
 * 는 <b>Jackson 3</b> ({@code tools.jackson.databind.ObjectMapper}) 다.
 * 그런데 Spring Data Redis 의 {@link GenericJackson2JsonRedisSerializer} 는 이름 그대로
 * Jackson <b>2</b> ({@code com.fasterxml.jackson.databind.ObjectMapper}) 기반이라
 * 자동 구성된 빈을 그대로 주입할 수 없다. 그래서 이 Config 가 Pub/Sub 전용 Jackson 2
 * {@code ObjectMapper} 빈({@link #pubSubObjectMapper()}) 을 직접 만들어 둔다.
 * <br>(클래스 자체에 "marked for removal" 표기가 붙어 있는 것도 같은 마이그레이션의 일환이다 —
 * 대체 Jackson 3 기반 시리얼라이저가 GA 되면 그쪽으로 옮겨가면 된다.)
 */
@Configuration
public class RedisPubSubConfig {

    /**
     * Pub/Sub 전용 Jackson 2 ObjectMapper.
     * <p>왜 직접 만드는가? 위 클래스 Javadoc 의 마이그레이션 노트 참고.
     * <p>모듈 등록: {@link JavaTimeModule} — DTO 의 {@code LocalDateTime} 필드를
     * ISO-8601 문자열로 직렬화/역직렬화하기 위해 필수.
     */
    @Bean
    public ObjectMapper pubSubObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        return om;
    }

    /**
     * Pub/Sub 전용 RedisTemplate.
     * <p>값 직렬화를 Jackson JSON 으로 고정해 redis-cli {@code MONITOR} 에서도
     * 사람이 읽을 수 있는 형태로 메시지가 보이게 한다.
     */
    @Bean
    @SuppressWarnings("removal") // Spring Data Redis 4.x: Jackson 3 기반 대체 API 가 GA 될 때까지 빌더 사용 유지
    public RedisTemplate<String, Object> pubSubRedisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper pubSubObjectMapper) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        // GenericJackson2JsonRedisSerializer 는 @class 타입 정보를 포함한다.
        // 서버 간 같은 패키지 구조를 공유하므로 역직렬화 시 원본 타입으로 복원된다.
        template.setValueSerializer(GenericJackson2JsonRedisSerializer.builder()
                .objectMapper(pubSubObjectMapper)
                .build());
        return template;
    }

    /**
     * Pub/Sub 구독 총괄 컨테이너.
     * <p>개별 리스너 등록은 각 도메인 패키지의 리스너 빈이
     * {@code @PostConstruct} 등으로 수행한다 (다음 Step 에서 구현).
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }

    /** DM 실시간 브리지 채널 토픽. */
    @Bean
    public ChannelTopic dmChannelTopic() {
        return new ChannelTopic(RedisKeys.DM_CHANNEL);
    }

    /** 알림 실시간 브리지 채널 토픽. */
    @Bean
    public ChannelTopic notificationChannelTopic() {
        return new ChannelTopic(RedisKeys.NOTIFICATION_CHANNEL);
    }
}
