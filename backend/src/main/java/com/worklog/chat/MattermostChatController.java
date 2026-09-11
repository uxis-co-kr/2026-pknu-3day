package com.worklog.chat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.MultiValueMap;

/**
 * Mattermost 가 채널의 말을 여기로 보낸다 (Outgoing Webhook / Slash Command).
 *
 * <p>둘 다 폼 인코딩으로 {@code token, text, user_name, channel_name...} 을 보내고, 응답 JSON 의
 * {@code text} 를 채널에 올린다. 응답 본문이 비어 있으면 아무것도 올리지 않는다 — 그래서
 * 업무 일지를 묻는 말이 아니면 빈 응답을 돌려 채널의 다른 대화에 끼어들지 않는다.
 *
 * <p>인증은 JWT 가 아니라 Mattermost 가 웹훅마다 발급한 token 이다. 설정에 token 을 적어 두면
 * 일치하는 요청만 받는다. 비워 두면 누구든 부를 수 있으므로 시연 때만 그렇게 둔다.
 */
@RestController
public class MattermostChatController {

    private static final Logger log = LoggerFactory.getLogger(MattermostChatController.class);

    private final WorkLogAnswerService answerService;
    private final String expectedToken;

    public MattermostChatController(
            WorkLogAnswerService answerService,
            @Value("${worklog.chat.mattermost-token:}") String expectedToken) {
        this.answerService = answerService;
        this.expectedToken = expectedToken == null ? "" : expectedToken.trim();
        if (this.expectedToken.isEmpty()) {
            log.warn("MATTERMOST_OUTGOING_TOKEN 이 비어 있어 /chat/mattermost 를 닫아 둔다.");
        }
    }

    /** Mattermost 기본값인 폼 인코딩. */
    @PostMapping(value = "/chat/mattermost", consumes = "application/x-www-form-urlencoded")
    public ResponseEntity<?> form(@RequestParam MultiValueMap<String, String> form) {
        return handle(form.getFirst("token"), form.getFirst("text"), form.getFirst("user_name"), form.getFirst("channel_name"));
    }

    /** Outgoing Webhook 의 Content Type 을 JSON 으로 고른 경우. */
    @PostMapping(value = "/chat/mattermost", consumes = "application/json")
    public ResponseEntity<?> json(@RequestBody Map<String, Object> body) {
        return handle(str(body.get("token")), str(body.get("text")), str(body.get("user_name")), str(body.get("channel_name")));
    }

    private ResponseEntity<?> handle(String token, String text, String userName, String channel) {
        // 연결이 되는지부터 알아야 한다 — Mattermost 가 사내 주소를 막으면 여기까지 오지 않는다.
        log.info("Mattermost 요청 도착 — {}@{}: {}", userName, channel, text);
        // 이 경로는 로그인 없이 열려 있고(SecurityConfig permitAll), 답에는 **남의 업무 일지가
        // 그대로** 실린다 (WorkLogAnswerService). 설정이 비었을 때 경고만 남기고 열어 두면
        // 사내망의 누구든 이름만 적어 남의 일지를 꺼내 볼 수 있다 (BACKLOG2 §2-2).
        // 설정이 없으면 막는다 — 웹훅을 붙이는 사람은 어차피 token 을 함께 넣는다.
        if (expectedToken.isEmpty()) {
            log.warn("MATTERMOST_OUTGOING_TOKEN 이 없어 요청을 거절한다 (채널 {}).", channel);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "code", "CHAT_NOT_CONFIGURED",
                            "message", "MATTERMOST_OUTGOING_TOKEN 이 설정되지 않았습니다."));
        }
        if (!tokenMatches(token)) {
            log.warn("Mattermost token 불일치 (채널 {}).", channel);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "BAD_TOKEN", "message", "token 이 맞지 않습니다."));
        }
        Optional<String> answer = answerService.answer(text);
        if (answer.isEmpty()) {
            return ResponseEntity.ok().build();
        }
        log.info("채팅 질의 응답 — {}@{}: {}", userName, channel, text);
        return ResponseEntity.ok(Map.of(
                "response_type", "in_channel",
                "username", "WorkLog Drafter",
                "text", answer.get()));
    }

    /** 글자 수·앞자리로 정답을 좁혀 갈 수 없게 상수 시간으로 견준다. */
    private boolean tokenMatches(String token) {
        if (token == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
