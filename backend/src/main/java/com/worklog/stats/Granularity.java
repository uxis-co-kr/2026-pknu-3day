package com.worklog.stats;

import com.worklog.config.ApiException;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Arrays;

/**
 * 인원별 시계열의 묶는 단위 (PRD 7. /stats/people).
 */
public enum Granularity {
    DAY,
    WEEK;

    /**
     * PRD 7. 은 {@code granularity=day|week} 로 소문자를 쓴다. Spring 의 기본 enum 변환은
     * 대소문자를 구분해 {@code day} 를 거부하므로 여기서 직접 받는다.
     */
    public static Granularity from(String value) {
        if (value == null || value.isBlank()) {
            return DAY;
        }
        return Arrays.stream(values())
                .filter(g -> g.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest(
                        "INVALID_PARAMETER",
                        "granularity 값이 올바르지 않습니다: %s. 가능한 값: day, week".formatted(value)));
    }

    /** 응답과 쿼리에 쓰는 표기. */
    public String wireName() {
        return name().toLowerCase();
    }

    /** 그 날짜가 속한 구간의 시작일. 주는 ISO 기준(월요일 시작). */
    public LocalDate bucketOf(LocalDate date) {
        return this == DAY ? date : date.with(WeekFields.ISO.dayOfWeek(), 1);
    }

    /** 다음 구간의 시작일 — 빈 구간을 0 으로 채울 때 쓴다. */
    public LocalDate next(LocalDate bucketStart) {
        return this == DAY ? bucketStart.plusDays(1) : bucketStart.plusWeeks(1);
    }
}
