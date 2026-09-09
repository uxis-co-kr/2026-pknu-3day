package com.worklog.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** OAuth 콜백에서 UPSERT 기준으로 쓴다 (PRD F5). login 은 바뀔 수 있으므로 github_id 로 찾는다. */
    Optional<User> findByGithubId(Long githubId);

    /** 커밋 author login → 가입 사용자 매핑 (PRD F1-5). */
    Optional<User> findByLogin(String login);
}
