package com.worklog.vscode;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 전송마다 덮어써지는 AI 대화 목록에 서버가 적어 둔 요약을 어떻게 남기는지.
 *
 * <p>{@link VscodeSessionService#carryOverSummaries} 만 보는 순수 테스트라 목이 필요 없다.
 */
class AiSummaryCarryOverTest {

    private static final String FIRST = "2026-09-09T10:00:00+09:00";
    private static final String LAST = "2026-09-09T11:00:00+09:00";

    /** 확장이 방금 보낸 대화 — 요약은 비어 있고 질문은 두 번이다. */
    private static List<AiSessionSummary> incoming() {
        return List.of(new AiSessionSummary(
                "sess-1", "출석 중복 검증", FIRST, LAST, 2,
                List.of(new AiTurn(FIRST, "봐 줘", null), new AiTurn(LAST, "테스트도", null)), null, null));
    }

    @Test
    @DisplayName("질문이 늘어난 대화는 요약을 물려주지 않는다 — 오전 요약이 오후 일을 덮는다")
    void dropsSummaryWhenConversationGrew() {
        AiSessionSummary morning = new AiSessionSummary(
                "sess-1", "출석 중복 검증", "2026-09-09T10:00:00+09:00", "2026-09-09T10:30:00+09:00",
                1, List.of(new AiTurn("2026-09-09T10:00:00+09:00", "봐 줘", null)), null, "오전에 정한 것");

        // request(...) 의 대화는 promptCount 2 다 — 오후에 한 번 더 물었다.
        List<AiSessionSummary> kept = VscodeSessionService.carryOverSummaries(
                List.of(morning), incoming());

        assertThat(kept).singleElement().extracting(AiSessionSummary::summary).isNull();
    }

    @Test
    @DisplayName("질문에 답이 뒤늦게 달려도 다시 요약한다 — 질문 수는 그대로다")
    void dropsSummaryWhenAnswerArrived() {
        // 답을 기다리던 때 적어 둔 요약. 질문 수(2)는 지금 오는 것과 같다.
        AiSessionSummary waiting = new AiSessionSummary(
                "sess-1", "출석 중복 검증", FIRST, LAST, 2,
                List.of(new AiTurn(FIRST, "봐 줘", null), new AiTurn(LAST, "테스트도", null)), null,
                "무엇을 할지 물었다");

        List<AiSessionSummary> answered = List.of(new AiSessionSummary(
                "sess-1", "출석 중복 검증", FIRST, LAST, 2,
                List.of(new AiTurn(FIRST, "봐 줘", "같은 날 두 번 찍히는 것을 막았습니다"),
                        new AiTurn(LAST, "테스트도", "네 개 붙였습니다")),
                null, null));

        assertThat(VscodeSessionService.carryOverSummaries(List.of(waiting), answered))
                .singleElement().extracting(AiSessionSummary::summary).isNull();
    }

    @Test
    @DisplayName("오간 것이 그대로면 다시 요약하지 않는다 — 확장은 10분마다 같은 대화를 보낸다")
    void keepsSummaryWhenNothingChanged() {
        AiSessionSummary summarized = new AiSessionSummary(
                "sess-1", "출석 중복 검증", FIRST, LAST, 2,
                List.of(new AiTurn(FIRST, "봐 줘", null), new AiTurn(LAST, "테스트도", null)), null,
                "출석 중복 검증을 살피기로 했다");

        assertThat(VscodeSessionService.carryOverSummaries(List.of(summarized), incoming()))
                .singleElement().extracting(AiSessionSummary::summary)
                .isEqualTo("출석 중복 검증을 살피기로 했다");
    }

    @Test
    @DisplayName("처음 받는 대화는 그대로 둔다")
    void keepsIncomingWhenNothingBefore() {
        assertThat(VscodeSessionService.carryOverSummaries(List.of(), incoming()))
                .isEqualTo(incoming());
    }
}
