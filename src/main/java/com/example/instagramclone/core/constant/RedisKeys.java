package com.example.instagramclone.core.constant;

/**
 * Redis 키 상수 모음.
 *
 * <p>키 문자열을 코드 곳곳에 리터럴로 흩어 두면 오타가 나도 컴파일 시점에 잡히지 않는다.
 * 이 클래스에 모아 두면 IDE 자동완성 + 오타 방지 + 전체 Redis 키 목록 파악이 한 곳에서 가능하다.
 *
 * <p>네이밍 규칙: 도메인_용도_PREFIX / 도메인_용도_PATTERN
 *
 * <pre>
 * 사용 예:
 *   redissonClient.getLock(RedisKeys.lockLike(postId))
 *   redisTemplate.keys(RedisKeys.LIKE_DELTA_PATTERN)
 *   redisTemplate.opsForValue().increment(RedisKeys.likeDelta(postId), +1)
 * </pre>
 *
 * <p>Day별 Redis 키 용도 요약:
 * <pre>
 *   Day 17 — Spring @Cacheable  : 키 관리는 CacheNames.java (Spring Cache 추상화)
 *   Day 18 — 분산 락            : lock:like:{postId}
 *   Day 19 — Write-Back delta   : like:delta:{postId}
 *   Day 24 — Pub/Sub 채널       : pubsub:dm, pubsub:notification
 *   Day 24 — Rate Limit 카운터  : ratelimit:{action}:{memberId}
 * </pre>
 */
public final class RedisKeys {

    // =========================================================================
    // Day 18: 분산 락 (Distributed Lock)
    // =========================================================================

    /** 좋아요 분산 락 키 접두사. 전체 키는 {@link #lockLike(long)} 사용. */
    public static final String LOCK_LIKE_PREFIX = "lock:like:";

    /**
     * 게시물 좋아요 분산 락 키를 생성한다.
     * <pre>lockLike(42) → "lock:like:42"</pre>
     */
    public static String lockLike(long postId) {
        return LOCK_LIKE_PREFIX + postId;
    }

    // =========================================================================
    // Day 19: Write-Back delta
    // =========================================================================

    /** 좋아요 delta 키 접두사. 전체 키는 {@link #likeDelta(long)} 사용. */
    public static final String LIKE_DELTA_PREFIX = "like:delta:";

    /** Write-Back 스케줄러에서 SCAN/KEYS 패턴으로 사용. */
    public static final String LIKE_DELTA_PATTERN = "like:delta:*";

    /**
     * 게시물 좋아요 delta 키를 생성한다.
     * <pre>likeDelta(42) → "like:delta:42"</pre>
     */
    public static String likeDelta(long postId) {
        return LIKE_DELTA_PREFIX + postId;
    }

    /**
     * delta 키에서 postId를 파싱한다.
     * <pre>postIdFromLikeDelta("like:delta:42") → 42L</pre>
     */
    public static long postIdFromLikeDelta(String key) {
        return Long.parseLong(key.substring(LIKE_DELTA_PREFIX.length()));
    }

    // =========================================================================
    // Day 24: Redis Pub/Sub 채널
    // =========================================================================

    /** DM 실시간 브리지 채널. 모든 WebSocket 서버가 구독한다. */
    public static final String DM_CHANNEL = "pubsub:dm";

    /** 알림 실시간 브리지 채널. DM 과 구분해 각 도메인 리스너가 관심사만 처리한다. */
    public static final String NOTIFICATION_CHANNEL = "pubsub:notification";

    // =========================================================================
    // Day 24: Rate Limiting (Token Bucket)
    // =========================================================================

    /** Rate Limit 키 접두사. 전체 키는 {@link #rateLimit(String, Long)} 사용. */
    public static final String RATE_LIMIT_PREFIX = "ratelimit:";

    /**
     * Rate Limit 카운터 키.
     * <p>partitionKey 는 보통 유저 ID 지만, 비로그인 경로(로그인·비밀번호 재설정 등)에서는
     * 호출 측이 IP 문자열을 넘겨 같은 키 포맷을 재사용한다.
     * <pre>rateLimit("dm.send", "42") → "ratelimit:dm.send:42"</pre>
     */
    public static String rateLimit(String action, String partitionKey) {
        return RATE_LIMIT_PREFIX + action + ":" + partitionKey;
    }

    private RedisKeys() {
        // 유틸리티 클래스 — 인스턴스화 방지
    }
}
