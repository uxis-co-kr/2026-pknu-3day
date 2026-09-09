package com.worklog.external;

import com.worklog.config.KstDates;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 외부 연동 API (PRD F8, 7).
 *
 * <p>인증은 {@code SecurityConfig} 에서 이미 API Key 전용으로 걸려 있다 — JWT 로 부르면 403.
 * 컨트롤러 쪽에는 아무 설정도 필요 없다.
 */
@RestController
@RequestMapping("/external")
public class ExternalController {

    private final ExternalSummaryService summaryService;

    public ExternalController(ExternalSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/summary")
    public ExternalSummaryService.DailySummaryResponse summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date) {
        return summaryService.summarize(date == null ? KstDates.today() : date);
    }
}
