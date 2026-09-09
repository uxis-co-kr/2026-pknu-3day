package com.worklog.vscode;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RemoteUrlParserTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @DisplayName("확장이 보내는 여러 형태의 origin 에서 owner/repo 를 뽑는다")
    @CsvSource({
        "https://github.com/withly/punchcheck.git, withly/punchcheck",
        "https://github.com/withly/punchcheck,     withly/punchcheck",
        "https://github.com/withly/punchcheck/,    withly/punchcheck",
        "git@github.com:withly/punchcheck.git,     withly/punchcheck",
        "ssh://git@github.com/withly/salty-web.git, withly/salty-web",
        "https://github.com/uxis-co-kr/2026-pknu-3day.git, uxis-co-kr/2026-pknu-3day",
        "https://github.com/withly/my.repo.name.git, withly/my.repo.name",
    })
    void parses(String remoteUrl, String expected) {
        assertThat(RemoteUrlParser.toFullName(remoteUrl)).contains(expected);
    }

    @ParameterizedTest
    @DisplayName("모양이 아니면 예외가 아니라 빈 값이다 — 등록 안 된 리포도 세션은 저장돼야 한다")
    @ValueSource(strings = {"", "   ", "not-a-url", "https://github.com/"})
    void emptyWhenUnparseable(String remoteUrl) {
        assertThat(RemoteUrlParser.toFullName(remoteUrl)).isEmpty();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("null 도 빈 값")
    void emptyWhenNull() {
        assertThat(RemoteUrlParser.toFullName(null)).isEmpty();
    }
}
