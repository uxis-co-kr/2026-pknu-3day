package com.worklog.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.worklog.github.dto.GitHubPullRequestDto;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PR 응답 파싱도 고정 fixture 로 검증한다 (PRD 11).
 */
class GitHubPullRequestParseTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private List<GitHubPullRequestDto> pulls() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/pulls.json")) {
            return List.of(mapper.readValue(in, GitHubPullRequestDto[].class));
        }
    }

    @Test
    @DisplayName("머지된 PR 은 merged_at 과 merged_by 를 읽는다")
    void parsesMergedPullRequest() throws IOException {
        GitHubPullRequestDto merged = pulls().get(0);

        assertThat(merged.number()).isEqualTo(2);
        assertThat(merged.isMerged()).isTrue();
        assertThat(merged.mergedAt())
                .isEqualTo(OffsetDateTime.of(2026, 9, 9, 5, 7, 1, 0, ZoneOffset.UTC));
        assertThat(merged.mergedByLogin()).isEqualTo("UngsikJo");
        assertThat(merged.branch()).isEqualTo("docs/api-contract-ui-fields");
    }

    @Test
    @DisplayName("열려 있는 PR 은 merged 가 아니다")
    void parsesOpenPullRequest() throws IOException {
        GitHubPullRequestDto open = pulls().get(1);

        assertThat(open.isMerged()).isFalse();
        assertThat(open.mergedAt()).isNull();
        assertThat(open.openedByLogin()).isEqualTo("Ae-Ti");
        assertThat(open.body()).isNull();
    }

    @Test
    @DisplayName("external_id 는 PR 번호 문자열 — 커밋의 sha 자리에 대응한다")
    void externalIdIsNumber() throws IOException {
        assertThat(pulls().get(0).externalId()).isEqualTo("2");
        assertThat(pulls().get(1).externalId()).isEqualTo("3");
    }

    @Test
    @DisplayName("merged_by 가 없으면 PR 작성자를 머지한 사람으로 본다")
    void fallsBackToAuthorWhenMergedByMissing() throws IOException {
        GitHubPullRequestDto open = pulls().get(1);

        assertThat(open.mergedBy()).isNull();
        assertThat(open.mergedByLogin()).isEqualTo("Ae-Ti");
    }
}
