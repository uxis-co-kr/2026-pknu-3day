package com.worklog.notify;

import com.worklog.config.KstDates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 업무 시간에 매시 정각, 미커밋 리마인드를 돌린다 (PRD F7-2).
 *
 * <p>대상이 없으면 쿼리 한 번으로 끝난다.
 */
@Component
public class UncommittedReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(UncommittedReminderScheduler.class);

    private final UncommittedReminder reminder;

    public UncommittedReminderScheduler(UncommittedReminder reminder) {
        this.reminder = reminder;
    }

    @Scheduled(cron = "${worklog.notify.remind-cron:0 0 10-18 * * *}", zone = "Asia/Seoul")
    public void run() {
        try {
            reminder.forgetBefore(KstDates.today());
            reminder.remindToday();
        } catch (Exception e) {
            log.error("미커밋 리마인드 실행 실패", e);
        }
    }
}
