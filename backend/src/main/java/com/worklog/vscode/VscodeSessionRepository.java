package com.worklog.vscode;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 세션 수신·조회 API(PRD 7. /vscode/sessions)는 담당자 1의 영역이고,
 * 아래쪽 집계 메서드는 담당자 2의 통계(/stats/daily)와 초안 생성(F3)이 읽는다.
 * 1일차·2일차에 양쪽이 각각 만든 것을 합쳤다.
 */
public interface VscodeSessionRepository extends JpaRepository<VscodeSession, Long> {

    // ---- 수신·조회 (담당자 1) ----

    /** UPSERT 키 — vscode_sessions 의 uq_vscode_sessions 와 같은 조합이다 (PRD F6). */
    Optional<VscodeSession> findByUserIdAndRemoteUrlAndBranchAndWorkDate(
            Long userId, String remoteUrl, String branch, LocalDate workDate);

    /**
     * user 와 repo 는 LAZY 라 응답을 만들 때 지연 로딩이 걸린다. 조회 시점에 함께 읽어 온다
     * (담당자 2가 GET /repos 에서 겪은 LazyInitializationException 과 같은 문제).
     */
    @Query(
            """
            select s from VscodeSession s
            left join fetch s.user
            left join fetch s.repo
            where s.workDate = :workDate
              and (:userId is null or s.user.id = :userId)
            order by s.reportedAt desc
            """)
    List<VscodeSession> findForDay(@Param("workDate") LocalDate workDate, @Param("userId") Long userId);

    // ---- 통계·초안 생성이 읽는 집계 (담당자 2) ----

    long countByWorkDate(LocalDate workDate);

    List<VscodeSession> findByUserIdAndWorkDate(Long userId, LocalDate workDate);

    @Query("select s.user.id, count(s) from VscodeSession s where s.workDate = :workDate group by s.user.id")
    List<Object[]> countByUser(@Param("workDate") LocalDate workDate);

    /**
     * 마지막 커밋이 오래된 세션 수 (PRD 7. staleSessions, F7-2 리마인드와 같은 기준).
     * 커밋 이력이 아예 없는 세션도 방치로 본다.
     */
    @Query("select count(s) from VscodeSession s"
            + " where s.workDate = :workDate and (s.lastCommitAt is null or s.lastCommitAt < :threshold)")
    long countStale(
            @Param("workDate") LocalDate workDate, @Param("threshold") OffsetDateTime threshold);
}
