package com.worklog.vscode;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** V10 에서 모양이 바뀐 AI 대화 요약 (BACKLOG2 §2-3). */
class AiSessionSummaryTest {

    private static final String FIRST = "2026-09-09T10:00:00+09:00";
    private static final String LAST = "2026-09-09T11:00:00+09:00";

    @Test
    @DisplayName("V10 이전 확장이 보낸 prompts[] 는 turns 로 옮기고 다시 내보내지 않는다")
    void convertsLegacyPrompts() {
        AiSessionSummary legacy = new AiSessionSummary(
                "sess-old", null, FIRST, LAST, 7, null, List.of("출석 중복 검증 로직 봐 줘", "테스트도 붙여 줘"));

        // 같은 내용을 두 모양으로 들고 있으면 어느 쪽이 옳은지 알 수 없다.
        assertThat(legacy.prompts()).isNull();
        assertThat(legacy.turns()).extracting(AiTurn::prompt)
                .containsExactly("출석 중복 검증 로직 봐 줘", "테스트도 붙여 줘");
        // 옛 판에는 질문별 시각이 없었다. 세션 시작 시각으로 둔다.
        assertThat(legacy.turns().get(0).at()).isEqualTo(FIRST);
        assertThat(legacy.turns().get(0).answer()).isNull();
        // 자른 개수가 아니라 실제로 물어본 횟수는 그대로 둔다 (C-1 ①).
        assertThat(legacy.promptCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("제목이 없으면 첫 질문에서 만든다 — 시각만으로는 무슨 대화였는지 알 수 없다")
    void buildsTitleFromFirstPrompt() {
        AiSessionSummary noTitle = new AiSessionSummary(
                "s", "  ", FIRST, LAST, 1, List.of(new AiTurn(FIRST, "출석 중복 검증 로직 봐 줘", null)), null);
        assertThat(noTitle.title()).isEqualTo("출석 중복 검증 로직 봐 줘");

        String longPrompt = "가".repeat(60);
        AiSessionSummary clipped = new AiSessionSummary(
                "s", null, FIRST, LAST, 1, List.of(new AiTurn(FIRST, longPrompt, null)), null);
        assertThat(clipped.title()).isEqualTo("가".repeat(40) + "…");

        AiSessionSummary empty = new AiSessionSummary("s", null, FIRST, LAST, 0, List.of(), null);
        assertThat(empty.title()).isEqualTo("제목 없는 대화");
        assertThat(empty.turns()).isEmpty();
    }

    @Test
    @DisplayName("확장이 준 제목은 그대로 둔다")
    void keepsRecordedTitle() {
        AiSessionSummary s = new AiSessionSummary(
                "s", "WorkLog Drafter 클라이언트 진행", FIRST, LAST, 3,
                List.of(new AiTurn(FIRST, "푸시하고 백로그2 진행해", "백로그2 의 1순위를 끝냈습니다")), null);

        assertThat(s.title()).isEqualTo("WorkLog Drafter 클라이언트 진행");
        assertThat(s.turns().get(0).answer()).isEqualTo("백로그2 의 1순위를 끝냈습니다");
    }
}
