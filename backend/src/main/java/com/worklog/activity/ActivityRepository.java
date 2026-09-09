package com.worklog.activity;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

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
}
