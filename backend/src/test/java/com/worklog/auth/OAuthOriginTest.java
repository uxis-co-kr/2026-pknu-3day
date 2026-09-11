package com.worklog.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.worklog.config.InternalNetwork;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 돌아갈 주소를 요청이 알려 준 대로 믿으면 열린 리다이렉트가 된다 — 바깥 주소를 적어 보내면
 * 토큰이 그리로 날아간다. 사내망만 통과시키는지 본다 (BACKLOG2 §2-1).
 */
class OAuthOriginTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "localhost", "127.0.0.1", "[::1]",
        "192.168.1.224", "192.168.0.1",
        "10.0.0.5", "10.255.255.255",
        "172.16.0.1", "172.31.255.254", "172.20.10.3",
    })
    @DisplayName("사내망 주소는 통과한다")
    void allowsPrivate(String host) {
        assertThat(InternalNetwork.isPrivateHost(host)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "evil.com", "github.com",
        "172.15.0.1", "172.32.0.1", // 사설 대역 바로 바깥
        "11.0.0.1", "193.168.1.1",
        "192.168.1.224.evil.com", // 앞부분만 흉내 낸 주소
        "1.2.3.4",
    })
    @DisplayName("사내망 밖은 막는다")
    void rejectsPublic(String host) {
        assertThat(InternalNetwork.isPrivateHost(host)).isFalse();
    }

    /** CORS 허용 주소도 같은 자를 쓴다 (SecurityConfig). 한쪽만 느슨하면 그쪽으로 들어온다. */
    @ParameterizedTest
    @ValueSource(strings = {
        "http://192.168.1.224:5173", "http://192.168.1.218:5173", "http://localhost:5173",
    })
    @DisplayName("사내망 Origin 은 통과한다")
    void allowsInternalOrigin(String origin) {
        assertThat(InternalNetwork.isInternalOrigin(origin)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://192.168.1.224.evil.com", "http://10.evil.com", "https://evil.com", "null", "",
    })
    @DisplayName("사내망 주소인 척하는 도메인은 막는다")
    void rejectsLookalikeOrigin(String origin) {
        assertThat(InternalNetwork.isInternalOrigin(origin)).isFalse();
    }
}
