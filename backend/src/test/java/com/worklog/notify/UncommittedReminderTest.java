package com.worklog.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worklog.auth.User;
import com.worklog.github.Repo;
import com.worklog.vscode.UncommittedFile;
import com.worklog.vscode.VscodeSession;
import com.worklog.vscode.VscodeSessionRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UncommittedReminderTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 10);
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-10T18:00:00+09:00");

    private VscodeSessionRepository sessionRepository;
    private NotifyService notifyService;
    private UncommittedReminder reminder;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(VscodeSessionRepository.class);
        notifyService = mock(NotifyService.class);
        when(notifyService.notifyUncommitted(any(), any())).thenReturn(true);
        reminder = new UncommittedReminder(sessionRepository, notifyService);
    }

    private static VscodeSession session(Long id, OffsetDateTime lastCommitAt, int lines) {
        User user = new User();
        user.setId(1L);
        user.setLogin("UngsikJo");

        Repo repo = new Repo();
        repo.setFullName("uxis-co-kr/2026-pknu-3day");

        VscodeSession s = new VscodeSession();
        s.setId(id);
        s.setUser(user);
        s.setRepo(repo);
        s.setBranch("feature/attendance");
        s.setWorkDate(DAY);
        s.setLastCommitAt(lastCommitAt);
        s.setReportedAt(NOW);
        s.setUncommittedFiles(List.of(new UncommittedFile("src/a.ts", lines, 0, "@@")));
        return s;
    }

    private void given(VscodeSession... sessions) {
        when(sessionRepository.findAllForRemind(DAY)).thenReturn(List.of(sessions));
    }

    @Test
    @DisplayName("6시간 지난 세션에 알린다")
    void remindsStaleSession() {
        given(session(1L, NOW.minusHours(7), 10));

        assertThat(reminder.remind(DAY, NOW)).isEqualTo(1);
        verify(notifyService).notifyUncommitted(any(), any());
    }

    @Test
    @DisplayName("조건에 안 맞는 세션은 건너뛴다")
    void skipsHealthySession() {
        given(session(1L, NOW.minusHours(1), 10));

        assertThat(reminder.remind(DAY, NOW)).isZero();
        verify(notifyService, never()).notifyUncommitted(any(), any());
    }

    @Test
    @DisplayName("같은 세션에 하루 한 번만 보낸다 — 스케줄러가 매시 돌아도 중복되지 않는다")
    void sendsOncePerDay() {
        given(session(1L, NOW.minusHours(7), 10));

        assertThat(reminder.remind(DAY, NOW)).isEqualTo(1);
        assertThat(reminder.remind(DAY, NOW.plusHours(1))).isZero();
        assertThat(reminder.remind(DAY, NOW.plusHours(2))).isZero();

        verify(notifyService, times(1)).notifyUncommitted(any(), any());
    }

    @Test
    @DisplayName("날짜가 바뀌면 다시 보낸다")
    void sendsAgainNextDay() {
        given(session(1L, NOW.minusHours(7), 10));
        reminder.remind(DAY, NOW);

        LocalDate tomorrow = DAY.plusDays(1);
        when(sessionRepository.findAllForRemind(tomorrow)).thenReturn(List.of(session(1L, NOW.minusHours(7), 10)));
        reminder.forgetBefore(tomorrow);

        assertThat(reminder.remind(tomorrow, NOW.plusDays(1))).isEqualTo(1);
        verify(notifyService, times(2)).notifyUncommitted(any(), any());
    }

    @Test
    @DisplayName("전송이 실패해도(설정 없음·꺼둠) 나머지 세션을 계속 처리한다")
    void continuesWhenSendFails() {
        when(notifyService.notifyUncommitted(any(), any())).thenReturn(false);
        given(session(1L, NOW.minusHours(7), 10), session(2L, NOW.minusHours(8), 10));

        assertThat(reminder.remind(DAY, NOW)).isZero();
        verify(notifyService, times(2)).notifyUncommitted(any(), any());
    }

    @Test
    @DisplayName("세션이 없으면 조용히 끝난다")
    void quietWhenNoSessions() {
        given();

        assertThat(reminder.remind(DAY, NOW)).isZero();
        verify(notifyService, never()).notifyUncommitted(any(), any());
    }
}
