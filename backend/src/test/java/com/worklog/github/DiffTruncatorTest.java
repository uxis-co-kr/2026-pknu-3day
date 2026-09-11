package com.worklog.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.worklog.github.dto.GitHubCommitDto;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DiffTruncatorTest {

    private static GitHubCommitDto.File file(String name, String patch) {
        return new GitHubCommitDto.File(name, "modified", 1, 0, 1, patch);
    }

    private static String lines(int count) {
        return IntStream.range(0, count).mapToObj(i -> "+line " + i).collect(Collectors.joining("\n"));
    }

    @Test
    @DisplayName("파일이 없으면 빈 문자열")
    void emptyForNoFiles() {
        assertThat(DiffTruncator.truncate(null)).isEmpty();
        assertThat(DiffTruncator.truncate(List.of())).isEmpty();
    }

    @Test
    @DisplayName("파일마다 이름 머리말을 붙인다")
    void prependsFilename() {
        String diff = DiffTruncator.truncate(List.of(file("a.ts", "@@ -1 +1 @@"), file("b.ts", "@@ -2 +2 @@")));

        assertThat(diff).contains("--- a.ts\n@@ -1 +1 @@").contains("--- b.ts\n@@ -2 +2 @@");
    }

    @Test
    @DisplayName("파일당 200줄을 넘으면 잘라내고 생략 표시를 남긴다")
    void truncatesPerFileLines() {
        String diff = DiffTruncator.truncate(List.of(file("big.ts", lines(260))), 200, 100_000);

        assertThat(diff).contains("+line 199").doesNotContain("+line 200");
        assertThat(diff).contains("(60줄 생략)");
    }

    @Test
    @DisplayName("200줄 이하면 그대로 둔다")
    void keepsShortPatch() {
        String diff = DiffTruncator.truncate(List.of(file("small.ts", lines(200))), 200, 100_000);

        assertThat(diff).contains("+line 199").doesNotContain("줄 생략");
    }

    @Test
    @DisplayName("커밋당 3,000자를 넘으면 전체를 자른다")
    void truncatesPerCommitChars() {
        List<GitHubCommitDto.File> files =
                IntStream.range(0, 20).mapToObj(i -> file("f" + i + ".ts", lines(100))).toList();

        String diff = DiffTruncator.truncate(files);

        assertThat(diff).hasSizeLessThan(DiffTruncator.MAX_CHARS_PER_COMMIT + 20);
        assertThat(diff).endsWith("… (생략)");
    }

    @Test
    @DisplayName("patch 가 없는 파일(바이너리 등)도 이름은 남긴다")
    void keepsBinaryFilename() {
        String diff = DiffTruncator.truncate(List.of(file("logo.png", null)));

        assertThat(diff).isEqualTo("--- logo.png\n(diff 없음)\n");
    }
}
