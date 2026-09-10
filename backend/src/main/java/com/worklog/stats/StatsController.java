package com.worklog.stats;

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

    @GetMapping("/daily")
    public DailyStatsResponse daily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date) {
        return statsService.daily(date == null ? KstDates.today() : date);
    }

    /**
     * 인원별 시계열 (PRD 7. GET /stats/people — P2, F10b).
     *
     * <p>{@code from}/{@code to} 를 생략하면 오늘까지 7일. {@code userId} 를 생략하면 전원.
     */
    @GetMapping("/people")
    public PeopleStatsResponse people(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @RequestParam(defaultValue = "day") String granularity,
            @RequestParam(required = false) Long userId) {
        return peopleStatsService.people(from, to, Granularity.from(granularity), userId);
    }
}
