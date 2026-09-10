package com.worklog.stats.dto;

import com.worklog.draft.DraftStatus;
import java.time.LocalDate;
import java.util.List;

/**
 * GET /stats/people — 사용자별 시계열 (PRD 7, F10b).
 */
public record PeopleStatsResponse(
        LocalDate from, LocalDate to, String granularity, List<Item> items) {

    public record Item(UserSummary user, Totals totals, List<SeriesPoint> series) {}

    public record UserSummary(Long id, String login, String name, String avatarUrl) {}

    public record Totals(long commits, long prs, long merges) {}

    /**
     * @param date 구간의 시작일. 주 단위면 그 주의 월요일이다.
     * @param draft 그날 그 사용자의 최신 버전 초안. 주 단위 집계에서는 항상 null 이다 —
     *     한 주에 초안이 여러 건이라 하나를 고를 근거가 없다.
     */
    public record SeriesPoint(
            LocalDate date, long commits, long prs, long merges, DraftRef draft) {}

    public record DraftRef(Long id, DraftStatus status) {}
}
