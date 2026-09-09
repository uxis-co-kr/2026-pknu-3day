package com.worklog.vscode;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 세션 수신·조회 API 는 담당자 1의 영역이다. 여기 있는 메서드는 통계(PRD 7. /stats/daily)와
 * 초안 생성(F3)이 읽기 위한 것이고, 담당자 1이 필요한 메서드를 자유롭게 추가하면 된다.
 */
public interface VscodeSessionRepository extends JpaRepository<VscodeSession, Long> {

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
