package com.worklog.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkLogQueryParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 10);
    private static final List<String> NAMES = List.of("조웅식", "배태일", "ungsikJo", "김민", "김민수");

    @Test
    @DisplayName("업무 일지를 묻는 말만 받는다")
    void intent() {
        assertThat(WorkLogQueryParser.asksForWorkLog("조웅식의 오늘 업무일지를 요약해서 보내줘")).isTrue();
        assertThat(WorkLogQueryParser.asksForWorkLog("배태일 어제 뭐했어?")).isTrue();
        assertThat(WorkLogQueryParser.asksForWorkLog("점심 뭐 먹을까요")).isFalse();
        assertThat(WorkLogQueryParser.asksForWorkLog(null)).isFalse();
    }

    @Test
    @DisplayName("날짜 표현이 없으면 오늘")
    void defaultsToToday() {
        assertThat(WorkLogQueryParser.dateIn("조웅식의 오늘 업무일지", TODAY)).isEqualTo(TODAY);
        assertThat(WorkLogQueryParser.dateIn("조웅식 업무일지", TODAY)).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("어제·그저께·9월 9일·9/9·2026-09-09 를 읽는다")
    void dates() {
        assertThat(WorkLogQueryParser.dateIn("배태일 어제 업무일지", TODAY)).isEqualTo(TODAY.minusDays(1));
        assertThat(WorkLogQueryParser.dateIn("배태일 그저께 업무일지", TODAY)).isEqualTo(TODAY.minusDays(2));
        assertThat(WorkLogQueryParser.dateIn("9월 9일 배태일 일지", TODAY)).isEqualTo(LocalDate.of(2026, 9, 9));
        assertThat(WorkLogQueryParser.dateIn("9/9 배태일 일지", TODAY)).isEqualTo(LocalDate.of(2026, 9, 9));
        assertThat(WorkLogQueryParser.dateIn("2026-09-08 배태일 일지", TODAY)).isEqualTo(LocalDate.of(2026, 9, 8));
    }

    @Test
    @DisplayName("구체적인 날짜가 '어제' 보다 우선하고, 없는 날짜는 오늘로 본다")
    void precedenceAndInvalid() {
        assertThat(WorkLogQueryParser.dateIn("어제 말고 9월 8일 일지", TODAY)).isEqualTo(LocalDate.of(2026, 9, 8));
        assertThat(WorkLogQueryParser.dateIn("2월 30일 일지", TODAY)).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("조사가 붙어도 명단의 이름을 찾고, 겹치면 긴 이름을 고른다")
    void person() {
        assertThat(WorkLogQueryParser.personIn("조웅식의 오늘 업무일지", NAMES)).contains("조웅식");
        assertThat(WorkLogQueryParser.personIn("배태일이 어제 뭐했어", NAMES)).contains("배태일");
        assertThat(WorkLogQueryParser.personIn("김민수의 일지", NAMES)).contains("김민수");
        assertThat(WorkLogQueryParser.personIn("오늘 업무일지 요약", NAMES)).isEmpty();
    }
}
