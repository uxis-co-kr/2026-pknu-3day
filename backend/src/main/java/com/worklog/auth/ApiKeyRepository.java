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
}
