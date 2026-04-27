package com.example.instagramclone.domain.member.application;

import com.example.instagramclone.core.constant.CacheNames;
import com.example.instagramclone.domain.follow.domain.FollowRepository;
import com.example.instagramclone.domain.member.api.MemberProfileResponse;
import com.example.instagramclone.domain.member.api.ProfileStats;
import com.example.instagramclone.domain.member.domain.Member;
import com.example.instagramclone.domain.member.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 조회 전용 서비스.
 *
 * <p>프로필 헤더 응답({@link MemberProfileResponse}) 은 두 종류의 필드로 나뉜다.
 * <ul>
 *   <li><b>viewer-independent</b> — 팔로워/팔로잉/게시물 수 등 프로필 주인 기준 값</li>
 *   <li><b>viewer-dependent</b>   — {@code isFollowing}, {@code isCurrentUser}
 *       (보는 사람이 누구냐에 따라 달라지는 값)</li>
 * </ul>
 * 이 둘을 한 응답에 같이 넣고 {@code #targetMemberId} 단일 키로 캐싱하면,
 * 서로 다른 viewer 가 같은 캐시 엔트리를 받게 되어 다음과 같은 사고가 난다.
 * <pre>
 *   T1) heartping 이 자기 프로필 진입 → isCurrentUser=true 로 캐시 저장
 *   T2) kuromi 가 /heartping 진입    → 캐시 hit → isCurrentUser=true 그대로 받음 ❌
 * </pre>
 *
 * <h2>Day 17 캐시 분리 설계</h2>
 *
 * <h3>캐시 대상: viewer-independent 필드만 ({@link ProfileStats})</h3>
 * <p>{@link #getProfileStatsById(Long)} 가 캐시 메서드다.
 * 키는 {@code targetMemberId} 단일 키 — FollowService·PostService 의
 * {@code @CacheEvict(key = "#loginMemberId" or "#targetMemberId")} 와 그대로 호환된다.</p>
 *
 * <h3>viewer 의존 필드는 매번 합성</h3>
 * <p>{@link #getProfileByUsername(Long, String)} 진입점에서
 * {@code isFollowing} 은 {@link FollowRepository#existsByFromMemberAndToMember(Member, Member)} 1회 조회,
 * {@code isCurrentUser} 는 ID 비교 1줄로 계산해 {@link MemberProfileResponse} 에 합성한다.
 * 캐시 hit 가 나도 viewer 마다 결과가 항상 정확하다.</p>
 *
 * <h3>self-invocation 함정</h3>
 * <p>{@code getProfileByUsername} 이 같은 빈의 {@code getProfileStatsById} 를 직접 호출하면
 * Spring AOP 프록시를 우회해 {@code @Cacheable} 이 동작하지 않는다.
 * → {@link #self} self-주입으로 해결한다 (라이브에서 시연).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberProfileService {

    private final MemberService memberService;
    private final MemberRepository memberRepository;
    private final FollowRepository followRepository;

    // ================================================================
    // TODO [Day 17 Live - Step 4-A] self-invocation 함정 시연
    //
    // 1단계 (일부러 잘못 작성):
    //   getProfileByUsername 에서 아래처럼 this.getProfileStatsById() 를 직접 호출한다.
    //   → 캐시가 전혀 동작하지 않는다. (프록시 우회)
    //
    // 2단계 (원인 파악):
    //   @Cacheable 은 Spring AOP 프록시를 통해 동작한다.
    //   같은 클래스 내 직접 호출은 프록시를 거치지 않아 애너테이션이 무시된다.
    //
    // 3단계 (수정):
    //   self 필드를 통해 프록시 빈을 거쳐 호출한다. (아래 @Lazy @Autowired 참고)
    // ================================================================

    /**
     * self-invocation 해결용 self 주입.
     *
     * <p>{@code @Lazy}: 순환 참조 방지 — 빈이 완전히 초기화된 후에 주입된다.<br>
     * {@code @Autowired}: {@code @RequiredArgsConstructor} 는 {@code final} 필드만 다루므로,
     * non-final 인 self 는 별도 필드 주입으로 받는다.</p>
     */
    @Lazy
    @Autowired
    private MemberProfileService self;

    /**
     * username 기반 프로필 조회 (공개 진입점).
     *
     * <p>흐름:
     * <ol>
     *   <li>username → targetMember (존재 여부 검증 포함)</li>
     *   <li>self 를 통해 캐시된 {@link ProfileStats} 조회 (프로필 주인 기준 카운트)</li>
     *   <li>viewer 의존 필드({@code isFollowing}, {@code isCurrentUser}) 별도 계산</li>
     *   <li>둘을 합쳐 최종 응답 생성</li>
     * </ol>
     *
     * <p>self 호출이 아니면 {@code @Cacheable} 이 동작하지 않으니 주의.</p>
     */
    public MemberProfileResponse getProfileByUsername(Long loginMemberId, String username) {
        // 1. username → targetMember (존재 여부 검증 포함)
        Member targetMember = memberService.findByUsername(username);

        // 2. 프록시 빈(self)을 통해 호출해야 @Cacheable 이 동작한다.
        //    this.getProfileStatsById(...) ← X (프록시 우회, 캐시 미동작)
        //    self.getProfileStatsById(...) ← O (프록시 경유, 캐시 동작)
        ProfileStats stats = self.getProfileStatsById(targetMember.getId());

        // 3. viewer 의존 필드는 매번 직접 계산 — 캐시에 들어가지 않는다.
        boolean isCurrentUser = targetMember.getId().equals(loginMemberId);
        boolean isFollowing = !isCurrentUser
                && followRepository.existsByFromMemberAndToMember(
                        memberService.getReferenceById(loginMemberId),
                        targetMember
                );

        // 4. 합성
        return MemberProfileResponse.of(stats, isFollowing, isCurrentUser);
    }

    /**
     * targetMemberId 기반 프로필 통계 조회 (캐시 대상 메서드).
     *
     * <p>반환 타입이 {@link ProfileStats} 인 점이 핵심이다.
     * viewer 의존 필드는 들어 있지 않으므로, 단일 키 {@code #targetMemberId}
     * 캐시에 그대로 들어가도 다른 viewer 에게 노출되어 문제를 일으키지 않는다.</p>
     *
     * <p>직접 호출 금지 — 반드시 {@code self.getProfileStatsById()} 또는 외부 빈에서 호출.</p>
     *
     * <p><b>무효화 시점</b> (캐시 키 = {@code targetMemberId})
     * <ul>
     *   <li>팔로우 / 언팔로우: FollowService → loginMemberId, targetMemberId 각각 evict
     *       (양쪽의 followerCount / followingCount 가 동시에 바뀌므로)</li>
     *   <li>게시물 작성: PostService → loginMemberId(= 작성자) evict (postCount 증가)</li>
     * </ul>
     */
    @Cacheable(value = CacheNames.PROFILE_STATS, key = "#targetMemberId")
    public ProfileStats getProfileStatsById(Long targetMemberId) {
        return memberRepository.getProfileStats(targetMemberId);
    }
}
