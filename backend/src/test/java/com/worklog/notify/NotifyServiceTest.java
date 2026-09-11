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
        return new NotifyService(notifier, settingRepository, null, ENV_URL, "http://localhost:5173");
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
                new NotifyService(notifier, settingRepository, null, "", "http://localhost:5173");

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
    @DisplayName("전송 버튼은 관리자 채팅방(전역 웹훅)에 '요약되었습니다' 한 줄과 링크를 보낸다 — 본문은 보내지 않는다")
    void notifiesAdminThatSummaryIsDone() {
        Draft draft = draft();

        assertThat(service().notifyDraftSummarized(draft)).isTrue();

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(notifier).send(eq(ENV_URL), text.capture());
        assertThat(text.getValue())
                .contains("배태일의 ")
                .contains("2026-09-10)의 업무일지가 요약되었습니다. 확인해주시기 바랍니다.")
                .contains("http://localhost:5173/drafts/7")
                .doesNotContain("첫째 줄");
    }

    @Test
    @DisplayName("대표 채널이 있으면 봇이 그 채널에 쓰고 웹훅은 건드리지 않는다. 봇이 못 쓰면 웹훅으로")
    void prefersPrimaryChannelOverWebhook() {
        com.worklog.chat.MattermostBot bot = mock(com.worklog.chat.MattermostBot.class);
        NotifyService withBot = new NotifyService(notifier, settingRepository, bot, ENV_URL, "http://localhost:5173");
        Draft draft = draft();

        when(bot.postToPrimaryChannel(any(), any())).thenReturn(true);
        assertThat(withBot.notifyDraftSummarized(draft)).isTrue();
        verify(notifier, org.mockito.Mockito.never()).send(any(), any());
        // 봇 경로에는 예/아니오 안내가 붙고, 초안 id 로 확인 대기를 건다
        verify(bot).postToPrimaryChannel(
                org.mockito.ArgumentMatchers.contains("✅"), eq(draft.getId()));

        when(bot.postToPrimaryChannel(any(), any())).thenReturn(false);
        assertThat(withBot.notifyDraftSummarized(draft)).isTrue();
        verify(notifier).send(eq(ENV_URL), org.mockito.ArgumentMatchers.contains("업무일지가 요약되었습니다"));
    }

    @Test
    @DisplayName("확인 안내는 링크 줄 앞에 들어가고, 이모지와 글 답을 함께 알린다")
    void confirmQuestionBeforeLink() {
        String s = NotifyService.withConfirmQuestion("A의 오늘 업무일지가 요약되었습니다.\n🔗 http://x/drafts/1");
        assertThat(s).startsWith("A의 오늘 업무일지가 요약되었습니다.\n").endsWith("\n🔗 http://x/drafts/1");

        assertThat(s).contains("✅").contains("❌").contains("**예**/**아니오**라고 답해도 됩니다");
    }

    @Test
    @DisplayName("날짜 표시 — 오늘·어제는 말로, 그 밖은 날짜만")
    void dayLabel() {
        java.time.LocalDate today = java.time.LocalDate.of(2026, 9, 11);
        assertThat(NotifyService.dayLabel(today, today)).isEqualTo("오늘(2026-09-11)");
        assertThat(NotifyService.dayLabel(today.minusDays(1), today)).isEqualTo("어제(2026-09-10)");
        assertThat(NotifyService.dayLabel(today.minusDays(2), today)).isEqualTo("2026-09-09");
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
