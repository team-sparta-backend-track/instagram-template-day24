package com.example.instagramclone.core.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

/**
 * [Day 24] STOMP 메시지 처리 전용 예외 advice.
 *
 * <p><b>왜 필요한가?</b><br>
 * {@link GlobalExceptionHandler} 는 {@code @RestControllerAdvice} 라
 * <b>HTTP DispatcherServlet 경로</b> 의 예외만 잡는다. 반면 STOMP 메시지 처리는
 * 별도 인프라({@code SimpAnnotationMethodMessageHandler}) 를 타기 때문에,
 * {@code @MessageMapping} 컨트롤러에서 {@link BusinessException} 이나
 * {@link RateLimitException} 이 던져져도 HTTP advice 는 못 잡는다.
 * 그러면 클라이언트에 <b>아무 응답도 가지 않은 채</b> 메시지 처리가 끝나고,
 * 사용자는 "보냈는데 화면에 아무 일도 안 일어남" 으로 보게 된다.</p>
 *
 * <p>이 advice 는 HTTP 의 {@code @RestControllerAdvice} 와 정확히 짝을 이루는
 * STOMP 측 advice 다. 모든 {@code @MessageMapping} 컨트롤러에서 던진 예외를 잡아
 * sender 의 개인 에러 큐 ({@code /user/queue/errors}) 로 변환된 응답을 돌려준다.</p>
 *
 * <p><b>클라이언트 측 합의</b>: 프론트는 STOMP CONNECT 직후 다음 두 큐를 모두 SUBSCRIBE 한다.
 * <ul>
 *   <li>{@code /user/queue/dm} — 정상 메시지</li>
 *   <li>{@code /user/queue/errors} — STOMP 처리 중 발생한 에러</li>
 * </ul>
 * 같은 SEND 요청에 대해 둘 중 하나의 destination 으로만 응답이 온다.</p>
 *
 * <p><b>HTTP advice 와의 관계</b>: {@code @ExceptionHandler}(HTTP) 와
 * {@code @MessageExceptionHandler}(STOMP) 는 적용 대상이 다르므로 같은
 * {@link BusinessException} 을 등록해도 충돌하지 않는다. 두 advice 는 각자의 채널에서
 * 독립적으로 동작한다.</p>
 */
@ControllerAdvice
@Slf4j
public class StompExceptionAdvice {

    private static final String ERROR_QUEUE = "/queue/errors";

    /**
     * RateLimit 초과 — {@code retryAfterSeconds} 까지 응답에 실어 클라이언트가
     * 재시도 타이밍을 알 수 있게 한다. HTTP 측의 {@link GlobalExceptionHandler}
     * RateLimit 핸들러와 같은 정보를 제공.
     */
    @MessageExceptionHandler(RateLimitException.class)
    @SendToUser(ERROR_QUEUE)
    public StompErrorResponse handleRateLimit(RateLimitException e) {
        log.warn("[STOMP RateLimit] {} (retryAfter={}s)", e.getMessage(), e.getRetryAfterSeconds());
        return StompErrorResponse.of(e.getErrorCode(), e.getMessage(), e.getRetryAfterSeconds());
    }

    /**
     * 그 외 비즈니스 예외 — 권한 없음, 존재하지 않는 대화방, 중복 등
     * STOMP 컨트롤러에서 던진 모든 {@link BusinessException} 을 일괄 처리.
     *
     * <p>RateLimit 핸들러보다 더 일반적인 타입이라 Spring 이 자동으로
     * RateLimit 핸들러를 우선 매칭하고 그 외엔 이 핸들러로 떨어진다.</p>
     */
    @MessageExceptionHandler(BusinessException.class)
    @SendToUser(ERROR_QUEUE)
    public StompErrorResponse handleBusiness(BusinessException e) {
        log.warn("[STOMP Business] {}", e.getMessage());
        return StompErrorResponse.of(e.getErrorCode(), e.getMessage(), null);
    }
}
