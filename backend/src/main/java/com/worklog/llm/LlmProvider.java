package com.worklog.llm;

/**
 * LLM 호출 추상화 (PRD F2).
 *
 * <p>구현체는 HTTP 호출만 담당하고 프롬프트는 갖지 않는다. 프롬프트는
 * {@code resources/llm/prompts/} 의 텍스트 파일을 {@link PromptLoader} 가 읽어 넘긴다.
 */
public interface LlmProvider {

    /** 프리셋 이름 — "mock" | "gemma4" | "qwen3". {@code worklog.llm.provider} 값과 대조된다. */
    String id();

    String complete(LlmRequest req);
}
