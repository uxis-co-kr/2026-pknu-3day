package com.worklog.activity;

import java.util.List;
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
}
