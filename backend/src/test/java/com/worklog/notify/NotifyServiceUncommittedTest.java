package com.worklog.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worklog.auth.User;
import com.worklog.github.Repo;
import com.worklog.vscode.UncommittedFile;
import com.worklog.vscode.VscodeSession;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 미커밋 리마인드 문구 (PRD F7 이벤트 2).
 * "⚠️ {repo}@{branch}에 미커밋 변경 {N}파일이 {H}시간째 있습니다."
 */
class NotifyServiceUncommittedTest {

    private static final String WEBHOOK = "https://mm.example.com/hooks/x";
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-10T18:00:00+09:00");

    private Notifier notifier;
    private NotifySettingRepository settingRepository;
    private NotifyService service;

    @BeforeEach
    void setUp() {
        notifier = mock(Notifier.class);
        when(notifier.send(any(), any())).thenReturn(true);
        settingRepository = mock(NotifySettingRepository.class);
        when(settingRepository.findByUserId(anyLong())).thenReturn(Optional.empty());
        when(settingRepository.findGlobal()).thenReturn(Optional.empty());
        service = new NotifyService(notifier, settingRepository, WEBHOOK, "http://localhost:5173");
    }

    private static VscodeSession session(int fileCount, OffsetDateTime lastCommitAt) {
        User user = new User();
        user.setId(1L);
        user.setLogin("UngsikJo");

        Repo repo = new Repo();
        repo.setFullName("uxis-co-kr/2026-pknu-3day");

        VscodeSession s = new VscodeSession();
        s.setId(9L);
        s.setUser(user);
        s.setRepo(repo);
        s.setBranch("feature/attendance");
        s.setRemoteUrl("https://github.com/uxis-co-kr/2026-pknu-3day.git");
        s.setWorkDate(LocalDate.of(2026, 9, 10));
        s.setLastCommitAt(lastCommitAt);
        s.setReportedAt(NOW);
        s.setUncommittedFiles(java.util.stream.IntStream.range(0, fileCount)
                .mapToObj(i -> new UncommittedFile("src/f" + i + ".ts", 10, 0, "@@"))
                .toList());
        return s;
    }

    @Test
    @DisplayName("PRD F7 문구 그대로 만든다")
    void buildsMessage() {
        assertThat(service.notifyUncommitted(session(3, NOW.minusHours(7)), NOW)).isTrue();

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(notifier).send(eq(WEBHOOK), text.capture());

        assertThat(text.getValue())
                .isEqualTo("⚠️ uxis-co-kr/2026-pknu-3day@feature/attendance에 미커밋 변경 3파일이 7시간째 있습니다.");
    }

    @Test
    @DisplayName("등록되지 않은 리포는 원격 URL 로 대신 표기한다")
    void fallsBackToRemoteUrl() {
        VscodeSession s = session(1, NOW.minusHours(6));
        s.setRepo(null);

        service.notifyUncommitted(s, NOW);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(notifier).send(eq(WEBHOOK), text.capture());
        assertThat(text.getValue()).startsWith("⚠️ https://github.com/uxis-co-kr");
    }

    @Test
    @DisplayName("remind_uncommitted 를 끈 사용자에게는 보내지 않는다")
    void respectsOptOut() {
        NotifySetting off = new NotifySetting();
        off.setRemindUncommitted(false);
        when(settingRepository.findByUserId(1L)).thenReturn(Optional.of(off));

        assertThat(service.notifyUncommitted(session(3, NOW.minusHours(7)), NOW)).isFalse();
        verify(notifier, never()).send(any(), any());
    }

    @Test
    @DisplayName("설정 행이 없으면 기본은 켜짐")
    void defaultsToEnabled() {
        assertThat(service.remindEnabled(1L)).isTrue();
    }

    @Test
    @DisplayName("소유자가 없는 세션에는 보내지 않는다 — 보낼 곳이 없다")
    void skipsWhenNoOwner() {
        VscodeSession s = session(1, NOW.minusHours(7));
        s.setUser(null);

        assertThat(service.notifyUncommitted(s, NOW)).isFalse();
        verify(notifier, never()).send(any(), any());
    }
}
