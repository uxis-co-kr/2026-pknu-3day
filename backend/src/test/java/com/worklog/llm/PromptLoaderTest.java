package com.worklog.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PromptLoaderTest {

    private final PromptLoader loader = new PromptLoader();

    @Test
    @DisplayName("system 프롬프트는 PRD F2 본문을 그대로 읽는다")
    void loadsSystemPrompt() {
        String system = loader.load(PromptLoader.COMMIT_SUMMARY_SYSTEM);

        assertThat(system).startsWith("당신은 개발팀의 업무 일지 작성 보조자입니다.");
        assertThat(system).contains("추측하지 말고 diff에 근거하세요.");
    }

    @Test
    @DisplayName("{repo} {message} {files} {diff} 를 치환한다")
    void rendersPlaceholders() {
        String user = loader.render(
                PromptLoader.COMMIT_SUMMARY_USER,
                Map.of(
                        "repo", "uxis-co-kr/2026-pknu-3day",
                        "message", "feat: 출석 API 추가",
                        "files", "src/api/attendance.ts",
                        "diff", "@@ -1 +1 @@"));

        assertThat(user)
                .contains("리포: uxis-co-kr/2026-pknu-3day")
                .contains("커밋 메시지: feat: 출석 API 추가")
                .contains("변경 파일: src/api/attendance.ts")
                .contains("@@ -1 +1 @@")
                .doesNotContain("{repo}", "{diff}");
    }

    @Test
    @DisplayName("값이 없는 자리표시자는 빈 문자열로 지운다")
    void blanksMissingVars() {
        String user = loader.render(PromptLoader.COMMIT_SUMMARY_USER, Map.of("repo", "a/b"));

        assertThat(user).contains("리포: a/b").doesNotContain("{message}", "{files}", "{diff}");
    }

    @Test
    @DisplayName("치환 값에 $ 나 백슬래시가 있어도 그대로 들어간다")
    void escapesReplacement() {
        String user = loader.render(
                PromptLoader.COMMIT_SUMMARY_USER, Map.of("diff", "+ const price = \"$100\\n\""));

        assertThat(user).contains("+ const price = \"$100\\n\"");
    }

    @Test
    @DisplayName("없는 프롬프트 파일은 즉시 실패한다")
    void failsOnMissingFile() {
        assertThatThrownBy(() -> loader.load("no-such-prompt")).isInstanceOf(UncheckedIOException.class);
    }
}
