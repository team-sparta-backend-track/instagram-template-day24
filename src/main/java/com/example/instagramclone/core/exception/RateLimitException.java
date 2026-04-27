package com.example.instagramclone.core.exception;

public class RateLimitException extends BusinessException {

    /**
     * Rate Limit 해제까지 남은 시간(초).
     * null 이면 응답에서도 이 필드가 빠진다 ({@code @JsonInclude(NON_NULL)}).
     */
    private final Long retryAfterSeconds;

    public RateLimitException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public RateLimitException(ErrorCode errorCode, Long retryAfterSeconds) {
        super(errorCode);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
