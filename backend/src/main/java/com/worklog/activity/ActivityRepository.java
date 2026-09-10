package com.worklog.activity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * 뒤늦게 가입한 사용자의 기존 활동을 소급해서 연결한다 (PRD F1-5).
     *
     * <p>매핑은 수집 시점에만 일어나므로, 커밋이 먼저 쌓이고 그 뒤에 로그인하면 그 활동들은
     * external_login 만 남은 채 영영 사용자에 붙지 않는다. 재수집으로도 안 고쳐진다 —
     * 이미 저장한 sha 는 건너뛰기 때문이다. 로그인 시점에 한 번 이어 준다.
     *
     * @return 새로 연결된 활동 수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Activity a set a.user.id = :userId"
            + " where a.user is null and lower(a.externalLogin) = lower(:login)")
    int linkExistingActivities(@Param("userId") Long userId, @Param("login") String login);

    /**
     * 활동을 다른 사용자에게 넘긴다.
     *
     * <p>GitHub 로그인이 곧 회원가입이던 시절에 만들어진 계정에 활동이 묶여 있다. 그 사람이
     * 사원 번호로 로그인해 같은 GitHub 을 연결하면, 활동도 따라와야 한다 (TODO_0910 §1-1).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Activity a set a.user.id = :toUserId where a.user.id = :fromUserId")
    int reassignActivities(@Param("fromUserId") Long fromUserId, @Param("toUserId") Long toUserId);

    /** 관리자 콘솔 — (사용자, 리포)별 활동 수. 사원 행을 펼쳤을 때 리포마다 보여 준다. */
    @Query("select a.user.id, a.repo.id, count(a) from Activity a"
            + " where a.user is not null group by a.user.id, a.repo.id")
    List<Object[]> countByUserAndRepo();

    /** 관리자 콘솔 — 요약 상태별 건수. 요약이 밀려 있으면 초안이 부실해진다. */
    @Query("select a.summaryStatus, count(a) from Activity a group by a.summaryStatus")
    List<Object[]> countBySummaryStatus();

    /** 관리자 콘솔 — 사용자별 총 활동 수. */
    @Query("select a.user.id, count(a) from Activity a where a.user is not null group by a.user.id")
    List<Object[]> countAllByUser();

    /**
     * 관리자 콘솔 — 로그인한 적 없는 GitHub 계정별 활동 수와 마지막 활동 시각 (BACKLOG §3-4).
     */
    @Query("select a.externalLogin, count(a), max(a.occurredAt) from Activity a"
            + " where a.user is null and a.externalLogin is not null"
            + " group by a.externalLogin order by count(a) desc")
    List<Object[]> countUnclaimedContributors();

    /** 아직 어느 사용자에도 붙지 않은 활동 수 — 통계에서 총계와 사용자별 합의 차이를 설명한다. */
    @Query("select a.type, count(a) from Activity a"
            + " where a.user is null and a.occurredAt >= :start and a.occurredAt < :end"
            + " group by a.type")
    List<Object[]> countUnmappedByTypeBetween(
            @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    /**
     * 인원별 시계열 집계 (PRD 7. /stats/people).
     *
     * <p>KST 날짜로 묶어야 하므로 DB 에서 시간대를 변환한다. TIMESTAMPTZ 라 저장은 UTC 다.
     * 주 단위 묶기는 서비스에서 한다 — 날짜 단위 결과를 접는 편이 쿼리 하나로 끝나고 검증도 쉽다.
     *
     * @return [userId, KST 날짜, type, 건수]
     */
    @Query(value = "select a.user_id,"
            + "       (a.occurred_at at time zone 'Asia/Seoul')::date as d,"
            + "       a.type, count(*)"
            + " from activities a"
            + " where a.user_id is not null and a.occurred_at >= :start and a.occurred_at < :end"
            + " group by a.user_id, d, a.type",
            nativeQuery = true)
    List<Object[]> countByUserAndDateBetween(
            @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    /** 활동이 있는 사용자 id — 18:00 초안 스케줄러가 대상을 고를 때 쓴다. */
    @Query("select distinct a.user.id from Activity a"
            + " where a.user is not null and a.occurredAt >= :start and a.occurredAt < :end")
    List<Long> findUserIdsWithActivityBetween(
            @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);
}
