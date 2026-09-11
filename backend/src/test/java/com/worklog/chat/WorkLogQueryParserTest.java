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
    @DisplayName("분명한 말(업무일지)과 느슨한 말(요약·뭐함)을 구분하고, 무관한 말은 거른다")
    void intent() {
        assertThat(WorkLogQueryParser.intentOf("조웅식의 오늘 업무일지를 요약해서 보내줘")).isEqualTo(WorkLogQueryParser.Intent.STRONG);
        assertThat(WorkLogQueryParser.intentOf("조웅식 업무요약")).isEqualTo(WorkLogQueryParser.Intent.WEAK);
        assertThat(WorkLogQueryParser.intentOf("배태일 어제 뭐했어?")).isEqualTo(WorkLogQueryParser.Intent.WEAK);
        assertThat(WorkLogQueryParser.intentOf("조웅식 오늘 뭐함")).isEqualTo(WorkLogQueryParser.Intent.WEAK);
        assertThat(WorkLogQueryParser.intentOf("점심 뭐 먹을까요")).isEqualTo(WorkLogQueryParser.Intent.NONE);
        assertThat(WorkLogQueryParser.intentOf("지금 연결된건가")).isEqualTo(WorkLogQueryParser.Intent.NONE);
        assertThat(WorkLogQueryParser.intentOf(null)).isEqualTo(WorkLogQueryParser.Intent.NONE);
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
    @DisplayName("날짜 둘, 이번 주·지난 주·이번 달, 최근 N일을 기간으로 읽는다")
    void ranges() {
        assertThat(WorkLogQueryParser.rangeIn("조웅식 9월 8일~9월 10일 업무일지 요약해서 줘", TODAY))
                .contains(new WorkLogQueryParser.DateRange(LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 10)));
        assertThat(WorkLogQueryParser.rangeIn("9/8 - 9/10 배태일 일지", TODAY))
                .contains(new WorkLogQueryParser.DateRange(LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 10)));
        assertThat(WorkLogQueryParser.rangeIn("2026-09-08부터 2026-09-10까지 조웅식 작업 내역", TODAY))
                .contains(new WorkLogQueryParser.DateRange(LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 10)));
        // 뒤쪽은 일만 적어도, 순서를 바꿔 적어도
        assertThat(WorkLogQueryParser.rangeIn("9월 8일 ~ 10일 조웅식 일지", TODAY))
                .contains(new WorkLogQueryParser.DateRange(LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 10)));
        assertThat(WorkLogQueryParser.rangeIn("9/10~9/8 조웅식 일지", TODAY))
                .contains(new WorkLogQueryParser.DateRange(LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 10)));
        // 상대 표현 (TODAY = 2026-09-10 목요일)
        assertThat(WorkLogQueryParser.rangeIn("조웅식 이번 주 업무일지", TODAY))
                .contains(new WorkLogQueryParser.DateRange(LocalDate.of(2026, 9, 7), TODAY));
        assertThat(WorkLogQueryParser.rangeIn("조웅식 지난주 뭐함", TODAY))
                .contains(new WorkLogQueryParser.DateRange(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 6)));
        assertThat(WorkLogQueryParser.rangeIn("조웅식 이번 달 업무일지", TODAY))
                .contains(new WorkLogQueryParser.DateRange(LocalDate.of(2026, 9, 1), TODAY));
        assertThat(WorkLogQueryParser.rangeIn("조웅식 최근 7일 업무일지", TODAY))
                .contains(new WorkLogQueryParser.DateRange(LocalDate.of(2026, 9, 4), TODAY));
        assertThat(WorkLogQueryParser.rangeIn("조웅식 3일간 작업 내역", TODAY))
                .contains(new WorkLogQueryParser.DateRange(LocalDate.of(2026, 9, 8), TODAY));
        assertThat(WorkLogQueryParser.rangeIn("조웅식 일주일 업무 정리", TODAY))
                .contains(new WorkLogQueryParser.DateRange(LocalDate.of(2026, 9, 4), TODAY));
    }

    @Test
    @DisplayName("이름이 여럿이면 나온 순서대로 전부, 겹치는 자리는 긴 이름만, 같은 이름은 한 번")
    void manyPeople() {
        assertThat(WorkLogQueryParser.peopleIn("배태일이랑 조웅식 이번 주 업무일지", NAMES)).containsExactly("배태일", "조웅식");
        assertThat(WorkLogQueryParser.peopleIn("조웅식, 김민수, 배태일 어제 뭐함", NAMES)).containsExactly("조웅식", "김민수", "배태일");
        assertThat(WorkLogQueryParser.peopleIn("김민수의 일지", NAMES)).containsExactly("김민수");
        assertThat(WorkLogQueryParser.peopleIn("김민 그리고 김민수 일지", NAMES)).containsExactly("김민", "김민수");
        assertThat(WorkLogQueryParser.peopleIn("오늘 업무일지 요약", NAMES)).isEmpty();
        assertThat(WorkLogQueryParser.peopleIn(null, NAMES)).isEmpty();
    }

    @Test
    @DisplayName("날짜가 하나뿐이거나 같은 날 둘이면 기간이 아니다")
    void notARange() {
        assertThat(WorkLogQueryParser.rangeIn("조웅식 9월 9일 업무일지", TODAY)).isEmpty();
        assertThat(WorkLogQueryParser.rangeIn("조웅식 어제 업무일지", TODAY)).isEmpty();
        assertThat(WorkLogQueryParser.rangeIn("9/9~9/9 조웅식 일지", TODAY)).isEmpty();
        assertThat(WorkLogQueryParser.rangeIn("조웅식 1일간 일지", TODAY)).isEmpty();
        assertThat(WorkLogQueryParser.rangeIn(null, TODAY)).isEmpty();
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
