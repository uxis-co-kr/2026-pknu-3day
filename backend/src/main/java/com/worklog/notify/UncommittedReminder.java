package com.worklog.notify;

import com.worklog.config.KstDates;
import com.worklog.vscode.VscodeSession;
import com.worklog.vscode.VscodeSessionRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미커밋 작업 리마인드 (PRD F7 이벤트 2, F6 커밋 리마인드).
 *
 * <p>같은 세션에 하루 한 번만 보낸다. 표시를 DB 에 남기려면 {@code vscode_sessions} 에 컬럼이
 * 필요한데 다음 사이클의 회원 체계가 V4 를 쓸 예정이라 번호를 비워 두었다. 프로세스를 다시
 * 띄우면 그날 한 번 더 갈 수 있지만, 스케줄러가 시간당 1회이고 하루짜리 제약이라 실해가 없다.
 */
@Service
public class UncommittedReminder {

    private static final Logger log = LoggerFactory.getLogger(UncommittedReminder.class);

    private final VscodeSessionRepository sessionRepository;
    private final NotifyService notifyService;

    /** 이미 보낸 (날짜, 세션) — 중복 방지. */
    private final Set<String> alreadySent = ConcurrentHashMap.newKeySet();

    public UncommittedReminder(
            VscodeSessionRepository sessionRepository, NotifyService notifyService) {
        this.sessionRepository = sessionRepository;
        this.notifyService = notifyService;
    }

    /**
     * 그날 세션을 훑어 조건에 맞는 것만 알린다.
     *
     * @return 보낸 건수
     */
    @Transactional(readOnly = true)
    public int remind(LocalDate workDate, OffsetDateTime now) {
        int sent = 0;
        int candidates = 0;
        for (VscodeSession session : sessionRepository.findAllForRemind(workDate)) {
            if (!RemindPolicy.needsReminder(session, now)) {
                continue;
            }
            candidates++;
            if (!alreadySent.add(key(workDate, session.getId()))) {
                continue; // 오늘 이미 보냈다
            }
            if (notifyService.notifyUncommitted(session, now)) {
                sent++;
            }
        }
        if (candidates > 0) {
            log.info("미커밋 리마인드 — 대상 {}건, 전송 {}건", candidates, sent);
        }
        return sent;
    }

    public int remindToday() {
        return remind(KstDates.today(), OffsetDateTime.now());
    }

    /** 날짜가 바뀌면 지난 기록은 필요 없다 — 무한히 쌓이지 않게 비운다. */
    void forgetBefore(LocalDate workDate) {
        alreadySent.removeIf(k -> !k.startsWith(workDate.toString() + "#"));
    }

    private static String key(LocalDate workDate, Long sessionId) {
        return workDate + "#" + sessionId;
    }
}
