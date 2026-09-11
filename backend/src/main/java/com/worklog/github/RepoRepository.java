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

    /** 내가 등록한 리포만 (9/10 결정). 남이 등록한 것은 그 사람의 목록에만 있다. */
    @Query("select r from Repo r left join fetch r.registeredBy"
            + " where r.registeredBy.id = :userId order by r.fullName asc")
    List<Repo> findMineWithRegistrant(@Param("userId") Long userId);

    @Query("select r from Repo r left join fetch r.registeredBy where r.id = :id")
    Optional<Repo> findWithRegistrant(@Param("id") Long id);

    /**
     * 등록된 리포 이름 전부. <b>누가 등록했든</b> 상관없다 — 확장이 "이 폴더를 서버가 아는가"
     * 를 가리는 데 쓴다 (BACKLOG2 §2-4). 행을 통째로 읽을 이유가 없어 이름만 가져온다.
     */
    @Query("select r.fullName from Repo r order by r.fullName asc")
    List<String> findAllFullNames();
}
