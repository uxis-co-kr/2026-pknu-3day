package com.worklog.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KstDatesTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 10);

    @Test
    @DisplayName("KST 하루는 UTC 로 전날 15시에 시작한다")
    void startIsUtcPreviousDay() {
        OffsetDateTime start = KstDates.startOf(DAY);

        assertThat(start.toInstant())
                .isEqualTo(OffsetDateTime.of(2026, 9, 9, 15, 0, 0, 0, ZoneOffset.UTC).toInstant());
    }

    @Test
    @DisplayName("끝은 다음 날 00:00 — 열린 구간이라 경계가 겹치지 않는다")
    void endIsExclusiveNextMidnight() {
        assertThat(KstDates.endOf(DAY)).isEqualTo(KstDates.startOf(DAY.plusDays(1)));
    }

    @Test
    @DisplayName("UTC 로 저장된 시각도 KST 날짜로 되돌린다")
    void convertsBackToKstDate() {
        // 2026-09-09T15:30Z = 2026-09-10 00:30 KST
        OffsetDateTime justAfterMidnightKst =
                OffsetDateTime.of(2026, 9, 9, 15, 30, 0, 0, ZoneOffset.UTC);

        assertThat(KstDates.toKstDate(justAfterMidnightKst)).isEqualTo(DAY);
    }

    @Test
    @DisplayName("KST 자정 직전은 아직 같은 날이다")
    void lastInstantBelongsToSameDay() {
        OffsetDateTime lastMoment = KstDates.endOf(DAY).minusNanos(1);

        assertThat(KstDates.toKstDate(lastMoment)).isEqualTo(DAY);
    }
}
