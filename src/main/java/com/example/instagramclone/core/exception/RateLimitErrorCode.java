package com.example.instagramclone.core.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RateLimitErrorCode implements ErrorCode {
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "R001",
            "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
