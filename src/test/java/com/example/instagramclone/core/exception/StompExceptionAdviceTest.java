package com.example.instagramclone.core.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StompExceptionAdvice} 단위 테스트.
 *
 * <p>STOMP 인프라 통합 테스트(@SpringBootTest + WebSocketStompClient) 는
 * {@code tdd-code-verification} 스킬의 STOMP 레시피를 따른다.
 * 이 테스트는 advice 메서드가 입력 예외를 정확히 {@link StompErrorResponse} 로
 * 변환하는지만 검증한다 — 빠르고 회귀에 민감한 단위 테스트.</p>
 */
class StompExceptionAdviceTest {

    private final StompExceptionAdvice advice = new StompExceptionAdvice();

    @Nested
    @DisplayName("handleRateLimit()")
    class HandleRateLimit {

        @Test
        @DisplayName("RateLimit 초과 — retryAfterSeconds 가 응답에 실린다")
        void rate_limit_response_includes_retry_after() {
            RateLimitException e = new RateLimitException(
                    RateLimitErrorCode.RATE_LIMIT_EXCEEDED, 42L);

            StompErrorResponse response = advice.handleRateLimit(e);

            assertThat(response.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
            assertThat(response.code()).isEqualTo("R001");
            assertThat(response.message()).isEqualTo(RateLimitErrorCode.RATE_LIMIT_EXCEEDED.getMessage());
            assertThat(response.retryAfterSeconds()).isEqualTo(42L);
            assertThat(response.timestamp()).isNotNull();
        }

        @Test
        @DisplayName("RateLimit 초과 — retryAfterSeconds 가 null 이면 응답에서도 null")
        void rate_limit_response_omits_retry_after_when_null() {
            RateLimitException e = new RateLimitException(
                    RateLimitErrorCode.RATE_LIMIT_EXCEEDED);

            StompErrorResponse response = advice.handleRateLimit(e);

            assertThat(response.retryAfterSeconds()).isNull();
            // @JsonInclude(NON_NULL) 로 직렬화 시 응답에서 빠진다 — JSON 직렬화 검증은 통합 테스트에서.
        }
    }

    @Nested
    @DisplayName("handleBusiness()")
    class HandleBusiness {

        @Test
        @DisplayName("BusinessException — code/message/status 가 ErrorCode 그대로 전달된다")
        void business_exception_response_carries_error_code_fields() {
            // BusinessException 은 abstract — 익명 클래스로 구체화
            BusinessException e = new BusinessException(TestErrorCode.SAMPLE) {};

            StompErrorResponse response = advice.handleBusiness(e);

            assertThat(response.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
            assertThat(response.code()).isEqualTo("T001");
            assertThat(response.message()).isEqualTo("샘플 비즈니스 예외");
            assertThat(response.retryAfterSeconds())
                    .as("RateLimit 외의 비즈니스 예외에서는 retryAfterSeconds 가 null 이어야 한다")
                    .isNull();
        }
    }

    /** 테스트 전용 ErrorCode — 다른 도메인 ErrorCode 에 영향 안 주려고 별도로 둔다. */
    private enum TestErrorCode implements ErrorCode {
        SAMPLE(HttpStatus.BAD_REQUEST, "T001", "샘플 비즈니스 예외");

        private final HttpStatus status;
        private final String code;
        private final String message;

        TestErrorCode(HttpStatus status, String code, String message) {
            this.status = status;
            this.code = code;
            this.message = message;
        }

        @Override public HttpStatus getStatus() { return status; }
        @Override public String getCode() { return code; }
        @Override public String getMessage() { return message; }
    }
}
