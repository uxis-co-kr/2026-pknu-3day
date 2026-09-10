package com.worklog.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 사내 API 가 없을 때 쓰는 임시 사원 목록. 연동이 끝나면 이 기능과 함께 지운다.
 */
class WapleFallbackTest {

    private static WapleClient client(String baseUrl, String apiKey, String fallback) {
        WapleProperties p = new WapleProperties();
        p.setBaseUrl(baseUrl);
        p.setApiKey(apiKey);
        p.setFallbackEmployees(fallback);
        return new WapleClient(p);
    }

    @Test
    @DisplayName("사내 API 설정이 없으면 임시 목록을 쓴다")
    void usesFallbackWhenNotConfigured() {
        var employees = client(null, null, "9999:조웅식,9998:배태일").employees(0L);

        assertThat(employees).hasSize(2);
        assertThat(employees.get(0).empSeq()).isEqualTo(9999L);
        assertThat(employees.get(0).empNm()).isEqualTo("조웅식");
        assertThat(employees.get(1).empSeq()).isEqualTo(9998L);
    }

    @Test
    @DisplayName("공백이 섞여 있어도 읽는다")
    void tolerantOfSpaces() {
        assertThat(client(null, null, " 9999 : 조웅식 , 9998 : 배태일 ").employees(0L)).hasSize(2);
    }

    @Test
    @DisplayName("형식이 틀린 항목은 건너뛰고 나머지를 살린다")
    void skipsMalformedEntries() {
        var employees = client(null, null, "9999:조웅식,이건번호가없음,abc:xyz,9998:배태일").employees(0L);

        assertThat(employees).hasSize(2);
    }

    @Test
    @DisplayName("임시 목록이 비어 있으면 빈 목록")
    void emptyWhenNoFallback() {
        assertThat(client(null, null, "").employees(0L)).isEmpty();
        assertThat(client(null, null, null).employees(0L)).isEmpty();
    }

    @Test
    @DisplayName("진짜 API 가 설정돼 있으면 임시 목록은 쓰이지 않는다")
    void realApiWins() {
        // 사내망이 아니라 호출은 실패하지만, 임시 목록으로 떨어지지 않는다는 것이 요점이다.
        var employees = client("http://127.0.0.1:1/none", "key", "9999:조웅식").employees(1L);

        assertThat(employees).isEmpty();
    }
}
