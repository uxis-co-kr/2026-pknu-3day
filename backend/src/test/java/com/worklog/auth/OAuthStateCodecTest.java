package com.worklog.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OAuthStateCodecTest {

    private static final Instant T0 = Instant.parse("2026-09-10T07:00:00Z");
    private final OAuthStateCodec codec = new OAuthStateCodec("test-secret", Clock.fixed(T0, ZoneOffset.UTC));

    @Test
    @DisplayName("발급한 state 는 그대로 통하고, 붙일 사용자 id 를 돌려준다")
    void roundTrip() {
        assertThat(codec.verify(codec.issue(7L))).get().extracting(OAuthStateCodec.Parsed::linkUserId).isEqualTo(7L);
        assertThat(codec.verify(codec.issue(null))).get().extracting(OAuthStateCodec.Parsed::linkUserId).isNull();
    }

    @Test
    @DisplayName("돌아갈 화면 주소도 서명 안에 실려 그대로 돌아온다 (BACKLOG2 §2-1)")
    void carriesReturnTo() {
        String state = codec.issue(7L, "http://192.168.1.218:5173");
        OAuthStateCodec.Parsed parsed = codec.verify(state).orElseThrow();

        assertThat(parsed.linkUserId()).isEqualTo(7L);
        assertThat(parsed.returnTo()).isEqualTo("http://192.168.1.218:5173");
        assertThat(codec.verify(codec.issue(null)).orElseThrow().returnTo()).isNull();
    }

    @Test
    @DisplayName("한 글자만 바꿔도, 사용자 id 를 바꿔 끼워도 통하지 않는다")
    void rejectsTampering() {
        String state = codec.issue(7L);
        String body = state.substring(0, state.lastIndexOf('.'));
        String sig = state.substring(state.lastIndexOf('.') + 1);

        assertThat(codec.verify(state.substring(0, state.length() - 1) + "x")).isEmpty();
        // 본문(사용자 id 포함)을 바꾸고 서명은 그대로
        assertThat(codec.verify(body + "AA." + sig)).isEmpty();
        assertThat(codec.verify("garbage")).isEmpty();
        assertThat(codec.verify(null)).isEmpty();
    }

    @Test
    @DisplayName("다른 비밀로 만든 state 는 통하지 않는다")
    void rejectsOtherSecret() {
        String other = new OAuthStateCodec("other-secret", Clock.fixed(T0, ZoneOffset.UTC)).issue(7L);
        assertThat(codec.verify(other)).isEmpty();
    }

    @Test
    @DisplayName("5분이 지나면 통하지 않는다")
    void expires() {
        String state = codec.issue(7L);
        OAuthStateCodec later = new OAuthStateCodec("test-secret", Clock.fixed(T0.plus(Duration.ofMinutes(6)), ZoneOffset.UTC));
        OAuthStateCodec soon = new OAuthStateCodec("test-secret", Clock.fixed(T0.plus(Duration.ofMinutes(4)), ZoneOffset.UTC));
        assertThat(later.verify(state)).isEmpty();
        assertThat(soon.verify(state)).isPresent();
    }
}
