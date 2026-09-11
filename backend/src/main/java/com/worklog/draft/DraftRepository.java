package com.worklog.draft;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 초안 저장소. 생성(담당자 2)과 조회·수정·확정(담당자 1)이 함께 쓴다 — 메서드 추가는 자유롭게,
 * 기존 메서드 시그니처 변경은 합의 후에 (PRD 2).
 */
public interface DraftRepository extends JpaRepository<Draft, Long> {

    /** 하루치의 최신 버전. 주간·저장소별은 같은 날 시작해도 따로 센다 (V15). */
    @Query("select coalesce(max(d.version), 0) from Draft d"
            + " where d.user.id = :userId and d.workDate = :workDate and d.kind = com.worklog.draft.DraftKind.DAILY")
    int findMaxVersion(@Param("userId") Long userId, @Param("workDate") LocalDate workDate);

    /**
     * 같은 (사용자, 종류, 시작일, 저장소) 의 다음 버전 (V15).
     *
     * <p>하루치와 주간이 같은 날 시작해도 버전이 따로 올라간다 — 유니크 인덱스가 종류까지 본다.
     */
    @Query("select coalesce(max(d.version), 0) + 1 from Draft d"
            + " where d.user.id = :userId and d.kind = :kind and d.workDate = :workDate"
            + " and ((:repoId is null and d.repo is null) or d.repo.id = :repoId)")
    int nextVersion(
            @Param("userId") Long userId,
            @Param("kind") DraftKind kind,
            @Param("workDate") LocalDate workDate,
            @Param("repoId") Long repoId);

    boolean existsByUserIdAndWorkDateAndStatus(Long userId, LocalDate workDate, DraftStatus status);

    /**
     * 그날 사용자가 저장한 적 있는 <b>하루치</b> 일지가 있는지 (V6). 스케줄러가 건너뛰는 기준이다.
     * 주간·저장소별이 같은 날 시작했다고 하루치 자동 생성이 멈추면 안 된다 (V15).
     */
    boolean existsByUserIdAndWorkDateAndKindAndUserEditedTrue(
            Long userId, LocalDate workDate, DraftKind kind);

    default boolean existsByUserIdAndWorkDateAndUserEditedTrue(Long userId, LocalDate workDate) {
        return existsByUserIdAndWorkDateAndKindAndUserEditedTrue(userId, workDate, DraftKind.DAILY);
    }

    /** 같은 (사용자, 날짜) 의 최신 <b>하루치</b>. 채팅 답과 홈 카드가 쓴다. */
    default Optional<Draft> findFirstByUserIdAndWorkDateOrderByVersionDesc(Long userId, LocalDate workDate) {
        return findFirstByUserIdAndWorkDateAndKindOrderByVersionDesc(userId, workDate, DraftKind.DAILY);
    }

    Optional<Draft> findFirstByUserIdAndWorkDateAndKindOrderByVersionDesc(
            Long userId, LocalDate workDate, DraftKind kind);

    @Query("select d from Draft d join fetch d.user where d.workDate = :workDate"
            + " and d.version = (select max(d2.version) from Draft d2"
            + "                  where d2.user.id = d.user.id and d2.workDate = d.workDate"
            + "                    and d2.kind = d.kind"
            + "                    and ((d2.repo is null and d.repo is null) or d2.repo.id = d.repo.id))")
    List<Draft> findLatestByWorkDate(@Param("workDate") LocalDate workDate);

    /**
     * 기간 안의 (사용자, 날짜) 별 최신 버전. 최근 날짜가 먼저 온다.
     *
     * <p>재생성은 version 을 올리므로 같은 (user, date) 에 여러 행이 있다. 그중 최신만 준다.
     *
     * <p>두 곳이 쓴다 — 업무 일지 목록 화면(하루씩이 아니라 여러 날)과 인원별 통계(날짜마다
     * 배지). 둘 다 {@code draft.getUser()} 를 읽으므로 {@code join fetch} 로 N+1 을 막는다.
     */
    @Query("select d from Draft d join fetch d.user"
            + " where d.workDate between :from and :to"
            + " and d.version = (select max(d2.version) from Draft d2"
            + "                  where d2.user.id = d.user.id and d2.workDate = d.workDate"
            + "                    and d2.kind = d.kind"
            + "                    and ((d2.repo is null and d.repo is null) or d2.repo.id = d.repo.id))"
            + " order by d.workDate desc, d.user.id")
    List<Draft> findLatestBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
