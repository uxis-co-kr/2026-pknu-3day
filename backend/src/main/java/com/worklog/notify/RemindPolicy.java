package com.worklog.notify;

import com.worklog.vscode.UncommittedFile;
import com.worklog.vscode.VscodeSession;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 미커밋 작업을 "방치"로 볼 기준 (PRD F6 커밋 리마인드 · F7 이벤트 2).
 *
 * <p>같은 숫자를 두 곳에서 쓴다 — 리마인드가 알림을 보낼 때, 그리고 {@code /stats/daily} 의
 * {@code staleSessions} 가 화면에 셀 때다. 값이 어긋나면 "6시간 이상 1건"이라 보여 주면서
 * 알림은 안 가는 상태가 된다. 그래서 한 곳에만 둔다.
 */
public final class RemindPolicy {

    /** 마지막 커밋이 이만큼 지나면 방치로 본다. */
    public static final Duration STALE_AFTER = Duration.ofHours(6);

    /** 커밋 시각과 무관하게 이 줄 수를 넘으면 방치로 본다. */
    public static final int MAX_UNCOMMITTED_LINES = 300;

    private RemindPolicy() {}

    /** {@code lastCommitAt} 과 비교할 시각. 이보다 오래됐으면 방치다. */
    public static OffsetDateTime staleThreshold(OffsetDateTime now) {
        return now.minus(STALE_AFTER);
    }

    /** 커밋 이력이 아예 없는 세션도 방치로 본다. */
    public static boolean isStale(VscodeSession session, OffsetDateTime now) {
        OffsetDateTime lastCommit = session.getLastCommitAt();
        return lastCommit == null || lastCommit.isBefore(staleThreshold(now));
    }

    /** 미커밋 변경 줄 수 (추가 + 삭제). */
    public static int changedLines(VscodeSession session) {
        List<UncommittedFile> files = session.getUncommittedFiles();
        if (files == null) {
            return 0;
        }
        return files.stream()
                .mapToInt(f -> nullToZero(f.additions()) + nullToZero(f.deletions()))
                .sum();
    }

    public static boolean isTooLarge(VscodeSession session) {
        return changedLines(session) >= MAX_UNCOMMITTED_LINES;
    }

    /**
     * 알림 대상인가 — PRD F6: "미커밋 diff 가 300줄 이상이거나 마지막 커밋이 6시간 이상 지난".
     *
     * <p>미커밋 파일이 하나도 없으면 알릴 것이 없다. 두 조건 중 하나만 맞으면 대상이다.
     */
    public static boolean needsReminder(VscodeSession session, OffsetDateTime now) {
        if (changedLines(session) == 0 && emptyFileList(session)) {
            return false;
        }
        return isTooLarge(session) || isStale(session, now);
    }

    /** 마지막 커밋 이후 몇 시간이 지났는지. 커밋 이력이 없으면 세션 보고 시각을 기준으로 센다. */
    public static long hoursSinceLastCommit(VscodeSession session, OffsetDateTime now) {
        OffsetDateTime base =
                session.getLastCommitAt() != null ? session.getLastCommitAt() : session.getReportedAt();
        if (base == null) {
            return 0;
        }
        return Math.max(0, Duration.between(base, now).toHours());
    }

    private static boolean emptyFileList(VscodeSession session) {
        return session.getUncommittedFiles() == null || session.getUncommittedFiles().isEmpty();
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
