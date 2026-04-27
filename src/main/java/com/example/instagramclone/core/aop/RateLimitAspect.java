package com.example.instagramclone.core.aop;

import com.example.instagramclone.core.aop.annotation.RateLimit;
import com.example.instagramclone.core.constant.RedisKeys;
import com.example.instagramclone.core.exception.RateLimitErrorCode;
import com.example.instagramclone.core.exception.RateLimitException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * [Day 24] {@link RateLimit} 어노테이션을 처리하는 AOP Aspect.
 *
 * <p><b>핵심 실행 순서</b>
 * <pre>
 *   RateLimitAspect (카운터 체크) — 초과 시 즉시 예외
 *     └─ 실제 메서드 실행
 * </pre>
 *
 * <p><b>키 해석</b>: Day 18 {@code @DistributedLock} 와 동일한 SpEL 패턴.
 * 메서드 파라미터를 {@code #이름} 으로 참조해 호출 컨텍스트(HTTP/STOMP/IP)
 * 와 무관하게 동작한다.
 *
 * <p><b>Redis 명령</b>: Lua 스크립트로 {@code INCR} + (첫 호출 시) {@code EXPIRE}
 * 를 한 번의 왕복·원자적으로 실행한다 (Day 24 과제 1).
 *
 * <p><b>실행 순서 보장</b>: {@code @Order(0)} 으로 {@code @DistributedLock(@Order(1))}
 * 와 {@code @Transactional(MAX_VALUE)} 보다 바깥에서 먼저 차단한다 — Rate Limit 초과
 * 시 락 획득·트랜잭션 시작·DB 접근이 모두 일어나지 않는다.
 */
@Slf4j
@Aspect
@Order(0) // @DistributedLock(@Order(1))·@Transactional 보다 바깥쪽에서 먼저 차단
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Long> rateLimitScript;

    // SpelExpressionParser 는 스레드 안전하므로 인스턴스를 재사용한다 (DistributedLockAspect 와 동일)
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String partitionKey = resolveKey(joinPoint, rateLimit.key());
        String redisKey = RedisKeys.rateLimit(rateLimit.action(), partitionKey);

        // Lua 스크립트 원자 실행: INCR + (첫 호출 시) EXPIRE
        // KEYS = [redisKey], ARGV = [windowSeconds]
        Long count = stringRedisTemplate.execute(
                rateLimitScript,
                List.of(redisKey),
                String.valueOf(rateLimit.windowSeconds())
        );

        if (count != null && count > rateLimit.limit()) {
            // 남은 TTL 조회 — 음수(-1: TTL 없음, -2: 키 없음)는 null 로 변환해 응답에서 빠지게 한다
            Long ttl = stringRedisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
            Long retryAfter = (ttl != null && ttl > 0) ? ttl : null;

            log.warn("[RateLimit] 초과 partitionKey={}, action={}, count={}, limit={}, retryAfter={}s",
                    partitionKey, rateLimit.action(), count, rateLimit.limit(), retryAfter);

            throw new RateLimitException(RateLimitErrorCode.RATE_LIMIT_EXCEEDED, retryAfter);
        }

        log.debug("[RateLimit] 통과 partitionKey={}, action={}, count={}/{}",
                partitionKey, rateLimit.action(), count, rateLimit.limit());

        return joinPoint.proceed();
    }

    /**
     * SpEL 표현식을 평가해 분리 키 문자열을 반환한다.
     * Day 18 {@code DistributedLockAspect.resolveKey} 와 동일한 패턴.
     *
     * <pre>
     *   key = "#senderId", senderId=42  →  "42"
     * </pre>
     */
    private String resolveKey(ProceedingJoinPoint joinPoint, String keyExpression) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        StandardEvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }

        Object value = parser.parseExpression(keyExpression).getValue(context);
        if (value == null) {
            // 키가 null 이면 모든 호출이 같은 카운터를 공유하게 되어 정책이 무너진다.
            log.error("[RateLimit] key 표현식이 null 로 평가됨: expression={}", keyExpression);
            throw new RateLimitException(RateLimitErrorCode.RATE_LIMIT_EXCEEDED);
        }
        return value.toString();
    }
}
