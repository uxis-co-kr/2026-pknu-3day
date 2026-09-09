package com.worklog.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LlmProviderResolverTest {

    private final MockLlmProvider mock = new MockLlmProvider();
    private final LlmProvider fake = new LlmProvider() {
        @Override
        public String id() {
            return "gemma4";
        }

        @Override
        public String complete(LlmRequest req) {
            return "실제 요약";
        }
    };

    private static LlmProperties properties(String provider) {
        LlmProperties p = new LlmProperties();
        p.setProvider(provider);
        return p;
    }

    @Test
    @DisplayName("설정된 id 의 프로바이더를 고른다")
    void resolvesConfigured() {
        var resolver = new LlmProviderResolver(List.of(mock, fake), properties("gemma4"));

        assertThat(resolver.resolve()).isSameAs(fake);
    }

    @Test
    @DisplayName("모르는 id 면 mock 으로 폴백한다 — 기동은 성공해야 한다")
    void fallsBackToMock() {
        var resolver = new LlmProviderResolver(List.of(mock, fake), properties("gpt-9"));

        assertThat(resolver.resolve()).isSameAs(mock);
    }

    @Test
    @DisplayName("사용자별 오버라이드가 없거나 모르는 값이면 설정값으로 돌아간다")
    void resolvesOverride() {
        var resolver = new LlmProviderResolver(List.of(mock, fake), properties("mock"));

        assertThat(resolver.resolve("gemma4")).isSameAs(fake);
        assertThat(resolver.resolve(null)).isSameAs(mock);
        assertThat(resolver.resolve("  ")).isSameAs(mock);
        assertThat(resolver.resolve("qwen3")).isSameAs(mock);
    }

    @Test
    @DisplayName("mock 빈이 없으면 폴백이 불가능하므로 기동에 실패시킨다")
    void requiresMock() {
        assertThatThrownBy(() -> new LlmProviderResolver(List.of(fake), properties("gemma4")))
                .isInstanceOf(IllegalStateException.class);
    }
}
