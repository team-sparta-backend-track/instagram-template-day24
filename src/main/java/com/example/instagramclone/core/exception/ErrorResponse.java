package com.example.instagramclone.core.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 에러 발생 시 클라이언트에게 응답할 데이터 형식을 정의한 클래스입니다.
 * 일관된 JSON 구조로 에러 정보를 전달하여 프론트엔드에서 처리가 쉽도록 합니다.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL) // null 인 필드는 JSON 에 포함하지 않는다
public class ErrorResponse {
    private final LocalDateTime timestamp; // 에러가 발생한 시간 (예: 2024-03-01T12:00:00)
    private final int status;              // HTTP 상태 코드 (예: 400, 404)
    private final String error;            // HTTP 상태 코드 이름 (예: BAD_REQUEST)
    private final String code;             // 기획 요구사항 및 클라이언트 식별을 위해 정의한 커스텀 에러 코드 (예: M001)
    private final String message;          // 에러 원인에 대한 상세 메시지 (예: 이미 존재하는 이메일입니다.)
    private final String path;             // 에러가 발생한 API 요청 경로 (예: /api/auth/signup)

    /**
     * [Day 24 과제 2] 429 응답에만 동봉되는 재시도 가능 시간(초).
     *
     * <p>Rate Limit 이 해제될 때까지 남은 TTL 을 담는다.
     * Rate Limit 이 아닌 일반 에러 응답에서는 null → {@code @JsonInclude(NON_NULL)} 덕분에
     * JSON 바디에 이 필드 자체가 나타나지 않는다. 기존 응답 포맷 호환성 유지!
     */
    private final Long retryAfterSeconds;
}
