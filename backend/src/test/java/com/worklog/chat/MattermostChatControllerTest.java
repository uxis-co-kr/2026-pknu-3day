package com.worklog.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;

class MattermostChatControllerTest {

    private final WorkLogAnswerService answers = mock(WorkLogAnswerService.class);

    private ResponseEntity<?> post(MattermostChatController c, String token, String text) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);
        form.add("text", text);
        form.add("user_name", "ungsikjo");
        form.add("channel_name", "test");
        return c.form(form);
    }

    @Test
    @DisplayName("token 이 설정돼 있으면 일치하는 요청만 받는다")
    void checksToken() {
        MattermostChatController c = new MattermostChatController(answers, "secret");
        when(answers.answer("조웅식 오늘 업무일지")).thenReturn(Optional.of("답"));

        assertThat(post(c, "wrong", "조웅식 오늘 업무일지").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post(c, "secret", "조웅식 오늘 업무일지").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("답할 말이 없으면 빈 200 — Mattermost 는 아무것도 올리지 않는다")
    void emptyWhenNotAQuestion() {
        MattermostChatController c = new MattermostChatController(answers, "");
        when(answers.answer("점심")).thenReturn(Optional.empty());

        ResponseEntity<?> res = post(c, null, "점심");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNull();
    }

    @Test
    @DisplayName("답은 채널 전체에 보이는 in_channel 로 돌려준다")
    void repliesInChannel() {
        MattermostChatController c = new MattermostChatController(answers, "");
        when(answers.answer("조웅식 오늘 업무일지")).thenReturn(Optional.of("답"));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) post(c, null, "조웅식 오늘 업무일지").getBody();
        assertThat(body).containsEntry("text", "답").containsEntry("response_type", "in_channel");
    }
}
