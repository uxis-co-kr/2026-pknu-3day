package com.worklog.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 자체 로그인 비밀번호 해시 (TODO_0910 §1-1).
 *
 * <p>사람이 고른 비밀번호는 API Key 와 달리 엔트로피가 낮아 사전 공격 대상이다. 그래서
 * SHA-256 한 번이 아니라 <b>계정마다 다른 소금 + 반복 해싱</b>을 쓴다. 저장 형식은
 * {@code sha256$반복수$소금$해시} 로, 나중에 반복수를 올려도 옛 해시를 그대로 읽을 수 있다.
 *
 * <p>bcrypt/argon2 를 쓰려면 의존성이 하나 더 늘어난다. 사내 도구에 계정 수십 개 규모라
 * 표준 라이브러리로 충분하다고 보고 여기서 끝냈다.
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "sha256";
    private static final int ITERATIONS = 100_000;
    private static final int SALT_BYTES = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {}

    public static String hash(String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        String saltHex = HexFormat.of().formatHex(salt);
        return "%s$%d$%s$%s"
                .formatted(ALGORITHM, ITERATIONS, saltHex, derive(rawPassword, saltHex, ITERATIONS));
    }

    /** 저장된 해시와 맞는지. 형식이 깨졌거나 비어 있으면 false — 예외를 던지지 않는다. */
    public static boolean matches(String rawPassword, String stored) {
        if (rawPassword == null || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !ALGORITHM.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            String expected = derive(rawPassword, parts[2], iterations);
            // 길이가 같은 hex 문자열이라 상수 시간 비교를 쓴다.
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    parts[3].getBytes(StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String derive(String rawPassword, String saltHex, int iterations) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] current = (saltHex + rawPassword).getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < iterations; i++) {
                current = digest.digest(current);
            }
            return Base64.getEncoder().encodeToString(current);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없다.", e);
        }
    }
}
