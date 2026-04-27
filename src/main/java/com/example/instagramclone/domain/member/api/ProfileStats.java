package com.example.instagramclone.domain.member.api;

/**
 * 프로필 헤더의 캐시 전용 스냅샷.
 *
 * <p><b>왜 별도 record 인가?</b><br>
 * {@link MemberProfileResponse} 는 viewer(loginMember) 에 따라 달라지는
 * {@code isFollowing}, {@code isCurrentUser} 를 함께 들고 있다.
 * 단일 키 {@code #targetMemberId} 로 캐싱할 때 이 두 필드까지 같이 캐싱하면
 * "프로필 주인 기준 1개의 스냅샷" 이 모든 viewer 에게 그대로 노출되어
 * <b>서로 다른 사람이 보는데도 같은 isFollowing / isCurrentUser 가 나온다</b>.
 * (kuromi 가 자기 프로필을 본 캐시를 heartping 이 그대로 받아서
 *  "내 프로필" 처럼 보이는 사고가 실제로 발생.)</p>
 *
 * <p>그래서 캐시에는 <b>프로필 주인 기준으로만 정의되는 viewer-independent 필드</b>
 * 만 담고, viewer 의존 필드({@code isFollowing}, {@code isCurrentUser}) 는
 * 진입점에서 매번 별도로 계산해 응답에 합성한다.</p>
 *
 * <p>참고: {@code @Cacheable} 메서드의 반환 타입이 곧 캐시에 저장되는 형태다.
 * 이 record 는 Jackson JSON 으로 직렬화되어 Redis 에 들어간다
 * ({@code GenericJackson2JsonRedisSerializer} 사용).</p>
 */
public record ProfileStats(
        Long memberId,
        String username,
        String name,
        String profileImageUrl,
        long followerCount,
        long followingCount,
        long postCount
) {
}
