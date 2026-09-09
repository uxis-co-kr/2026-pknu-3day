package com.worklog.draft;

import com.worklog.config.KstDates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 18:00 KST 에 그날 초안을 자동 생성한다 (PRD F3, DRAFT_SCHEDULE_CRON).
 */
@Component
public class DraftScheduler {

    private static final Logger log = LoggerFactory.getLogger(DraftScheduler.class);

    private final DraftGenerator draftGenerator;

    public DraftScheduler(DraftGenerator draftGenerator) {
        this.draftGenerator = draftGenerator;
    }

    @Scheduled(cron = "${worklog.draft.schedule-cron}", zone = "Asia/Seoul")
    public void generateToday() {
        try {
            int created = draftGenerator.generateForAll(KstDates.today());
            log.info("일일 초안 생성 완료 — {}건", created);
        } catch (Exception e) {
            log.error("일일 초안 생성 실패", e);
        }
    }
}
