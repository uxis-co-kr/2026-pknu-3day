package com.worklog.notify;

import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Mattermost Incoming Webhook 으로 메시지를 보낸다 (PRD F7, 12 — 봇 API 는 확장 계획 X1).
 */
@Component
public class MattermostNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(MattermostNotifier.class);

    private final RestClient restClient;

    public MattermostNotifier() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public boolean send(String webhookUrl, String text) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("Mattermost webhook URL 이 없어 알림을 건너뛴다.");
            return false;
        }
        try {
            restClient
                    .post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", text))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            // 알림 실패로 초안 생성이나 확정이 실패하면 안 된다 (PRD F7).
            log.warn("Mattermost 전송 실패: {}", e.getMessage());
            return false;
        }
    }
}
