package com.worklog.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.worklog.github.dto.GitHubCommitDto;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * GitHub 응답 파싱은 고정 fixture 로 검증한다 (PRD 11) — OAuth 없이도 독립적으로 돌아간다.
 */
class GitHubCollectorParseTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private <T> T read(String fixture, Class<T> type) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/" + fixture)) {
            return mapper.readValue(in, type);
        }
    }

    @Test
    @DisplayName("커밋 목록에서 sha·제목·발생 시각을 읽는다")
    void parsesCommitList() throws IOException {
        List<GitHubCommitDto> commits =
                List.of(read("commits-list.json", GitHubCommitDto[].class));

        assertThat(commits).hasSize(2);
        GitHubCommitDto first = commits.get(0);
        assertThat(first.sha()).isEqualTo("a1b2c3d4e5f60718293a4b5c6d7e8f9012345678");
        assertThat(first.title()).isEqualTo("feat: 출석 API 추가");
        assertThat(first.commit().message()).contains("중복 출석 검증은 다음 커밋에서 붙인다.");
        assertThat(first.occurredAt())
                .isEqualTo(OffsetDateTime.of(2026, 9, 9, 10, 12, 0, 0, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("GitHub 계정이 연결된 커밋은 login 을, 아닌 커밋은 커밋에 적힌 이름을 쓴다")
    void resolvesAuthorLogin() throws IOException {
        List<GitHubCommitDto> commits =
                List.of(read("commits-list.json", GitHubCommitDto[].class));

        assertThat(commits.get(0).authorLogin()).isEqualTo("Ae-Ti");
        assertThat(commits.get(1).author()).isNull();
        assertThat(commits.get(1).authorLogin()).isEqualTo("외부 기여자");
    }

    @Test
    @DisplayName("커밋 상세에서 files·stats 를 읽는다 — 목록 응답에는 없는 필드")
    void parsesCommitDetail() throws IOException {
        GitHubCommitDto detail = read("commit-detail.json", GitHubCommitDto.class);

        assertThat(detail.stats().additions()).isEqualTo(120);
        assertThat(detail.stats().deletions()).isEqualTo(8);
        assertThat(detail.files()).hasSize(2);
        assertThat(detail.files().get(0).filename()).isEqualTo("src/api/attendance.ts");
        assertThat(detail.files().get(0).patch()).startsWith("@@ -1,3 +1,5 @@");
        // 바이너리 파일에는 patch 가 없다.
        assertThat(detail.files().get(1).patch()).isNull();
    }

    @Test
    @DisplayName("상세 응답을 그대로 diff 로 자를 수 있다")
    void truncatesDetailDiff() throws IOException {
        GitHubCommitDto detail = read("commit-detail.json", GitHubCommitDto.class);

        String diff = DiffTruncator.truncate(detail.files());

        assertThat(diff)
                .contains("--- src/api/attendance.ts")
                .contains("+export function check() {")
                .contains("--- docs/logo.png")
                .contains("(diff 없음)");
    }
}
