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

    @Query("select coalesce(max(d.version), 0) from Draft d"
            + " where d.user.id = :userId and d.workDate = :workDate")
    int findMaxVersion(@Param("userId") Long userId, @Param("workDate") LocalDate workDate);

    boolean existsByUserIdAndWorkDateAndStatus(Long userId, LocalDate workDate, DraftStatus status);

    /** 같은 (사용자, 날짜) 의 최신 버전. */
    Optional<Draft> findFirstByUserIdAndWorkDateOrderByVersionDesc(Long userId, LocalDate workDate);

    @Query("select d from Draft d join fetch d.user where d.workDate = :workDate"
            + " and d.version = (select max(d2.version) from Draft d2"
            + "                  where d2.user.id = d.user.id and d2.workDate = d.workDate)")
    List<Draft> findLatestByWorkDate(@Param("workDate") LocalDate workDate);
}
