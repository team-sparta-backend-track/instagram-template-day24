package com.example.instagramclone.domain.member.application;

import com.example.instagramclone.domain.follow.domain.FollowRepository;
import com.example.instagramclone.domain.member.api.MemberProfileResponse;
import com.example.instagramclone.domain.member.api.ProfileStats;
import com.example.instagramclone.domain.member.domain.Member;
import com.example.instagramclone.domain.member.domain.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberProfileServiceTest {

    @Mock
    private MemberService memberService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private FollowRepository followRepository;

    @InjectMocks
    private MemberProfileService memberProfileService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // self-invocation 패턴: @Lazy @Autowired self 필드는 @InjectMocks 로 주입되지 않음
        ReflectionTestUtils.setField(memberProfileService, "self", memberProfileService);
    }

    private Member buildMockMember(Long id, String username) {
        Member member = Member.builder()
                .username(username)
                .password("encoded_pw")
                .email(username + "@test.com")
                .name("테스트 유저")
                .profileImageUrl("/profiles/" + username + ".jpg")
                .build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private ProfileStats buildStats(Member member, long followers, long followings, long posts) {
        return new ProfileStats(
                member.getId(),
                member.getUsername(),
                member.getName(),
                member.getProfileImageUrl(),
                followers,
                followings,
                posts
        );
    }

    @Nested
    @DisplayName("getProfileByUsername()")
    class GetProfileByUsername {

        @Test
        @DisplayName("성공 - 자기 자신의 프로필이면 isCurrentUser=true, isFollowing=false (팔로우 조회 생략)")
        void success_my_profile_returns_isCurrentUser_true_and_skips_follow_check() {
            Long loginMemberId = 1L;
            Member me = buildMockMember(1L, "me");

            given(memberService.findByUsername("me")).willReturn(me);
            given(memberRepository.getProfileStats(me.getId()))
                    .willReturn(buildStats(me, 10L, 20L, 30L));

            MemberProfileResponse response = memberProfileService.getProfileByUsername(loginMemberId, "me");

            assertThat(response.memberId()).isEqualTo(1L);
            assertThat(response.username()).isEqualTo("me");
            assertThat(response.followerCount()).isEqualTo(10L);
            assertThat(response.followingCount()).isEqualTo(20L);
            assertThat(response.postCount()).isEqualTo(30L);
            assertThat(response.isCurrentUser()).isTrue();
            assertThat(response.isFollowing()).isFalse();

            // 자기 자신은 isCurrentUser 만으로 끝, 팔로우 여부는 조회조차 하지 않음
            verify(followRepository, never()).existsByFromMemberAndToMember(any(), any());
        }

        @Test
        @DisplayName("성공 - 다른 유저 프로필 + 팔로우 중이면 isFollowing=true, isCurrentUser=false")
        void success_other_profile_following_true() {
            Long loginMemberId = 1L;
            Member loginRef = buildMockMember(1L, "viewer");
            Member targetMember = buildMockMember(2L, "target");

            given(memberService.findByUsername("target")).willReturn(targetMember);
            given(memberService.getReferenceById(loginMemberId)).willReturn(loginRef);
            given(memberRepository.getProfileStats(targetMember.getId()))
                    .willReturn(buildStats(targetMember, 11L, 22L, 33L));
            given(followRepository.existsByFromMemberAndToMember(loginRef, targetMember))
                    .willReturn(true);

            MemberProfileResponse response = memberProfileService.getProfileByUsername(loginMemberId, "target");

            assertThat(response.memberId()).isEqualTo(2L);
            assertThat(response.username()).isEqualTo("target");
            assertThat(response.profileImageUrl()).isEqualTo("/profiles/target.jpg");
            assertThat(response.followerCount()).isEqualTo(11L);
            assertThat(response.followingCount()).isEqualTo(22L);
            assertThat(response.postCount()).isEqualTo(33L);
            assertThat(response.isFollowing()).isTrue();
            assertThat(response.isCurrentUser()).isFalse();
        }

        @Test
        @DisplayName("성공 - 다른 유저 프로필 + 팔로우 안 함이면 isFollowing=false")
        void success_other_profile_following_false() {
            Long loginMemberId = 1L;
            Member loginRef = buildMockMember(1L, "viewer");
            Member targetMember = buildMockMember(2L, "target");

            given(memberService.findByUsername("target")).willReturn(targetMember);
            given(memberService.getReferenceById(loginMemberId)).willReturn(loginRef);
            given(memberRepository.getProfileStats(targetMember.getId()))
                    .willReturn(buildStats(targetMember, 1L, 2L, 3L));
            given(followRepository.existsByFromMemberAndToMember(loginRef, targetMember))
                    .willReturn(false);

            MemberProfileResponse response = memberProfileService.getProfileByUsername(loginMemberId, "target");

            assertThat(response.isFollowing()).isFalse();
            assertThat(response.isCurrentUser()).isFalse();
        }

        /**
         * <p><b>Day 17 캐시 함정 회귀 테스트.</b></p>
         *
         * <p>캐시에 들어가는 {@link ProfileStats} 를 두 viewer 가 동일하게 받더라도
         * (= Repository 가 동일한 stats 인스턴스를 반환하더라도),
         * 최종 응답의 {@code isCurrentUser} / {@code isFollowing} 은
         * 각 viewer 마다 독립적으로 계산되어야 한다.</p>
         *
         * <p>이 테스트가 깨지면 캐시 응답에 viewer 의존 필드가 다시 섞여 들어간 것이다.
         * Day 17 캐시 분리 설계가 무너진 것이므로 즉시 원인 추적 필요.</p>
         */
        @Test
        @DisplayName("회귀(Day 17) - 같은 ProfileStats 라도 viewer 가 다르면 isCurrentUser/isFollowing 이 다르게 계산된다")
        void regression_same_stats_but_viewer_dependent_fields_differ_per_viewer() {
            Member kuromi    = buildMockMember(1L, "kuromi");
            Member heartping = buildMockMember(5L, "heartping");

            // heartping 프로필의 캐시 스냅샷 — viewer-independent 필드만
            ProfileStats heartpingStats = buildStats(heartping, 100L, 200L, 5L);

            given(memberService.findByUsername("heartping")).willReturn(heartping);
            given(memberRepository.getProfileStats(heartping.getId())).willReturn(heartpingStats);

            // ── viewer 1: heartping 본인이 자기 프로필을 본다 ──────────────────
            MemberProfileResponse asOwner = memberProfileService.getProfileByUsername(heartping.getId(), "heartping");
            assertThat(asOwner.isCurrentUser()).isTrue();
            assertThat(asOwner.isFollowing()).isFalse();

            // ── viewer 2: kuromi 가 heartping 프로필을 본다 (캐시 hit 가정) ────
            given(memberService.getReferenceById(kuromi.getId())).willReturn(kuromi);
            given(followRepository.existsByFromMemberAndToMember(kuromi, heartping)).willReturn(false);

            MemberProfileResponse asVisitor = memberProfileService.getProfileByUsername(kuromi.getId(), "heartping");

            // 핵심: 같은 ProfileStats 를 받았어도 viewer 의존 필드는 viewer 기준으로 다시 계산되어야 한다
            assertThat(asVisitor.isCurrentUser()).isFalse();
            assertThat(asVisitor.isFollowing()).isFalse();

            // viewer-independent 필드는 두 응답에서 동일
            assertThat(asVisitor.followerCount()).isEqualTo(asOwner.followerCount());
            assertThat(asVisitor.followingCount()).isEqualTo(asOwner.followingCount());
            assertThat(asVisitor.postCount()).isEqualTo(asOwner.postCount());
        }
    }
}
