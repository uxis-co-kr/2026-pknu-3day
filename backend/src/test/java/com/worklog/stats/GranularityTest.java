package com.worklog.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.worklog.config.ApiException;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GranularityTest {

    @Test
    @DisplayName("PRD 7. 의 소문자 표기를 받는다 — Spring 기본 enum 변환은 이걸 거부한다")
    void acceptsLowercase() {
        assertThat(Granularity.from("day")).isEqualTo(Granularity.DAY);
        assertThat(Granularity.from("week")).isEqualTo(Granularity.WEEK);
        assertThat(Granularity.from("WEEK")).isEqualTo(Granularity.WEEK);
        assertThat(Granularity.from(" day ")).isEqualTo(Granularity.DAY);
    }

    @Test
    @DisplayName("생략하면 일 단위")
    void defaultsToDay() {
        assertThat(Granularity.from(null)).isEqualTo(Granularity.DAY);
        assertThat(Granularity.from("  ")).isEqualTo(Granularity.DAY);
    }

    @Test
    @DisplayName("모르는 값은 가능한 값을 알려주며 400")
    void rejectsUnknown() {
        assertThatThrownBy(() -> Granularity.from("month"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("가능한 값: day, week");
    }

    @Test
    @DisplayName("주 단위는 ISO 기준으로 월요일에 묶는다")
    void weekStartsMonday() {
        // 2026-09-10 은 목요일. 그 주 월요일은 2026-09-07.
        LocalDate thursday = LocalDate.of(2026, 9, 10);

        assertThat(Granularity.WEEK.bucketOf(thursday)).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(Granularity.WEEK.bucketOf(LocalDate.of(2026, 9, 7))).isEqualTo(LocalDate.of(2026, 9, 7));
        // 일요일(9/13)도 같은 주다
        assertThat(Granularity.WEEK.bucketOf(LocalDate.of(2026, 9, 13))).isEqualTo(LocalDate.of(2026, 9, 7));
        // 그다음 월요일은 다음 주
        assertThat(Granularity.WEEK.bucketOf(LocalDate.of(2026, 9, 14))).isEqualTo(LocalDate.of(2026, 9, 14));
    }

    @Test
    @DisplayName("일 단위는 날짜를 그대로 쓴다")
    void dayIsIdentity() {
        LocalDate day = LocalDate.of(2026, 9, 10);

        assertThat(Granularity.DAY.bucketOf(day)).isEqualTo(day);
        assertThat(Granularity.DAY.next(day)).isEqualTo(LocalDate.of(2026, 9, 11));
        assertThat(Granularity.WEEK.next(LocalDate.of(2026, 9, 7))).isEqualTo(LocalDate.of(2026, 9, 14));
    }
}
