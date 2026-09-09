package com.worklog.auth;

import com.worklog.config.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * VS Code 확장·외부 API 용 개인 키 (PRD F5).
 *
 * <p>평문은 발급 응답에서 1회만 노출하고 DB 에는 SHA-256 해시만 남긴다. 키 자체가 32바이트
 * 난수라 사전 공격 대상이 아니므로 느린 해시(bcrypt 등)를 쓰지 않는다 — 매 요청 검증에 쓰이는
 * 값이라 상수 시간 해시 조회가 낫다.
 */
@Service
public class ApiKeyService {

    public static final String PREFIX = "wl_";
    private static final int RANDOM_BYTES = 32;

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();

    public ApiKeyService(ApiKeyRepository apiKeyRepository, UserRepository userRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.userRepository = userRepository;
    }

    /** 발급. 반환된 평문 키는 이 시점 이후 어디에서도 다시 얻을 수 없다. */
    @Transactional
    public Issued issue(Long userId, String label) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));

        String plain = generate();
        ApiKey apiKey = new ApiKey();
        apiKey.setUser(user);
        apiKey.setKeyHash(hash(plain));
        apiKey.setLabel(label);
        return new Issued(apiKeyRepository.save(apiKey), plain);
    }

    @Transactional(readOnly = true)
    public List<ApiKey> list(Long userId) {
        return apiKeyRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void revoke(Long userId, Long keyId) {
        ApiKey apiKey = apiKeyRepository
                .findByIdAndUserId(keyId, userId)
                // 남의 키인지 없는 키인지 구분해 알려주지 않는다.
                .orElseThrow(() -> ApiException.notFound("API_KEY_NOT_FOUND", "API 키를 찾을 수 없습니다."));
        apiKeyRepository.delete(apiKey);
    }

    /** X-Api-Key 검증. 일치하면 마지막 사용 시각을 갱신한다. */
    @Transactional
    public Optional<AuthenticatedUser> authenticate(String plainKey) {
        if (plainKey == null || plainKey.isBlank()) {
            return Optional.empty();
        }
        return apiKeyRepository.findByKeyHash(hash(plainKey.trim())).map(apiKey -> {
            apiKey.setLastUsedAt(OffsetDateTime.now());
            User user = apiKey.getUser();
            return new AuthenticatedUser(user.getId(), user.getLogin(), AuthMethod.API_KEY);
        });
    }

    String generate() {
        byte[] bytes = new byte[RANDOM_BYTES];
        random.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String plainKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(plainKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없다.", e);
        }
    }

    /** 저장된 키 + 이 응답에서만 볼 수 있는 평문. */
    public record Issued(ApiKey apiKey, String plainKey) {}
}
