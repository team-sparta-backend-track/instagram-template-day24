package com.example.instagramclone.domain.member.api;

import com.example.instagramclone.domain.member.domain.Member;

/**
 * 프로필 1건 조회 응답 DTO.
 *
 * Day 15에서 프로필 헤더 통계(팔로워/팔로잉/게시물 수)까지 확장됩니다.
 */
public record MemberProfileResponse(
        Long memberId,
        String username,
        String name,
        String profileImageUrl,
        long followerCount,
        long followingCount,
        long postCount,
        boolean isFollowing,
        boolean isCurrentUser
) {


    public static MemberProfileResponse of(
            Member member,
            long followerCount,
            long followingCount,
            long postCount,
            boolean isFollowing,
            boolean isCurrentUser
    ) {
        return new MemberProfileResponse(
                member.getId(),
                member.getUsername(),
                member.getName(),
                member.getProfileImageUrl(),
                followerCount,
                followingCount,
                postCount,
                isFollowing,
                isCurrentUser
        );
    }

    /**
     * 캐시 스냅샷({@link ProfileStats}) 과 viewer 의존 필드를 합쳐 응답으로 만든다.
     *
     * <p>캐시에 viewer 의존 필드를 담지 않기 위한 분리 설계의 일부.
     * 자세한 배경은 {@link ProfileStats} 의 클래스 javadoc 참조.</p>
     */
    public static MemberProfileResponse of(
            ProfileStats stats,
            boolean isFollowing,
            boolean isCurrentUser
    ) {
        return new MemberProfileResponse(
                stats.memberId(),
                stats.username(),
                stats.name(),
                stats.profileImageUrl(),
                stats.followerCount(),
                stats.followingCount(),
                stats.postCount(),
                isFollowing,
                isCurrentUser
        );
    }
}
