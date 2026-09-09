package com.worklog.external;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExternalSummaryServiceTest {

    private static final String CONTENT = """
            # 2026-09-10 업무 일지 — 배태일

            ## 완료한 작업
            - [repo] 하나
            - [repo] 둘
            - [repo] 셋
            - [repo] 넷
            - [repo] 다섯
            - [repo] 여섯

            ## 진행 중 / 미커밋
            - [repo] 미커밋 3개
            """;

    @Test
    @DisplayName("완료한 작업에서 최대 5줄만 뽑고 목록 기호는 뗀다")
    void extractsUpToFiveLines() {
        List<String> highlights = ExternalSummaryService.highlights(CONTENT);

        assertThat(highlights).hasSize(5).containsExactly("[repo] 하나", "[repo] 둘", "[repo] 셋", "[repo] 넷", "[repo] 다섯");
    }

    @Test
    @DisplayName("다른 섹션 내용은 섞이지 않는다")
    void stopsAtSectionBoundary() {
        assertThat(ExternalSummaryService.highlights(CONTENT))
                .noneMatch(line -> line.contains("미커밋"));
    }

    @Test
    @DisplayName("완료한 작업 섹션이 없거나 본문이 없으면 빈 목록")
    void handlesMissingSection() {
        assertThat(ExternalSummaryService.highlights("# 제목만")).isEmpty();
        assertThat(ExternalSummaryService.highlights(null)).isEmpty();
    }
}
