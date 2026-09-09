package com.worklog.auth.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AesEncryptorTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final AesEncryptor encryptor = new AesEncryptor(KEY);

    @Test
    @DisplayName("암호화한 값을 그대로 복호화한다")
    void roundTrip() {
        String token = "gho_16charactersandmore0123456789";

        assertThat(encryptor.decrypt(encryptor.encrypt(token))).isEqualTo(token);
    }

    @Test
    @DisplayName("같은 평문도 매번 다른 암호문이 된다 — IV 를 새로 뽑기 때문")
    void randomIv() {
        String first = encryptor.encrypt("same");
        String second = encryptor.encrypt("same");

        assertThat(first).isNotEqualTo(second);
        assertThat(encryptor.decrypt(first)).isEqualTo(encryptor.decrypt(second)).isEqualTo("same");
    }

    @Test
    @DisplayName("암호문에 평문이 남지 않는다")
    void doesNotLeakPlaintext() {
        assertThat(encryptor.encrypt("gho_secret")).doesNotContain("gho_secret");
    }

    @Test
    @DisplayName("null 은 그대로 통과시킨다 — 토큰이 없는 사용자")
    void passesNullThrough() {
        assertThat(encryptor.encrypt(null)).isNull();
        assertThat(encryptor.decrypt(null)).isNull();
    }

    @Test
    @DisplayName("다른 키로는 복호화되지 않는다")
    void failsWithWrongKey() {
        byte[] other = new byte[32];
        other[0] = 1;
        AesEncryptor another = new AesEncryptor(Base64.getEncoder().encodeToString(other));

        assertThatThrownBy(() -> another.decrypt(encryptor.encrypt("secret")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("키 길이가 32바이트가 아니면 기동에 실패한다")
    void rejectsShortKey() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new AesEncryptor(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트");
        assertThatThrownBy(() -> new AesEncryptor("  ")).isInstanceOf(IllegalStateException.class);
    }
}
