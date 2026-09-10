package com.worklog.auth;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    /** X-Api-Key 검증 — 평문은 저장하지 않으므로 해시로 찾는다. */
    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 남의 키를 지우지 못하게 소유자까지 함께 건다. */
    Optional<ApiKey> findByIdAndUserId(Long id, Long userId);

    /**
     * 관리자 콘솔 — VS Code 연동 여부 (TODO_0910 §1-3).
     * 확장은 API Key 로만 붙으므로, 키가 있으면 연동 수단은 갖춘 것이다.
     */
    @org.springframework.data.jpa.repository.Query(
            "select k.user.id, count(k) from ApiKey k group by k.user.id")
    List<Object[]> countByUser();
}
