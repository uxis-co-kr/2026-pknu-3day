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
        Unmapped unmapped,
        List<ByUser> byUser) {

    /**
     * 어느 사용자에도 붙지 않은 활동 수 (PRD F1-5 — 서비스에 가입한 적 없는 GitHub 계정).
     *
     * <p>총계({@code commits/prs/merges})에는 포함되지만 {@code byUser} 합계에는 빠지므로,
     * 화면이 두 숫자의 차이를 설명할 수 있게 따로 준다. 전부 0 이면 총계 = byUser 합계다.
     */
    public record Unmapped(long commits, long prs, long merges) {}

    /** 미가입 GitHub 계정의 활동은 userId 가 없어 여기 집계되지 않는다. */
    public record ByUser(Long userId, long commits, long prs, long merges, long sessions) {}
}
