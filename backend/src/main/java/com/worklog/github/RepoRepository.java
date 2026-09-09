package com.worklog.github;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepoRepository extends JpaRepository<Repo, Long> {

    Optional<Repo> findByFullName(String fullName);

    boolean existsByFullName(String fullName);

    /**
     * 등록자는 LAZY 라, 트랜잭션 밖(컨트롤러의 응답 매핑, 트랜잭션 없이 도는 수집기)에서
     * 건드리면 LazyInitializationException 이 난다. 두 경로 모두 함께 읽어 온다.
     */
    @Query("select r from Repo r left join fetch r.registeredBy order by r.fullName asc")
    List<Repo> findAllWithRegistrant();

    @Query("select r from Repo r left join fetch r.registeredBy where r.id = :id")
    Optional<Repo> findWithRegistrant(@Param("id") Long id);
}
