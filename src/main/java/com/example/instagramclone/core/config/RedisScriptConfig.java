package com.example.instagramclone.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * [Day 24 과제 1] Redis Lua 스크립트를 빈으로 등록한다.
 *
 * <p>스크립트는 애플리케이션 부팅 시 한 번 파일에서 읽은 뒤, Spring Data Redis 가
 * 내부적으로 SHA1 을 계산해 캐싱한다. 이후 실행은 {@code EVALSHA} 로 전송되어
 * 매번 스크립트 본문을 Redis 에 보내지 않는다 — 네트워크 페이로드가 훨씬 작다.
 */
@Configuration
public class RedisScriptConfig {

    /**
     * Rate Limit 용 Lua 스크립트 빈.
     *
     * <p>{@code DefaultRedisScript<Long>} 으로 선언하면 스크립트 반환값을 자동으로
     * {@code Long} 으로 역직렬화해 준다. (반환값 숫자는 Long 으로 받는 게 관례)
     */
    @Bean
    public RedisScript<Long> rateLimitScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("scripts/rate-limit.lua")
        ));
        script.setResultType(Long.class);
        return script;
    }
}
