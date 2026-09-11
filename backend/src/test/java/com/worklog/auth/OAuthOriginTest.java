package com.worklog.auth;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(GitHubOAuthController.isPrivateHost(host)).isTrue();
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
        assertThat(GitHubOAuthController.isPrivateHost(host)).isFalse();
    }
}
