package com.worklog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[48]);

    private final JwtService jwtService = new JwtService(SECRET, 12);

    private static User user(long id, String login) {
        User user = new User();
        user.setId(id);
        user.setGithubId(1000L + id);
        user.setLogin(login);
        return user;
    }

    @Test
    @DisplayName("발급한 토큰을 검증하면 사용자 id 와 login 이 나온다")
    void issueAndVerify() {
        String token = jwtService.issue(user(3L, "ungsik"));

        AuthenticatedUser principal = jwtService.verify(token);

        assertThat(principal.id()).isEqualTo(3L);
        assertThat(principal.login()).isEqualTo("ungsik");
        assertThat(principal.authMethod()).isEqualTo(AuthMethod.JWT);
    }

    @Test
    @DisplayName("서명이 다른 토큰은 거부한다")
    void rejectsForeignSignature() {
        byte[] other = new byte[48];
        other[0] = 9;
        String foreign = new JwtService(Base64.getEncoder().encodeToString(other), 12).issue(user(3L, "x"));

        assertThatThrownBy(() -> jwtService.verify(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("발급 12시간 뒤의 토큰은 만료로 거부한다")
    void rejectsExpired() {
        Clock thirteenHoursAgo =
                Clock.fixed(Clock.systemUTC().instant().minus(Duration.ofHours(13)), ZoneOffset.UTC);
        String expired = new JwtService(SECRET, 12, thirteenHoursAgo).issue(user(3L, "ungsik"));

        assertThatThrownBy(() -> jwtService.verify(expired))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("변조된 토큰은 거부한다")
    void rejectsTampered() {
        String token = jwtService.issue(user(3L, "ungsik"));
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "AAAA";

        assertThatThrownBy(() -> jwtService.verify(tampered)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwtService.verify("not.a.jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("32바이트 미만 비밀키는 기동에 실패한다")
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtService("short", 12)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService("", 12)).isInstanceOf(IllegalStateException.class);
    }
}
