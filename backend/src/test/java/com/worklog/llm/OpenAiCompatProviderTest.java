package com.worklog.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenAiCompatProviderTest {

    private static Map<String, Object> chatResponse(String content) {
        return Map.of("choices", List.of(Map.of("message", Map.of("role", "assistant", "content", content))));
    }

    @Test
    @DisplayName("choices[0].message.content 를 꺼낸다")
    void extractsContent() {
        assertThat(OpenAiCompatProvider.extractContent(chatResponse("출석 API 를 추가했다.")))
                .isEqualTo("출석 API 를 추가했다.");
    }

    @Test
    @DisplayName("형식이 다른 응답은 예외 — 요약 파이프라인이 재시도로 처리한다")
    void rejectsMalformedResponse() {
        assertThatThrownBy(() -> OpenAiCompatProvider.extractContent(null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> OpenAiCompatProvider.extractContent(Map.of()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> OpenAiCompatProvider.extractContent(Map.of("choices", List.of())))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() ->
                        OpenAiCompatProvider.extractContent(Map.of("choices", List.of(Map.of()))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("qwen3 의 <think> 블록을 지운다")
    void stripsThinkBlock() {
        String raw = "<think>먼저 diff를 보자. 파일이 두 개다.</think>\n출석 API 를 추가했다.";

        assertThat(OpenAiCompatProvider.stripThinking(raw)).isEqualTo("출석 API 를 추가했다.");
    }

    @Test
    @DisplayName("여러 줄에 걸친 <think> 도 지운다")
    void stripsMultilineThinkBlock() {
        String raw = "<think>\n한 줄\n두 줄\n</think>요약";

        assertThat(OpenAiCompatProvider.stripThinking(raw)).isEqualTo("요약");
    }

    @Test
    @DisplayName("닫히지 않은 <think> 는 그 뒤를 통째로 버린다 — max_tokens 로 잘린 경우")
    void dropsDanglingThink() {
        String raw = "요약 문장.\n<think>여기서 잘렸다";

        assertThat(OpenAiCompatProvider.stripThinking(raw)).isEqualTo("요약 문장.");
    }

    @Test
    @DisplayName("think 가 없으면 앞뒤 공백만 정리한다")
    void keepsPlainText() {
        assertThat(OpenAiCompatProvider.stripThinking("  그냥 요약  ")).isEqualTo("그냥 요약");
        assertThat(OpenAiCompatProvider.stripThinking(null)).isNull();
    }

    @Test
    @DisplayName("base-url 이 없는 프리셋은 만들 때 실패한다 — 기동 시 리졸버가 잡는다")
    void requiresBaseUrl() {
        LlmProperties.Preset preset = new LlmProperties.Preset();
        preset.setModel("some-model");

        assertThatThrownBy(() -> new OpenAiCompatProvider("broken", preset))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
