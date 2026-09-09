package com.worklog.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiKeyServiceTest {

    private final ApiKeyService service = new ApiKeyService(null, null);

    @Test
    @DisplayName("발급한 키는 wl_ 로 시작하고 32바이트 난수를 담는다")
    void generatesPrefixedKey() {
        String key = service.generate();

        assertThat(key).startsWith(ApiKeyService.PREFIX);
        String body = key.substring(ApiKeyService.PREFIX.length());
        assertThat(Base64.getUrlDecoder().decode(body)).hasSize(32);
    }

    @Test
    @DisplayName("매번 다른 키가 나온다")
    void generatesUniqueKeys() {
        assertThat(service.generate()).isNotEqualTo(service.generate());
    }

    @Test
    @DisplayName("해시는 같은 키에 대해 같고, 다른 키에 대해 다르다")
    void hashesDeterministically() {
        String key = service.generate();

        assertThat(ApiKeyService.hash(key)).isEqualTo(ApiKeyService.hash(key));
        assertThat(ApiKeyService.hash(key)).isNotEqualTo(ApiKeyService.hash(service.generate()));
    }

    @Test
    @DisplayName("해시는 SHA-256 hex 64자이고 평문을 담지 않는다")
    void hashHidesPlaintext() {
        String key = service.generate();
        String hash = ApiKeyService.hash(key);

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}").doesNotContain(key);
    }
}
