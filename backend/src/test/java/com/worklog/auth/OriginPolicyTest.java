package com.worklog.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** BACKLOG2 §2-1 B — 아무 주소로나 돌려보내면 열린 리다이렉트가 된다. 사내망 대역만 통과한다. */
class OriginPolicyTest {

    private final OriginPolicy policy = new OriginPolicy(List.of());

    @Test
    @DisplayName("사내망 대역과 localhost 는 포트에 상관없이 통과한다")
    void allowsPrivateRanges() {
        assertThat(policy.normalize("http://192.168.1.218:5173")).contains("http://192.168.1.218:5173");
        assertThat(policy.normalize("http://10.0.0.7:8080")).contains("http://10.0.0.7:8080");
        assertThat(policy.normalize("http://172.20.3.4")).contains("http://172.20.3.4");
        assertThat(policy.normalize("http://localhost:5173")).contains("http://localhost:5173");
        assertThat(policy.normalize("http://127.0.0.1:5173")).contains("http://127.0.0.1:5173");
    }

    @Test
    @DisplayName("경로·쿼리는 버리고 origin 만 남긴다")
    void stripsPath() {
        assertThat(policy.normalize("http://192.168.1.218:5173/settings?github=linked"))
                .contains("http://192.168.1.218:5173");
    }

    @Test
    @DisplayName("공인 주소·이름·다른 스킴은 거절한다")
    void rejectsOutside() {
        assertThat(policy.normalize("http://evil.example.com")).isEmpty();
        assertThat(policy.normalize("http://203.0.113.5:5173")).isEmpty();
        assertThat(policy.normalize("ftp://192.168.1.218")).isEmpty();
        assertThat(policy.normalize("javascript:alert(1)")).isEmpty();
        assertThat(policy.normalize("not a url")).isEmpty();
        assertThat(policy.normalize(null)).isEmpty();
        // 172.16/12 밖
        assertThat(policy.normalize("http://172.32.0.1")).isEmpty();
    }

    @Test
    @DisplayName("WORKLOG_ALLOWED_ORIGINS 로 호스트 이름과 CIDR 을 더할 수 있다")
    void extraEntries() {
        OriginPolicy extended = new OriginPolicy(List.of(" dev.example.internal ", "203.0.113.0/24", "https://ops.example.com:8443"));

        assertThat(extended.normalize("http://dev.example.internal:5173")).contains("http://dev.example.internal:5173");
        assertThat(extended.normalize("http://203.0.113.77")).contains("http://203.0.113.77");
        assertThat(extended.normalize("https://ops.example.com")).contains("https://ops.example.com");
        assertThat(extended.normalize("http://203.0.114.1")).isEmpty();
    }

    @Test
    @DisplayName("사내 IP 를 가리키는 이름은 목록에 없으면 통과하지 않는다 — DNS 를 믿지 않는다")
    void noDnsResolution() {
        assertThat(policy.isAllowed("http://localtest.me")).isFalse();
    }
}
