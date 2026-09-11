package com.worklog.vscode;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 그 폴더에서 오간 AI 대화 한 세션 (PRD 7 — 확장이 보낸다).
 *
 * <p>V10 에서 모양이 바뀌었다 — 제목이 생기고, 질문 목록이 {@link AiTurn 질문·답변 쌍}이
 * 되었다 (BACKLOG2 §2-3). 시각으로만 구분하면 무슨 대화였는지 알 수 없었다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiSessionSummary(
        String id,
        /** 세션 제목. Claude Code 가 남긴 것이 있으면 그것, 없으면 첫 질문에서 만든다. */
        String title,
        String firstAt,
        String lastAt,
        /** 그날 실제로 물어본 횟수. {@link #turns} 는 잘려도 이 값은 전부 센다. */
        Integer promptCount,
        List<AiTurn> turns,
        /**
         * V10 이전 확장이 보내던 질문 목록.
         *
         * <p>받으면 {@link #turns} 로 옮기고 다시 내보내지 않는다. 같은 내용을 두 모양으로
         * 들고 있으면 어느 쪽이 옳은지 알 수 없다. 옛 확장을 쓰는 사람의 대화를 조용히
         * 버리지 않으려고 입력으로만 받아 준다.
         */
        List<String> prompts,
        /**
         * 이 대화가 무엇이었는지 LLM 이 적은 두어 문장.
         *
         * <p>확장은 보내지 않는다. 서버가 채우고, 다음 전송에서 같은 대화가 다시 오면
         * {@code VscodeSessionService} 가 물려준다 — 대화가 길어지지 않았는데 다시 요약할
         * 이유가 없다.
         */
        String summary) {

    /** 제목을 첫 질문에서 만들 때의 길이. 확장의 MAX_TITLE_LEN 과 같다. */
    private static final int TITLE_LEN = 40;

    public AiSessionSummary {
        if ((turns == null || turns.isEmpty()) && prompts != null && !prompts.isEmpty()) {
            // 옛 판에는 질문별 시각이 없었다. 세션 시작 시각으로 둔다.
            String at = firstAt;
            turns = prompts.stream().map(p -> new AiTurn(at, p, null)).toList();
        }
        turns = turns == null ? List.of() : List.copyOf(turns);
        prompts = null;
        if (title == null || title.isBlank()) {
            String first = turns.isEmpty() ? null : turns.get(0).prompt();
            title = first == null || first.isBlank()
                    ? "제목 없는 대화"
                    : first.length() > TITLE_LEN ? first.substring(0, TITLE_LEN) + "…" : first;
        }
    }
}
