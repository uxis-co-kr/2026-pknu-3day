package com.worklog.github;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 주소를 붙여 넣어 등록한다 (9/10). 사람이 실제로 복사해 오는 모양들을 모두 받는다. */
class RepoRefParseTest {

    @ParameterizedTest
    @CsvSource({
        "https://github.com/uxis-co-kr/2026-pknu-3day, uxis-co-kr/2026-pknu-3day",
        "http://github.com/uxis-co-kr/2026-pknu-3day, uxis-co-kr/2026-pknu-3day",
        "https://www.github.com/uxis-co-kr/2026-pknu-3day, uxis-co-kr/2026-pknu-3day",
        "github.com/uxis-co-kr/2026-pknu-3day, uxis-co-kr/2026-pknu-3day",
        "https://github.com/uxis-co-kr/2026-pknu-3day.git, uxis-co-kr/2026-pknu-3day",
        "git@github.com:uxis-co-kr/2026-pknu-3day.git, uxis-co-kr/2026-pknu-3day",
        "https://github.com/uxis-co-kr/2026-pknu-3day/tree/main, uxis-co-kr/2026-pknu-3day",
        "https://github.com/uxis-co-kr/2026-pknu-3day/issues/12, uxis-co-kr/2026-pknu-3day",
        "  https://github.com/uxis-co-kr/2026-pknu-3day  , uxis-co-kr/2026-pknu-3day",
        "uxis-co-kr/2026-pknu-3day, uxis-co-kr/2026-pknu-3day",
    })
    @DisplayName("주소·클론 주소·하위 경로·owner/repo 를 모두 받는다")
    void parses(String input, String expected) {
        assertThat(RepoService.parseRepoRef(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "uxis-co-kr", "https://gitlab.com/a/b", "https://github.com/"})
    @DisplayName("GitHub 주소가 아니면 받지 않는다")
    void rejects(String input) {
        assertThat(RepoService.parseRepoRef(input)).isNull();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("null 도 안전하게 거른다")
    void rejectsNull() {
        assertThat(RepoService.parseRepoRef(null)).isNull();
    }
}
