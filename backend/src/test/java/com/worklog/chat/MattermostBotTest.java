package com.worklog.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worklog.chat.MattermostClient.MmChannel;
import com.worklog.chat.MattermostClient.MmPost;
import com.worklog.chat.MattermostClient.MmUser;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MattermostBotTest {

    private static final String BASE = "http://mm";
    private MattermostClient client;
    private WorkLogAnswerService answers;
    private MattermostBot bot;

    private ChatBotSettingsService settings;

    @BeforeEach
    void setUp() {
        settings = mock(ChatBotSettingsService.class);
        when(settings.effective()).thenReturn(
                new ChatBotSettingsService.Effective(BASE, "worklog-bot", "pw", true, null, "db"));
        client = mock(MattermostClient.class);
        answers = mock(WorkLogAnswerService.class);
        bot = new MattermostBot(settings, client, answers);

        when(client.login(eq(BASE), eq("worklog-bot"), eq("pw"))).thenReturn("tok");
        when(client.me(BASE, "tok")).thenReturn(new MmUser("bot-id", "worklog-bot"));
        when(client.myChannels(BASE, "tok", "bot-id")).thenReturn(List.of(new MmChannel("ch1", "test", "테스트 채널", "O")));
        // 봇은 reply() 를 쓴다 — 일지가 없으면 "만들어 드릴까요" 를 낼 수 있어서다 (9/11).
        when(answers.reply(anyString())).thenReturn(Optional.empty());
        when(answers.reply("조웅식 오늘 업무일지"))
                .thenReturn(Optional.of(new WorkLogAnswerService.Reply("답", null, null)));
    }

    private MmPost post(String id, String user, String text, long at) {
        return new MmPost(id, user, "ch1", text, at, "", java.util.Map.of());
    }

    private MmPost botPost(String id, String text, long at) {
        return new MmPost(id, "bot-id", "ch1", text, at, "", java.util.Map.of(MattermostClient.WORKLOG_PROP, true));
    }

    @Test
    @DisplayName("질문에 한 번만 답하고, 봇이 쓴 답과 잡담에는 답하지 않는다 — 같은 계정이 물어도 답한다")
    void answersOnceAndIgnoresSelf() {
        long later = System.currentTimeMillis() + 10_000;
        when(client.postsSince(eq(BASE), eq("tok"), eq("ch1"), anyLong()))
                .thenReturn(List.of(
                        post("p1", "bot-id", "조웅식 오늘 업무일지", later), // 봇 계정 = 사람 계정인 경우
                        botPost("p2", "**조웅식 · 업무 일지** …", later + 1),
                        post("p3", "someone", "점심 뭐 먹지", later + 2),
                        // 웹훅 알림 — "업무일지" 와 이름이 있어도 사람이 물은 것이 아니다
                        new MmPost("p4", "hook", "ch1", "조웅식의 오늘(2026-09-11)의 업무일지가 요약되었습니다.", later + 3, "",
                                java.util.Map.of("from_webhook", "true", "override_username", "uxis")),
                        new MmPost("p5", "bot2", "ch1", "조웅식 오늘 업무일지", later + 4, "",
                                java.util.Map.of("from_bot", "true"))))
                .thenReturn(List.of(post("p1", "bot-id", "조웅식 오늘 업무일지", later))); // 경계의 글이 다시 온다

        bot.poll();
        bot.poll();

        verify(client, times(1)).createPost(BASE, "tok", "ch1", "답");
    }

    @Test
    @DisplayName("대표 채널이 있으면 로그인된 봇이 그 채널에 쓴다. 없거나 로그인 전이면 false")
    void postsToPrimaryChannel() {
        assertThat(bot.postToPrimaryChannel("알림")).isFalse(); // 로그인 전

        bot.poll(); // 로그인
        assertThat(bot.postToPrimaryChannel("알림")).isFalse(); // 대표 채널 없음
        verify(client, never()).createPost(BASE, "tok", "ch1", "알림");

        when(settings.effective()).thenReturn(
                new ChatBotSettingsService.Effective(BASE, "worklog-bot", "pw", true, null, "db", "ch1"));
        assertThat(bot.postToPrimaryChannel("알림")).isTrue();
        verify(client).createPost(BASE, "tok", "ch1", "알림");
        assertThat(bot.status().get("primaryChannelId")).isEqualTo("ch1");
    }

    @Test
    @DisplayName("알림 뒤의 '예' 에만 요약본을 보내고, '아니오' 면 취소한다. 대기가 없으면 예/아니오는 지나간다")
    void confirmsSummaryAfterNotification() {
        long later = System.currentTimeMillis() + 10_000;
        when(answers.answerDraft(7L)).thenReturn(Optional.of("**조웅식 · 2026-09-11 업무 일지** …"));
        when(settings.effective()).thenReturn(
                new ChatBotSettingsService.Effective(BASE, "worklog-bot", "pw", true, null, "db", "ch1"));

        // 대기 없음 — "예" 는 그냥 지나간다
        when(client.postsSince(eq(BASE), eq("tok"), eq("ch1"), anyLong()))
                .thenReturn(List.of(post("p0", "someone", "예", later)));
        bot.poll();
        verify(client, never()).createPost(eq(BASE), eq("tok"), eq("ch1"), anyString());

        // 알림 → 예
        assertThat(bot.postToPrimaryChannel("요약되었습니다. 예/아니오", 7L)).isTrue();
        when(client.postsSince(eq(BASE), eq("tok"), eq("ch1"), anyLong()))
                .thenReturn(List.of(post("p1", "someone", " 예! ", later + 1)));
        bot.poll();
        verify(client).createPost(BASE, "tok", "ch1", "요약본을 전송합니다.");
        verify(client).createPost(BASE, "tok", "ch1", "**조웅식 · 2026-09-11 업무 일지** …");

        // 대기는 한 번뿐 — 다시 "예" 해도 안 보낸다
        when(client.postsSince(eq(BASE), eq("tok"), eq("ch1"), anyLong()))
                .thenReturn(List.of(post("p2", "someone", "예", later + 2)));
        bot.poll();
        verify(client, times(1)).createPost(BASE, "tok", "ch1", "요약본을 전송합니다.");

        // 알림 → 아니오
        bot.postToPrimaryChannel("요약되었습니다. 예/아니오", 8L);
        when(client.postsSince(eq(BASE), eq("tok"), eq("ch1"), anyLong()))
                .thenReturn(List.of(post("p3", "someone", "아니요", later + 3)));
        bot.poll();
        verify(client).createPost(BASE, "tok", "ch1", "전송을 취소했습니다.");
        verify(answers, never()).answerDraft(8L);

        // 예/아니오가 아닌 글은 대기를 지우지 않고 보통 질문으로 본다
        bot.postToPrimaryChannel("요약되었습니다. 예/아니오", 9L);
        when(answers.answerDraft(9L)).thenReturn(Optional.of("9번"));
        when(client.postsSince(eq(BASE), eq("tok"), eq("ch1"), anyLong()))
                .thenReturn(List.of(post("p4", "someone", "조웅식 오늘 업무일지", later + 4), post("p5", "someone", "넵", later + 5)));
        bot.poll();
        verify(client).createPost(BASE, "tok", "ch1", "답");
        verify(client).createPost(BASE, "tok", "ch1", "9번");
    }

    @Test
    @DisplayName("알림을 쓰면 봇이 ✅ ❌ 를 미리 달아 둔다 — 사람은 누르기만 하면 된다")
    void addsReactionsToNotification() {
        when(settings.effective()).thenReturn(
                new ChatBotSettingsService.Effective(BASE, "worklog-bot", "pw", true, null, "db", "ch1"));
        when(client.createPost(BASE, "tok", "ch1", "알림")).thenReturn("post1");

        bot.poll();
        assertThat(bot.postToPrimaryChannel("알림", 7L)).isTrue();

        verify(client).addReaction(BASE, "tok", "bot-id", "post1", MattermostBot.YES_EMOJI);
        verify(client).addReaction(BASE, "tok", "bot-id", "post1", MattermostBot.NO_EMOJI);
    }

    @Test
    @DisplayName("사람이 ✅ 를 누르면 요약본을, ❌ 면 취소를 보낸다 — 봇이 미리 단 반응은 답으로 세지 않는다")
    void reactionDecidesSummary() {
        when(settings.effective()).thenReturn(
                new ChatBotSettingsService.Effective(BASE, "worklog-bot", "pw", true, null, "db", "ch1"));
        when(client.createPost(eq(BASE), eq("tok"), eq("ch1"), anyString())).thenReturn("post1");
        when(answers.answerDraft(7L)).thenReturn(Optional.of("요약본"));
        bot.poll();
        bot.postToPrimaryChannel("알림", 7L);

        // 봇이 단 것만 있으면 아무 일도 없다
        when(client.reactions(BASE, "tok", "post1")).thenReturn(List.of(
                new MattermostClient.MmReaction("bot-id", "post1", MattermostBot.YES_EMOJI, 1),
                new MattermostClient.MmReaction("bot-id", "post1", MattermostBot.NO_EMOJI, 1)));
        bot.poll();
        verify(client, never()).createPost(BASE, "tok", "ch1", "요약본을 전송합니다.");

        // 다른 사람이 ✅ 를 눌렀다
        when(client.reactions(BASE, "tok", "post1")).thenReturn(List.of(
                new MattermostClient.MmReaction("bot-id", "post1", MattermostBot.YES_EMOJI, 1),
                new MattermostClient.MmReaction("bot-id", "post1", MattermostBot.NO_EMOJI, 1),
                new MattermostClient.MmReaction("manager", "post1", MattermostBot.YES_EMOJI, 2)));
        bot.poll();
        verify(client).createPost(BASE, "tok", "ch1", "요약본을 전송합니다.");
        verify(client).createPost(BASE, "tok", "ch1", "요약본");

        // 대기는 한 번뿐 — 반응이 그대로 남아 있어도 다시 보내지 않는다
        bot.poll();
        verify(client, times(1)).createPost(BASE, "tok", "ch1", "요약본을 전송합니다.");
    }

    @Test
    @DisplayName("봇 계정이 사람 계정과 같으면 누를 때 반응이 토글로 사라진다 — 그것도 답으로 본다")
    void toggledOffReactionCountsAsAnswer() {
        when(settings.effective()).thenReturn(
                new ChatBotSettingsService.Effective(BASE, "worklog-bot", "pw", true, null, "db", "ch1"));
        when(client.createPost(eq(BASE), eq("tok"), eq("ch1"), anyString())).thenReturn("post1");
        when(answers.answerDraft(7L)).thenReturn(Optional.of("요약본"));
        bot.poll();
        bot.postToPrimaryChannel("알림", 7L);

        // 먼저 봇이 단 둘을 실제로 한 번 읽어야 한다 — 달자마자 비어 보이는 것을 "사라졌다" 로
        // 읽으면 아무도 안 눌렀는데 전송된다.
        when(client.reactions(BASE, "tok", "post1")).thenReturn(List.of(
                new MattermostClient.MmReaction("bot-id", "post1", MattermostBot.YES_EMOJI, 1),
                new MattermostClient.MmReaction("bot-id", "post1", MattermostBot.NO_EMOJI, 1)));
        bot.poll();
        verify(client, never()).createPost(BASE, "tok", "ch1", "요약본을 전송합니다.");

        // ✅ 가 사라졌다 = 같은 계정의 사람이 눌러 토글했다
        when(client.reactions(BASE, "tok", "post1")).thenReturn(List.of(
                new MattermostClient.MmReaction("bot-id", "post1", MattermostBot.NO_EMOJI, 1)));
        bot.poll();

        verify(client).createPost(BASE, "tok", "ch1", "요약본을 전송합니다.");
        verify(client).createPost(BASE, "tok", "ch1", "요약본");
    }

    @Test
    @DisplayName("일지가 없다고 답하면 ✅ ❌ 를 달고, '예' 면 그 자리에서 만들어 보여 준다")
    void offersAndCreatesDraft() {
        long later = System.currentTimeMillis() + 10_000;
        when(answers.reply("조웅식 오늘 업무일지")).thenReturn(Optional.of(
                new WorkLogAnswerService.Reply("아직 업무 일지가 없습니다. 지금 만들어 드릴까요?", 1L, java.time.LocalDate.of(2026, 9, 11))));
        when(client.createPost(eq(BASE), eq("tok"), eq("ch1"), anyString())).thenReturn("q1");
        when(client.postsSince(eq(BASE), eq("tok"), eq("ch1"), anyLong()))
                .thenReturn(List.of(post("p1", "manager", "조웅식 오늘 업무일지", later)));

        bot.poll();

        // 물어본 글에 ✅ ❌ 를 달아 둔다
        verify(client).addReaction(BASE, "tok", "bot-id", "q1", MattermostBot.YES_EMOJI);
        verify(client).addReaction(BASE, "tok", "bot-id", "q1", MattermostBot.NO_EMOJI);
        verify(client, never()).createPost(BASE, "tok", "ch1", "업무 일지를 만들었습니다.");

        // "예" → 만든다
        when(answers.createDraft(1L, java.time.LocalDate.of(2026, 9, 11))).thenReturn(Optional.of(42L));
        when(answers.answerDraft(42L)).thenReturn(Optional.of("**조웅식 · 2026-09-11 업무 일지** …"));
        when(client.postsSince(eq(BASE), eq("tok"), eq("ch1"), anyLong()))
                .thenReturn(List.of(post("p2", "manager", "예", later + 1)));
        bot.poll();

        verify(client).createPost(BASE, "tok", "ch1", "업무 일지를 만들었습니다.");
        verify(client).createPost(BASE, "tok", "ch1", "**조웅식 · 2026-09-11 업무 일지** …");
    }

    @Test
    @DisplayName("'아니오' 면 만들지 않는다")
    void declinesToCreate() {
        long later = System.currentTimeMillis() + 10_000;
        when(answers.reply("조웅식 오늘 업무일지")).thenReturn(Optional.of(
                new WorkLogAnswerService.Reply("지금 만들어 드릴까요?", 1L, java.time.LocalDate.of(2026, 9, 11))));
        when(client.createPost(eq(BASE), eq("tok"), eq("ch1"), anyString())).thenReturn("q1");
        when(client.postsSince(eq(BASE), eq("tok"), eq("ch1"), anyLong()))
                .thenReturn(List.of(post("p1", "manager", "조웅식 오늘 업무일지", later)));
        bot.poll();

        when(client.postsSince(eq(BASE), eq("tok"), eq("ch1"), anyLong()))
                .thenReturn(List.of(post("p2", "manager", "아니오", later + 1)));
        bot.poll();

        verify(client).createPost(BASE, "tok", "ch1", "만들지 않았습니다.");
        verify(answers, never()).createDraft(any(), any());
    }

    @Test
    @DisplayName("예/아니오 정규화 — 앞뒤 공백·문장부호·대소문자를 무시한다")
    void normalizesReply() {
        assertThat(MattermostBot.normalizeReply(" 예! ")).isEqualTo("예");
        assertThat(MattermostBot.normalizeReply("Yes.")).isEqualTo("yes");
        assertThat(MattermostBot.normalizeReply("아니요~")).isEqualTo("아니요");
        assertThat(MattermostBot.normalizeReply(null)).isEqualTo("");
    }

    @Test
    @DisplayName("세션이 만료되면 다음 주기에 다시 로그인한다")
    void reloginsAfterUnauthorized() {
        when(client.postsSince(eq(BASE), eq("tok"), eq("ch1"), anyLong()))
                .thenThrow(new MattermostClient.Unauthorized())
                .thenReturn(List.of());

        bot.poll(); // 401 → 토큰 버림
        bot.poll(); // 다시 로그인

        verify(client, times(2)).login(BASE, "worklog-bot", "pw");
    }

    @Test
    @DisplayName("계정이 없으면 아무것도 하지 않는다")
    void disabledWithoutCredentials() {
        when(settings.effective()).thenReturn(
                new ChatBotSettingsService.Effective("", "", null, false, null, "none"));
        bot.poll();
        verify(client, never()).login(any(), any(), any());
    }

    @Test
    @DisplayName("읽기를 끈 채널은 보지 않는다 — 상태에는 남아서 다시 켤 수 있다")
    void skipsUnwatchedChannels() {
        when(settings.effective()).thenReturn(new ChatBotSettingsService.Effective(
                BASE, "worklog-bot", "pw", true, java.util.Set.of("other"), "db"));

        bot.poll();

        verify(client, never()).postsSince(any(), any(), eq("ch1"), anyLong());
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> rows =
                (java.util.List<java.util.Map<String, Object>>) bot.status().get("channels");
        org.assertj.core.api.Assertions.assertThat(rows).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(rows.get(0)).containsEntry("watching", false);
    }

    @Test
    @DisplayName("연결 버튼 — 비밀번호가 틀리면 이유를 담아 400")
    void connectNowReportsBadCredentials() {
        when(client.login(any(), any(), any())).thenThrow(new MattermostClient.Unauthorized());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> bot.connectNow())
                .isInstanceOf(com.worklog.config.ApiException.class)
                .hasMessageContaining("아이디 또는 비밀번호");
    }

    @Test
    @DisplayName("설정이 바뀌면 다음 주기에 새 계정으로 다시 로그인한다")
    void reloginsWhenSettingsChange() {
        when(client.postsSince(any(), any(), any(), anyLong())).thenReturn(List.of());
        bot.poll();
        when(settings.effective()).thenReturn(
                new ChatBotSettingsService.Effective(BASE, "other-bot", "pw2", true, null, "db"));
        when(client.login(BASE, "other-bot", "pw2")).thenReturn("tok2");
        when(client.me(BASE, "tok2")).thenReturn(new MmUser("bot-id", "other-bot"));
        when(client.myChannels(BASE, "tok2", "bot-id")).thenReturn(List.of());

        bot.poll();

        verify(client).login(BASE, "other-bot", "pw2");
    }
}
