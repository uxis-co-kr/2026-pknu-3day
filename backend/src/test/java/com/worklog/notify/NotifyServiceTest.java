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
import com.worklog.draft.Draft;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotifyServiceTest {

    private static final String ENV_URL = "https://mm.example.com/hooks/env";
    private static final String GLOBAL_URL = "https://mm.example.com/hooks/global";
    private static final String USER_URL = "https://mm.example.com/hooks/user";

    private Notifier notifier;
    private NotifySettingRepository settingRepository;

    @BeforeEach
    void setUp() {
        notifier = mock(Notifier.class);
        when(notifier.send(any(), any())).thenReturn(true);
        settingRepository = mock(NotifySettingRepository.class);
        when(settingRepository.findByUserId(anyLong())).thenReturn(Optional.empty());
        when(settingRepository.findGlobal()).thenReturn(Optional.empty());
    }

    private NotifyService service() {
        return new NotifyService(notifier, settingRepository, ENV_URL, "http://localhost:5173");
    }

    private static NotifySetting setting(String url) {
        NotifySetting s = new NotifySetting();
        s.setMattermostWebhookUrl(url);
        return s;
    }

    private static Draft draft() {
        User user = new User();
        user.setId(3L);
        user.setLogin("taeil");
        user.setName("배태일");

        Draft draft = new Draft();
        draft.setId(7L);
        draft.setUser(user);
        draft.setWorkDate(LocalDate.of(2026, 9, 10));
        draft.setContentMd("""
                # 2026-09-10 업무 일지 — 배태일

                ## 완료한 작업
                - [repo] 첫째 줄
                - [repo] 둘째 줄
                - [repo] 셋째 줄
                - [repo] 넷째 줄

                ## 진행 중 / 미커밋
                - (미커밋 작업 없음)
                """);
        return draft;
    }

    @Test
    @DisplayName("사용자별 설정이 전역·환경변수보다 우선한다")
    void prefersPerUserWebhook() {
        when(settingRepository.findByUserId(3L)).thenReturn(Optional.of(setting(USER_URL)));
        when(settingRepository.findGlobal()).thenReturn(Optional.of(setting(GLOBAL_URL)));

        assertThat(service().webhookUrlFor(3L)).isEqualTo(USER_URL);
    }

    @Test
    @DisplayName("사용자 설정이 없으면 전역 설정을 쓴다")
    void fallsBackToGlobal() {
        when(settingRepository.findGlobal()).thenReturn(Optional.of(setting(GLOBAL_URL)));

        assertThat(service().webhookUrlFor(3L)).isEqualTo(GLOBAL_URL);
    }

    @Test
    @DisplayName("설정이 하나도 없으면 환경변수 값을 쓴다")
    void fallsBackToEnv() {
        assertThat(service().webhookUrlFor(3L)).isEqualTo(ENV_URL);
    }

    @Test
    @DisplayName("빈 문자열 설정은 없는 것으로 본다")
    void treatsBlankAsMissing() {
        when(settingRepository.findByUserId(3L)).thenReturn(Optional.of(setting("  ")));

        assertThat(service().webhookUrlFor(3L)).isEqualTo(ENV_URL);
    }

    @Test
    @DisplayName("어디에도 URL 이 없으면 보내지 않는다 — 예외를 던지지 않는다")
    void doesNothingWithoutUrl() {
        NotifyService service =
                new NotifyService(notifier, settingRepository, "", "http://localhost:5173");

        assertThat(service.notifyDraftCreated(draft())).isFalse();
        verify(notifier, never()).send(any(), any());
    }

    @Test
    @DisplayName("초안 생성 알림에 이름·날짜·링크와 완료 작업 3줄이 들어간다")
    void buildsCreatedMessage() {
        assertThat(service().notifyDraftCreated(draft())).isTrue();

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(notifier).send(eq(ENV_URL), text.capture());

        assertThat(text.getValue())
                .contains("📝 배태일의 2026-09-10 업무 일지 초안이 생성되었습니다.")
                .contains("http://localhost:5173/drafts/7")
                .contains("- [repo] 첫째 줄")
                .contains("- [repo] 셋째 줄")
                .doesNotContain("넷째 줄");
    }

    @Test
    @DisplayName("전송 버튼은 초안 전체 Markdown 을 그대로 보낸다")
    void sendsFullContent() {
        Draft draft = draft();

        assertThat(service().notifyDraftContent(draft)).isTrue();

        verify(notifier).send(ENV_URL, draft.getContentMd());
    }

    @Test
    @DisplayName("전송이 실패해도 예외를 던지지 않고 false 를 돌려준다")
    void survivesSendFailure() {
        when(notifier.send(any(), any())).thenReturn(false);

        assertThat(service().notifyDraftCreated(draft())).isFalse();
    }

    @Test
    @DisplayName("완료한 작업이 없으면 미리보기는 비어 있다")
    void emptyPreviewWhenNoWork() {
        assertThat(NotifyService.previewOf("# 제목\n\n## 완료한 작업\n- (기록된 활동 없음)\n"))
                .isEqualTo("- (기록된 활동 없음)");
        assertThat(NotifyService.previewOf("# 제목만 있는 문서")).isEmpty();
    }
}
