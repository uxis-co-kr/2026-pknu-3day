package com.worklog.github;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepoRepository extends JpaRepository<Repo, Long> {

    Optional<Repo> findByFullName(String fullName);

    boolean existsByFullName(String fullName);

    List<Repo> findAllByOrderByFullNameAsc();
}
