package com.worklog.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LlmProviderResolverTest {

    private final MockLlmProvider mock = new MockLlmProvider();

    /** 프리셋으로 만들어지는 프로바이더 대역 — 실제 HTTP 를 타지 않는다. */
    private static LlmProvider stub(String id) {
        return new LlmProvider() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String complete(LlmRequest req) {
                return "실제 요약 by " + id;
            }
        };
    }

    private static LlmProperties properties(String provider, String... presetNames) {
        LlmProperties p = new LlmProperties();
        p.setProvider(provider);
        for (String name : presetNames) {
            LlmProperties.Preset preset = new LlmProperties.Preset();
            preset.setBaseUrl("http://example.invalid/v1");
            preset.setModel(name + "-model");
            p.getPresets().put(name, preset);
        }
        return p;
    }

    private LlmProviderResolver resolver(LlmProperties properties) {
        return new LlmProviderResolver(
                List.of(mock), properties, (id, preset) -> stub(id));
    }

    @Test
    @DisplayName("프리셋 이름으로 프로바이더가 등록된다 — 코드 수정 없이 모델을 늘릴 수 있어야 한다")
    void registersOneProviderPerPreset() {
        LlmProviderResolver resolver = resolver(properties("gemma4", "gemma4", "qwen3"));

        assertThat(resolver.resolve().id()).isEqualTo("gemma4");
        assertThat(resolver.resolve("qwen3").id()).isEqualTo("qwen3");
    }

    @Test
    @DisplayName("설정값이 mock 이면 프리셋이 있어도 mock 을 쓴다")
    void resolvesMock() {
        assertThat(resolver(properties("mock", "gemma4")).resolve()).isSameAs(mock);
    }

    @Test
    @DisplayName("모르는 id 면 mock 으로 폴백한다 — 기동은 성공해야 한다")
    void fallsBackToMock() {
        assertThat(resolver(properties("gpt-9", "gemma4")).resolve()).isSameAs(mock);
    }

    @Test
    @DisplayName("사용자별 오버라이드가 없거나 모르는 값이면 설정값으로 돌아간다")
    void resolvesOverride() {
        LlmProviderResolver resolver = resolver(properties("mock", "gemma4"));

        assertThat(resolver.resolve("gemma4").id()).isEqualTo("gemma4");
        assertThat(resolver.resolve(null)).isSameAs(mock);
        assertThat(resolver.resolve("  ")).isSameAs(mock);
        assertThat(resolver.resolve("qwen3")).isSameAs(mock);
    }

    @Test
    @DisplayName("프리셋 생성이 실패해도 기동은 계속된다")
    void survivesBrokenPreset() {
        LlmProviderResolver resolver = new LlmProviderResolver(
                List.of(mock),
                properties("gemma4", "gemma4"),
                (id, preset) -> {
                    throw new IllegalArgumentException("base-url 이 없다");
                });

        assertThat(resolver.resolve()).isSameAs(mock);
    }

    @Test
    @DisplayName("mock 빈이 없으면 폴백이 불가능하므로 기동에 실패시킨다")
    void requiresMock() {
        assertThatThrownBy(() -> new LlmProviderResolver(
                        List.of(), properties("gemma4", "gemma4"), (id, preset) -> stub(id)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("프리셋 맵이 비어 있어도 동작한다")
    void worksWithoutPresets() {
        assertThat(resolver(properties("mock")).resolve()).isSameAs(mock);
        assertThat(properties("mock").getPresets()).isEqualTo(Map.of());
    }
}
