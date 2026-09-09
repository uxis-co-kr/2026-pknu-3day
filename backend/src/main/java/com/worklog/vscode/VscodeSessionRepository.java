package com.worklog.vscode;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VscodeSessionRepository extends JpaRepository<VscodeSession, Long> {

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
}
