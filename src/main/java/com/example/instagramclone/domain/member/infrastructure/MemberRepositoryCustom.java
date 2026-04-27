package com.example.instagramclone.domain.member.infrastructure;

import com.example.instagramclone.domain.member.api.MemberSummary;
import com.example.instagramclone.domain.member.api.ProfileStats;
import com.example.instagramclone.domain.member.domain.Member;
import org.springframework.data.domain.Slice;

import java.util.List;

/**
 * MemberRepository에 QueryDSL 기반 커스텀 쿼리를 추가하기 위한 인터페이스.
 *
 * [커스텀 리포지토리 패턴]
 * Spring Data JPA(save, findById 등)와 QueryDSL 커스텀 쿼리를
 * 하나의 MemberRepository로 합치기 위한 중간 인터페이스입니다.
 *
 * MemberRepository extends JpaRepository<Member, Long>, MemberRepositoryCustom
 */
public interface MemberRepositoryCustom {

    /**
     * username에 keyword가 포함된 회원을 대소문자 구분 없이 검색합니다.
     * SQL: WHERE LOWER(username) LIKE LOWER('%keyword%')
     */
    List<Member> searchByUsername(String keyword);

    /**
     * Day 15 Live Coding: 프로필 헤더 통계 조회 (캐시 스냅샷).
     *
     * <p><b>Day 17 캐시 분리</b><br>
     * 이 쿼리는 <b>프로필 주인 기준</b> 의 viewer-independent 필드만 반환한다.
     * {@code isFollowing} / {@code isCurrentUser} 처럼 viewer 에 따라 달라지는
     * 값은 호출부에서 별도로 계산해 합성한다.</p>
     *
     * <p>이 분리가 중요한 이유는 {@link ProfileStats} javadoc 참조.
     * 단일 키 {@code #targetMemberId} 캐시 + viewer 의존 필드가 한 응답에 섞이면
     * 캐시 hit 시 서로 다른 viewer 가 같은 값을 받게 된다.</p>
     */
    ProfileStats getProfileStats(Long targetMemberId);

    /** 커서 기반 유저 검색 — MemberSummary DTO projection */
    Slice<MemberSummary> searchByUsernameByCursor(String keyword, Long cursorId, int size);
}
