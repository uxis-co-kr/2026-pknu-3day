package com.worklog.notify;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MattermostNotifierTest {

    @Test
    @DisplayName("Incoming Webhook 주소를 알아본다")
    void acceptsWebhookUrl() {
        assertThat(MattermostNotifier.looksLikeWebhookUrl(
                "https://mattermost.example.com/hooks/abcdefghijklmnopqrstuvwxyz")).isTrue();
        assertThat(MattermostNotifier.looksLikeWebhookUrl(
                "http://mm.internal:8065/hooks/xyz")).isTrue();
        assertThat(MattermostNotifier.looksLikeWebhookUrl(
                "  https://mattermost.example.com/hooks/abc  ")).isTrue();
    }

    @Test
    @DisplayName("채널을 브라우저에서 연 주소는 거른다 — 실제로 넣어 본 실수다")
    void rejectsChannelUrl() {
        assertThat(MattermostNotifier.looksLikeWebhookUrl(
                "https://mattermost.example.com/withly/channels/town-square")).isFalse();
        assertThat(MattermostNotifier.looksLikeWebhookUrl(
                "https://mattermost.example.com/")).isFalse();
    }

    @Test
    @DisplayName("빈 값과 주소가 아닌 문자열도 거른다")
    void rejectsBlankAndNonUrl() {
        assertThat(MattermostNotifier.looksLikeWebhookUrl(null)).isFalse();
        assertThat(MattermostNotifier.looksLikeWebhookUrl("")).isFalse();
        assertThat(MattermostNotifier.looksLikeWebhookUrl("   ")).isFalse();
        assertThat(MattermostNotifier.looksLikeWebhookUrl("hooks/abc")).isFalse();
    }
}
