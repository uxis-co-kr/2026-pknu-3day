package com.worklog.stats;

import com.worklog.auth.AuthenticatedUser;
import com.worklog.auth.DataScope;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.worklog.config.KstDates;
import com.worklog.stats.dto.DailyStatsResponse;
import com.worklog.stats.dto.PeopleStatsResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통계 (PRD 7. /stats ★ — JWT / API Key 둘 다 허용).
 */
@RestController
@RequestMapping("/stats")
public class StatsController {

    private final StatsService statsService;
    private final PeopleStatsService peopleStatsService;

    public StatsController(StatsService statsService, PeopleStatsService peopleStatsService) {
        this.statsService = statsService;
        this.peopleStatsService = peopleStatsService;
    }

    /**
     * 그날 요약. {@code userId} 를 주면 그 사람 것만 — MEMBER 는 생략해도 자기 것만이다 (DataScope).
     * 담당자 1 이 "팀 전원 숫자가 실려 온다" 고 한 자리다 (BACKLOG2 §1).
     */
    @GetMapping("/daily")
    public DailyStatsResponse daily(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date,
            @RequestParam(required = false) Long userId) {
        return statsService.daily(date == null ? KstDates.today() : date, DataScope.userIdFor(principal, userId));
    }

    /**
     * 인원별 시계열 (PRD 7. GET /stats/people — P2, F10b).
     *
     * <p>{@code from}/{@code to} 를 생략하면 오늘까지 7일. {@code userId} 를 생략하면 전원.
     */
    @GetMapping("/people")
    public PeopleStatsResponse people(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @RequestParam(defaultValue = "day") String granularity,
            @RequestParam(required = false) Long userId) {
        return peopleStatsService.people(
                from, to, Granularity.from(granularity), DataScope.userIdFor(principal, userId));
    }
}
