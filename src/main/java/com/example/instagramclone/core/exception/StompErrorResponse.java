package com.example.instagramclone.core.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * STOMP 메시지 처리 중 발생한 예외를 클라이언트에 전달하는 응답 DTO.
 *
 * <p><b>왜 별도 DTO 인가?</b><br>
 * HTTP 의 {@code ErrorResponse} 는 {@code path} 같은 HTTP 전용 필드가 들어 있지만,
 * STOMP 는 destination 기반이라 의미가 다르다. 그리고 STOMP 응답은 별도 토픽
 * ({@code /user/queue/errors}) 으로 가므로, 클라이언트가 정상 페이로드와 에러 페이로드를
 * destination 만으로 분기할 수 있어야 한다 — 두 스키마를 섞지 않는 게 맞다.</p>
 *
 * <p>{@code retryAfterSeconds} 는 RateLimit 에 의한 차단일 때만 채워진다.
 * 그 외 비즈니스 예외(권한 없음, 존재하지 않는 자원 등) 에서는 null 이며,
 * {@code @JsonInclude(NON_NULL)} 로 응답에서 자동으로 빠진다.</p>
 *
 * @param timestamp 에러 발생 시각
 * @param status HTTP 상태 코드 (개념적 매핑 — STOMP 자체엔 상태 코드가 없지만,
 *               프론트가 axios 에러 처리와 동일 분기를 쓸 수 있게 같이 내려준다)
 * @param code 도메인 에러 코드 (예: "R001")
 * @param message 사람이 읽는 메시지
 * @param retryAfterSeconds RateLimit 차단 시 남은 초 — 그 외엔 null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StompErrorResponse(
        LocalDateTime timestamp,
        int status,
        String code,
        String message,
        Long retryAfterSeconds
) {

    public static StompErrorResponse of(ErrorCode errorCode, String message, Long retryAfterSeconds) {
        return new StompErrorResponse(
                LocalDateTime.now(),
                errorCode.getStatus().value(),
                errorCode.getCode(),
                message,
                retryAfterSeconds
        );
    }
}
