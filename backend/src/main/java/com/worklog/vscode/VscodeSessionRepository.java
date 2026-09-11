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

    /**
     * 기간 조회 — VSCode 내역을 달 단위로 본다 (BACKLOG2 §2-3).
     *
     * <p>하루씩만 볼 수 있으면 지난주에 무엇을 했는지 보려고 날짜 선택기를 일곱 번 눌러야
     * 한다. 업무 일지 목록({@code GET /drafts?from&to})과 같은 방식으로 맞춘다.
     *
     * <p>같은 날 안에서는 늦게 보고한 것이 위로 온다 — 하루 조회와 같은 순서다.
     */
    @Query(
            """
            select s from VscodeSession s
            left join fetch s.user
            left join fetch s.repo
            where s.workDate between :from and :to
              and (:userId is null or s.user.id = :userId)
            order by s.workDate desc, s.reportedAt desc
            """)
    List<VscodeSession> findBetween(
            @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("userId") Long userId);

    // ---- 통계·초안 생성이 읽는 집계 (담당자 2) ----

    long countByWorkDate(LocalDate workDate);

    List<VscodeSession> findByUserIdAndWorkDate(Long userId, LocalDate workDate);

    @Query("select s.user.id, count(s) from VscodeSession s where s.workDate = :workDate group by s.user.id")
    List<Object[]> countByUser(@Param("workDate") LocalDate workDate);

    /** 관리자 콘솔 — 사용자별 전체 세션 수. 확장을 실제로 쓰고 있는지 판단한다. */
    @Query("select s.user.id, count(s) from VscodeSession s group by s.user.id")
    List<Object[]> countAllByUser();

    /**
     * 관리자 콘솔 — (사용자, 리포)별 세션 수.
     *
     * <p>리포를 <b>등록하지 않은</b> 사람도 그 리포에서 일한다 — 등록은 한 사람만 할 수 있다.
     * 등록만 보면 그 사람은 아무 리포에도 붙어 있지 않은 것처럼 보인다 (BACKLOG2 §2-4).
     * 연결을 못 찾은 세션(repo_id NULL)은 뺀다.
     */
    @Query("select s.user.id, s.repo.id, count(s) from VscodeSession s"
            + " where s.user is not null and s.repo is not null group by s.user.id, s.repo.id")
    List<Object[]> countByUserAndRepo();

    /**
     * 마지막 커밋이 오래된 세션 수 (PRD 7. staleSessions, F7-2 리마인드와 같은 기준).
     * 커밋 이력이 아예 없는 세션도 방치로 본다.
     */
    @Query("select count(s) from VscodeSession s"
            + " where s.workDate = :workDate and (s.lastCommitAt is null or s.lastCommitAt < :threshold)")
    long countStale(
            @Param("workDate") LocalDate workDate, @Param("threshold") OffsetDateTime threshold);

    /**
     * 리마인드 대상 후보 (F7-2). 줄 수 조건은 JSONB 안을 봐야 하므로 SQL 로 걸지 않고
     * 그날 세션을 모두 읽어 {@link com.worklog.notify.RemindPolicy} 로 판정한다.
     * 하루치 세션은 팀 규모에서 수십 건이라 이 편이 단순하다.
     *
     * <p>알림 문구에 리포·브랜치·사용자가 들어가므로 연관을 함께 읽는다.
     */
    @Query("select s from VscodeSession s"
            + " left join fetch s.user left join fetch s.repo"
            + " where s.workDate = :workDate")
    List<VscodeSession> findAllForRemind(@Param("workDate") LocalDate workDate);
}
