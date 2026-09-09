package com.worklog.stats.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /stats/daily — 홈 요약 카드 4개와 사용자별 집계 (PRD 7).
 */
public record DailyStatsResponse(
        LocalDate date,
        long commits,
        long prs,
        long merges,
        long sessions,
        long commitsDelta,
        long staleSessions,
        List<ByUser> byUser) {

    /** 미가입 GitHub 계정의 활동은 userId 가 없어 여기 집계되지 않는다. */
    public record ByUser(Long userId, long commits, long prs, long merges, long sessions) {}
}
