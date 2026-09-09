package com.worklog.llm;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code worklog.llm} 설정 (PRD F2).
 *
 * <p>새 모델을 붙이는 일은 코드 수정 없이 {@code presets} 항목 추가로 끝나야 한다.
 * 프리셋을 실제로 호출하는 {@code OpenAiCompatProvider} 는 2일차(2-11)에 붙인다.
 */
@ConfigurationProperties(prefix = "worklog.llm")
public class LlmProperties {

    /** 사용할 프리셋 이름. 기본 mock, 실서비스 기본 gemma4. */
    private String provider = MockLlmProvider.ID;

    private Map<String, Preset> presets = new LinkedHashMap<>();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Map<String, Preset> getPresets() {
        return presets;
    }

    public void setPresets(Map<String, Preset> presets) {
        this.presets = presets;
    }

    /** OpenAI 호환 엔드포인트 하나. */
    public static class Preset {
        private String baseUrl;
        private String model;
        private String apiKey;
        /** 응답 대기 상한. 기본 60초(PRD F2). 느린 모델은 프리셋에서 늘린다. */
        private int timeoutSeconds = 60;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
