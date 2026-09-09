package com.worklog.config;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 날짜 파라미터를 KST 하루 구간으로 바꾼다 (PRD 12 — 업무 일자는 KST 기준).
 *
 * <p>{@code activities.occurred_at} 은 TIMESTAMPTZ 라 UTC 로 저장돼 있다. "2026-09-10" 은
 * KST 00:00~24:00 이고 UTC 로는 전날 15:00~당일 15:00 이므로, 문자열 비교나 date 캐스팅이
 * 아니라 구간 비교를 해야 한다.
 */
public final class KstDates {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private KstDates() {}

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /** 그날 00:00 KST. */
    public static OffsetDateTime startOf(LocalDate date) {
        return date.atStartOfDay(ZONE).toOffsetDateTime();
    }

    /** 다음 날 00:00 KST — 구간의 열린 끝. {@code start <= t < end} 로 쓴다. */
    public static OffsetDateTime endOf(LocalDate date) {
        return startOf(date.plusDays(1));
    }

    public static LocalDate toKstDate(OffsetDateTime time) {
        return time.atZoneSameInstant(ZONE).toLocalDate();
    }
}
