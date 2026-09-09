package com.worklog.activity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityRepository
        extends JpaRepository<Activity, Long>, JpaSpecificationExecutor<Activity> {

    boolean existsByRepoIdAndTypeAndExternalId(Long repoId, ActivityType type, String externalId);

    /**
     * 수집기가 "이미 저장한 커밋"을 한 번에 읽어 상세 API 호출을 건너뛰는 데 쓴다
     * (GitHub rate limit 절약, PRD 11).
     */
    @Query("select a.externalId from Activity a where a.repo.id = :repoId and a.type = :type")
    List<String> findExternalIds(@Param("repoId") Long repoId, @Param("type") ActivityType type);

    /**
     * 요약 파이프라인이 집을 대상 (PRD F2).
     *
     * <p>최대 3회까지 재시도하므로 FAILED 라도 재시도 횟수가 남아 있으면 다시 집힌다.
     * 프롬프트에 리포 이름이 들어가므로 repo 를 함께 읽는다.
     */
    @Query("select a from Activity a join fetch a.repo"
            + " where a.summaryStatus <> com.worklog.activity.SummaryStatus.DONE"
            + " and a.summaryRetries < :maxRetries"
            + " order by a.occurredAt desc")
    List<Activity> findSummaryTargets(@Param("maxRetries") int maxRetries, Pageable pageable);

    /** 상세 조회 — 응답 매핑이 트랜잭션 밖이라 연관을 함께 읽는다. */
    @Query("select a from Activity a left join fetch a.repo left join fetch a.user where a.id = :id")
    Optional<Activity> findDetailById(@Param("id") Long id);

    /** 초안 생성 대상 (PRD F3) — 그날 그 사용자의 활동. */
    @Query("select a from Activity a join fetch a.repo"
            + " where a.user.id = :userId and a.occurredAt >= :start and a.occurredAt < :end"
            + " order by a.occurredAt asc")
    List<Activity> findForUserBetween(
            @Param("userId") Long userId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    /** 일별 통계 — 타입별 건수 (PRD 7. /stats/daily). */
    @Query("select a.type, count(a) from Activity a"
            + " where a.occurredAt >= :start and a.occurredAt < :end"
            + " group by a.type")
    List<Object[]> countByTypeBetween(
            @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    /** 일별 통계 — 사용자·타입별 건수. 미가입 활동(user null)은 제외된다. */
    @Query("select a.user.id, a.type, count(a) from Activity a"
            + " where a.occurredAt >= :start and a.occurredAt < :end and a.user is not null"
            + " group by a.user.id, a.type")
    List<Object[]> countByUserAndTypeBetween(
            @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    /** 리포 관리 화면의 "오늘 활동 수" (PRD 7. GET /repos todayActivityCount). */
    @Query("select a.repo.id, count(a) from Activity a"
            + " where a.type = :type and a.occurredAt >= :start and a.occurredAt < :end"
            + " group by a.repo.id")
    List<Object[]> countByRepoBetween(
            @Param("type") ActivityType type,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    /** 활동이 있는 사용자 id — 18:00 초안 스케줄러가 대상을 고를 때 쓴다. */
    @Query("select distinct a.user.id from Activity a"
            + " where a.user is not null and a.occurredAt >= :start and a.occurredAt < :end")
    List<Long> findUserIdsWithActivityBetween(
            @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);
}
