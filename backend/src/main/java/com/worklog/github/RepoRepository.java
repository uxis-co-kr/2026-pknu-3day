package com.worklog.github;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepoRepository extends JpaRepository<Repo, Long> {

    Optional<Repo> findByFullName(String fullName);

    boolean existsByFullName(String fullName);

    List<Repo> findAllByOrderByFullNameAsc();

    /**
     * 수집기는 트랜잭션 밖에서 돌기 때문에 등록자를 지연 로딩할 수 없다.
     * 토큰을 꺼내야 하므로 함께 읽어 온다.
     */
    @Query("select r from Repo r left join fetch r.registeredBy where r.id = :id")
    Optional<Repo> findWithRegistrant(@Param("id") Long id);
}
