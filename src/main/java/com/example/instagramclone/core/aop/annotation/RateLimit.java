package com.example.instagramclone.core.aop.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * [Day 24] Redis INCR + EXPIRE 기반 Rate Limiting 어노테이션.
 *
 * <p>사용 예:
 * <pre>
 *   {@literal @}RateLimit(action = "dm.send", key = "#senderId", limit = 30, windowSeconds = 60)
 *   public DirectMessageResponse sendMessage(Long senderId, DmSendRequest request) {
 *       // 비즈니스 로직
 *   }
 * </pre>
 *
 * <p><b>키 구성</b>: {@code ratelimit:{action}:{key 평가 결과}}
 *
 * <p><b>왜 SpEL 인가?</b><br>
 * - HTTP 요청은 SecurityContextHolder 에 인증 정보가 있지만, STOMP 메시지 핸들러
 *   스레드에는 SecurityContext 가 없다 ({@code Principal} 만 있음).
 * - 결국 어떤 컨텍스트에서 호출되든 "유저를 식별하는 값" 은 항상
 *   <b>메서드 파라미터</b> 로 들어온다 (예: {@code senderId}, {@code loginMemberId}).
 * - 그래서 Day 18 {@code @DistributedLock} 와 똑같이 SpEL 로 파라미터에서 직접 뽑는다.
 *   Aspect 가 SecurityContext / STOMP / IP 같은 호출 컨텍스트에 결합되지 않는다.
 *
 * <p><b>동작</b>: 메서드 호출 직전 {@code INCR} 수행 → 결과가 {@code limit} 초과 시
 * {@link com.example.instagramclone.core.exception.RateLimitException} 을 던져 메서드 실행 자체를 막는다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 액션 식별자. Redis 키의 중간 세그먼트로 쓰인다. 예: "dm.send", "follow.change" */
    String action();

    /**
     * 카운터 분리 키 — SpEL 표현식. 메서드 파라미터 이름을 {@code #이름} 으로 참조한다.
     * 예: {@code "#senderId"}, {@code "#loginMemberId"}.
     * 비로그인 IP 기반은 호출 측에서 IP 를 인자로 넘겨 같은 패턴을 쓴다.
     */
    String key();

    /** 윈도우 동안 허용되는 최대 요청 수. */
    int limit();

    /** 윈도우 크기(초). 첫 요청 시 이 값으로 TTL 이 설정된다. */
    int windowSeconds();
}
