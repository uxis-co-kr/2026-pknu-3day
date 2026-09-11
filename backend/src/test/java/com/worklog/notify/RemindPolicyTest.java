package com.worklog.notify;

import static org.assertj.core.api.Assertions.assertThat;

import com.worklog.vscode.UncommittedFile;
import com.worklog.vscode.VscodeSession;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PRD F6: "미커밋 diff 가 300줄 이상이거나 마지막 커밋이 6시간 이상 지난 세션".
 * 2-13 수용 기준이 "6시간 조건 강제 테스트 통과"라 경계값을 고정한다.
 */
class RemindPolicyTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-10T18:00:00+09:00");

    private static VscodeSession session(OffsetDateTime lastCommitAt, int... lineCounts) {
        VscodeSession s = new VscodeSession();
        s.setBranch("feature/attendance");
        s.setRemoteUrl("https://github.com/uxis-co-kr/2026-pknu-3day.git");
        s.setLastCommitAt(lastCommitAt);
        s.setReportedAt(NOW);
        s.setUncommittedFiles(java.util.Arrays.stream(lineCounts)
                .mapToObj(n -> new UncommittedFile("src/f" + n + ".ts", n, 0, "@@"))
                .toList());
        return s;
    }

    @Test
    @DisplayName("푸시 전 커밋이 최근이면 방치가 아니다 — 커밋은 했는데 푸시만 안 한 사람에게 '커밋하세요' 는 틀린 말이다 (V11)")
    void unpushedCommitCountsAsCommit() {
        VscodeSession s = session(null, 10);
        assertThat(RemindPolicy.isStale(s, NOW)).isTrue();

        s.setUnpushedCommits(List.of(new com.worklog.vscode.UnpushedCommit(
                "be292b4", "fix", NOW.minusHours(1))));
        assertThat(RemindPolicy.isStale(s, NOW)).isFalse();
        assertThat(RemindPolicy.hoursSinceLastCommit(s, NOW)).isEqualTo(1);

        // 시각이 없는 항목은 무시한다
        s.setUnpushedCommits(List.of(new com.worklog.vscode.UnpushedCommit("x", "y", null)));
        assertThat(RemindPolicy.isStale(s, NOW)).isTrue();
    }

    @Test
    @DisplayName("6시간 정확히 지나면 방치 — 경계는 포함이다")
    void exactlySixHoursIsStale() {
        // 5시간 59분: 아직 아니다
        assertThat(RemindPolicy.isStale(session(NOW.minusHours(5).minusMinutes(59), 10), NOW)).isFalse();
        // 6시간 1초: 방치
        assertThat(RemindPolicy.isStale(session(NOW.minusHours(6).minusSeconds(1), 10), NOW)).isTrue();
    }

    @Test
    @DisplayName("커밋 이력이 아예 없으면 방치로 본다")
    void noCommitEverIsStale() {
        assertThat(RemindPolicy.isStale(session(null, 10), NOW)).isTrue();
    }

    @Test
    @DisplayName("300줄 경계 — 299 는 아니고 300 은 대상")
    void threeHundredLineBoundary() {
        assertThat(RemindPolicy.isTooLarge(session(NOW, 299))).isFalse();
        assertThat(RemindPolicy.isTooLarge(session(NOW, 300))).isTrue();
        // 여러 파일 합산
        assertThat(RemindPolicy.isTooLarge(session(NOW, 150, 150))).isTrue();
    }

    @Test
    @DisplayName("추가와 삭제를 합쳐서 센다")
    void countsAdditionsAndDeletions() {
        VscodeSession s = new VscodeSession();
        s.setUncommittedFiles(List.of(new UncommittedFile("a.ts", 200, 150, "@@")));

        assertThat(RemindPolicy.changedLines(s)).isEqualTo(350);
        assertThat(RemindPolicy.isTooLarge(s)).isTrue();
    }

    @Test
    @DisplayName("방금 커밋했어도 300줄을 넘으면 대상 — 두 조건은 OR 다")
    void largeChangeAlertsEvenAfterRecentCommit() {
        VscodeSession s = session(NOW.minusMinutes(5), 400);

        assertThat(RemindPolicy.isStale(s, NOW)).isFalse();
        assertThat(RemindPolicy.needsReminder(s, NOW)).isTrue();
    }

    @Test
    @DisplayName("조금 고쳤어도 6시간 지났으면 대상")
    void staleAlertsEvenWithSmallChange() {
        VscodeSession s = session(NOW.minusHours(7), 3);

        assertThat(RemindPolicy.isTooLarge(s)).isFalse();
        assertThat(RemindPolicy.needsReminder(s, NOW)).isTrue();
    }

    @Test
    @DisplayName("미커밋 파일이 없으면 알릴 것이 없다 — 커밋 이력이 없어도 조용하다")
    void nothingUncommittedNeedsNoReminder() {
        VscodeSession empty = session(null);
        assertThat(RemindPolicy.needsReminder(empty, NOW)).isFalse();

        VscodeSession nullList = new VscodeSession();
        nullList.setUncommittedFiles(null);
        assertThat(RemindPolicy.needsReminder(nullList, NOW)).isFalse();
    }

    @Test
    @DisplayName("경과 시간은 마지막 커밋 기준, 없으면 보고 시각 기준")
    void hoursSinceLastCommit() {
        assertThat(RemindPolicy.hoursSinceLastCommit(session(NOW.minusHours(7), 10), NOW)).isEqualTo(7);

        VscodeSession neverCommitted = session(null, 10);
        neverCommitted.setReportedAt(NOW.minusHours(3));
        assertThat(RemindPolicy.hoursSinceLastCommit(neverCommitted, NOW)).isEqualTo(3);
    }

    @Test
    @DisplayName("통계와 리마인드가 같은 기준을 쓴다 — 화면 문구가 '6시간 이상'으로 고정돼 있다")
    void thresholdIsSharedWithStats() {
        assertThat(RemindPolicy.STALE_AFTER.toHours()).isEqualTo(6);
        assertThat(RemindPolicy.MAX_UNCOMMITTED_LINES).isEqualTo(300);
        assertThat(RemindPolicy.staleThreshold(NOW)).isEqualTo(NOW.minusHours(6));
    }
}
