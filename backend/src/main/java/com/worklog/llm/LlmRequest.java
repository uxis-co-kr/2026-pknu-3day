package com.worklog.llm;

import java.util.Map;

/**
 * LLM 한 번의 호출 입력.
 *
 * @param vars 프롬프트 치환에 쓴 원본 값({@code message}, {@code files}, {@code fileCount} 등).
 *     {@link MockLlmProvider} 가 프롬프트 문자열을 되파싱하지 않고 원본을 그대로 쓰기 위한 통로다.
 *     실제 프로바이더는 이 값을 무시한다.
 */
public record LlmRequest(
        String system, String user, double temperature, int maxTokens, Map<String, String> vars) {

    public static final double DEFAULT_TEMPERATURE = 0.2;
    public static final int DEFAULT_MAX_TOKENS = 400;

    public LlmRequest {
        vars = vars == null ? Map.of() : Map.copyOf(vars);
    }

    public static LlmRequest of(String system, String user, Map<String, String> vars) {
        return new LlmRequest(system, user, DEFAULT_TEMPERATURE, DEFAULT_MAX_TOKENS, vars);
    }

    public String var(String key, String fallback) {
        return vars.getOrDefault(key, fallback);
    }
}
