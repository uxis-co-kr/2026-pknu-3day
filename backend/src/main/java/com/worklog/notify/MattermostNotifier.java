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

    /**
     * Incoming Webhook 주소인지 모양으로 판별한다.
     *
     * <p>채널을 브라우저에서 연 주소({@code .../<팀>/channels/<채널>})를 그대로 넣는 실수가
     * 잦다. 그 주소로 POST 하면 서버는 살아 있으니 연결은 되고 404 만 돌아와서, 무엇이
     * 잘못됐는지 알기 어렵다. 보내기 전에 걸러 낸다.
     */
    public static boolean looksLikeWebhookUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String trimmed = url.trim();
        return (trimmed.startsWith("http://") || trimmed.startsWith("https://"))
                && trimmed.contains("/hooks/");
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
