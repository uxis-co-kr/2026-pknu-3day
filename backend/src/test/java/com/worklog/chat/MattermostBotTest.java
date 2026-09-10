package com.worklog.chat;

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

    @BeforeEach
    void setUp() {
        MattermostBotProperties props = new MattermostBotProperties();
        props.setBaseUrl(BASE + "/");
        props.setLoginId("worklog-bot");
        props.setPassword("pw");
        client = mock(MattermostClient.class);
        answers = mock(WorkLogAnswerService.class);
        bot = new MattermostBot(props, client, answers);

        when(client.login(eq(BASE), eq("worklog-bot"), eq("pw"))).thenReturn("tok");
        when(client.me(BASE, "tok")).thenReturn(new MmUser("bot-id", "worklog-bot"));
        when(client.myChannels(BASE, "tok", "bot-id")).thenReturn(List.of(new MmChannel("ch1", "test", "테스트 채널", "O")));
        when(answers.answer(anyString())).thenReturn(Optional.empty());
        when(answers.answer("조웅식 오늘 업무일지")).thenReturn(Optional.of("답"));
    }

    private MmPost post(String id, String user, String text, long at) {
        return new MmPost(id, user, "ch1", text, at, "");
    }

    @Test
    @DisplayName("남이 쓴 질문에 한 번만 답하고, 내가 쓴 글과 잡담에는 답하지 않는다")
    void answersOnceAndIgnoresSelf() {
        long later = System.currentTimeMillis() + 10_000;
        when(client.postsSince(eq(BASE), eq("tok"), eq("ch1"), anyLong()))
                .thenReturn(List.of(
                        post("p1", "someone", "조웅식 오늘 업무일지", later),
                        post("p2", "bot-id", "조웅식 오늘 업무일지", later + 1),
                        post("p3", "someone", "점심 뭐 먹지", later + 2)))
                .thenReturn(List.of(post("p1", "someone", "조웅식 오늘 업무일지", later))); // 경계의 글이 다시 온다

        bot.poll();
        bot.poll();

        verify(client, times(1)).createPost(BASE, "tok", "ch1", "답");
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
        MattermostBot off = new MattermostBot(new MattermostBotProperties(), client, answers);
        off.poll();
        verify(client, never()).login(any(), any(), any());
    }
}
