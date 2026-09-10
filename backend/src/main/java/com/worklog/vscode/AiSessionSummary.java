package com.worklog.vscode;

import java.util.List;

/**
 * 그 폴더에서 오간 AI 대화 한 세션 (PRD 7 — 확장이 보낸다).
 *
 * <p>답변은 담지 않는다. 무엇을 시켰는지가 그날의 의도에 더 가깝고, 답변까지 담으면
 * 업무 일지 프롬프트가 감당할 수 없다.
 */
public record AiSessionSummary(
        String id, String firstAt, String lastAt, Integer promptCount, List<String> prompts) {

    public List<String> prompts() {
        return prompts == null ? List.of() : prompts;
    }
}
