package com.worklog.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    @Test
    @DisplayName("해시한 비밀번호를 다시 맞춰 본다")
    void roundTrip() {
        String hash = PasswordHasher.hash("admin1234");

        assertThat(PasswordHasher.matches("admin1234", hash)).isTrue();
        assertThat(PasswordHasher.matches("admin12345", hash)).isFalse();
        assertThat(PasswordHasher.matches("", hash)).isFalse();
    }

    @Test
    @DisplayName("같은 비밀번호도 매번 다른 해시가 된다 — 계정마다 소금이 다르다")
    void saltedPerCall() {
        String a = PasswordHasher.hash("same");
        String b = PasswordHasher.hash("same");

        assertThat(a).isNotEqualTo(b);
        assertThat(PasswordHasher.matches("same", a)).isTrue();
        assertThat(PasswordHasher.matches("same", b)).isTrue();
    }

    @Test
    @DisplayName("해시에 평문이 남지 않는다")
    void doesNotLeakPlaintext() {
        assertThat(PasswordHasher.hash("hunter2")).doesNotContain("hunter2");
    }

    @Test
    @DisplayName("반복수를 형식에 담아 나중에 올려도 옛 해시를 읽는다")
    void storesIterationCount() {
        String hash = PasswordHasher.hash("pw");
        String[] parts = hash.split("\\$");

        assertThat(parts).hasSize(4);
        assertThat(parts[0]).isEqualTo("sha256");
        assertThat(Integer.parseInt(parts[1])).isPositive();
    }

    @Test
    @DisplayName("형식이 깨진 값은 예외 없이 false")
    void toleratesBrokenInput() {
        assertThat(PasswordHasher.matches("pw", null)).isFalse();
        assertThat(PasswordHasher.matches(null, "sha256$1$aa$bb")).isFalse();
        assertThat(PasswordHasher.matches("pw", "그냥문자열")).isFalse();
        assertThat(PasswordHasher.matches("pw", "sha256$abc$aa$bb")).isFalse();
        assertThat(PasswordHasher.matches("pw", "md5$1$aa$bb")).isFalse();
    }
}
