package com.worklog.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MockLlmProviderTest {

    private final MockLlmProvider provider = new MockLlmProvider();

    @Test
    @DisplayName("id 는 mock")
    void id() {
        assertThat(provider.id()).isEqualTo("mock");
    }

    @Test
    @DisplayName("커밋 메시지와 파일 수로 고정 형식의 요약을 만든다")
    void formatsSummary() {
        LlmRequest req = LlmRequest.of(
                "system", "user", Map.of("message", "feat: 출석 API 추가", "fileCount", "4"));

        assertThat(provider.complete(req)).isEqualTo("feat: 출석 API 추가 작업을 수행했습니다. (변경 파일 4개)");
    }

    @Test
    @DisplayName("여러 줄 커밋 메시지는 제목 줄만 쓴다")
    void usesFirstLineOnly() {
        LlmRequest req = LlmRequest.of(
                "system",
                "user",
                Map.of("message", "fix: 중복 출석 방지\n\n본문 설명이 길게 이어진다.", "fileCount", "2"));

        assertThat(provider.complete(req)).isEqualTo("fix: 중복 출석 방지 작업을 수행했습니다. (변경 파일 2개)");
    }

    @Test
    @DisplayName("메시지가 비어 있어도 예외 없이 문장을 만든다")
    void handlesMissingVars() {
        LlmRequest req = LlmRequest.of("system", "user", Map.of());

        assertThat(provider.complete(req)).isEqualTo("커밋 내용을 확인했습니다. (변경 파일 0개)");
    }
}
