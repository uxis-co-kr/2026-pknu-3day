package com.worklog.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 요약 파이프라인을 주기적으로 돌린다 (PRD F2, 11).
 *
 * <p>수집 직후에도 한 번 돌아야 대시보드에 바로 뜨므로 {@code fixedDelay} 를 짧게 잡았다.
 * 처리할 대상이 없으면 쿼리 한 번으로 끝난다.
 */
@Component
public class SummaryScheduler {

    private static final Logger log = LoggerFactory.getLogger(SummaryScheduler.class);

    private final SummaryService summaryService;

    public SummaryScheduler(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @Scheduled(
            fixedDelayString = "${worklog.llm.summary-interval-ms:60000}",
            initialDelayString = "${worklog.llm.summary-initial-delay-ms:10000}")
    public void run() {
        try {
            summaryService.runOnce();
        } catch (Exception e) {
            log.error("요약 파이프라인 실행 실패", e);
        }
    }
}
