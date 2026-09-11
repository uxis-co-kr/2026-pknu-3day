package com.worklog.chat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Mattermost REST API v4 중 봇이 쓰는 다섯 가지 — 로그인, 나, 내 채널, 채널의 새 글, 글 쓰기.
 *
 * <p>세션 토큰은 로그인 응답 헤더 {@code Token} 으로 온다. 만료되면 401 이 나므로 호출하는 쪽이
 * 다시 로그인한다 ({@link Unauthorized}).
 */
@Component
public class MattermostClient {

    /** 봇이 쓴 글에 붙이는 표시 (post.props). */
    public static final String WORKLOG_PROP = "worklog_bot";

    private final RestClient restClient;

    public MattermostClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .messageConverters(c -> c.add(0, new MappingJackson2HttpMessageConverter(mapper)))
                .build();
    }

    /** @return 세션 토큰 */
    public String login(String baseUrl, String loginId, String password) {
        ResponseEntity<Map> res;
        try {
            res = restClient
                    .post()
                    .uri(baseUrl + "/api/v4/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("login_id", loginId, "password", password))
                    .retrieve()
                    .toEntity(Map.class);
        } catch (HttpClientErrorException e) {
            throw translate(e); // 401 → Unauthorized: "아이디 또는 비밀번호가 맞지 않습니다"
        }
        String token = res.getHeaders().getFirst("Token");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("로그인 응답에 Token 헤더가 없다.");
        }
        return token;
    }

    public MmUser me(String baseUrl, String token) {
        return get(baseUrl + "/api/v4/users/me", token, MmUser.class);
    }

    /** 봇이 들어가 있는 채널 전부 (모든 팀). */
    public List<MmChannel> myChannels(String baseUrl, String token, String userId) {
        List<MmChannel> all = new ArrayList<>();
        MmTeam[] teams = get(baseUrl + "/api/v4/users/" + userId + "/teams", token, MmTeam[].class);
        for (MmTeam team : teams) {
            MmChannel[] channels = get(
                    baseUrl + "/api/v4/users/" + userId + "/teams/" + team.id() + "/channels", token, MmChannel[].class);
            for (MmChannel c : channels) {
                all.add(c);
            }
        }
        return all;
    }

    /** {@code since} (epoch ms) 이후에 생기거나 바뀐 글. 오래된 순으로 준다. */
    public List<MmPost> postsSince(String baseUrl, String token, String channelId, long since) {
        PostList list = get(
                baseUrl + "/api/v4/channels/" + channelId + "/posts?since=" + since, token, PostList.class);
        List<MmPost> posts = new ArrayList<>();
        if (list == null || list.order() == null) {
            return posts;
        }
        for (String id : list.order()) {
            MmPost p = list.posts().get(id);
            if (p != null) {
                posts.add(p);
            }
        }
        posts.sort((a, b) -> Long.compare(a.create_at(), b.create_at()));
        return posts;
    }

    /** @return 쓴 글의 id. 그 글에 반응을 달려면 필요하다 */
    public String createPost(String baseUrl, String token, String channelId, String message) {
        try {
            Map<?, ?> created = restClient
                    .post()
                    .uri(baseUrl + "/api/v4/posts")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    // 우리가 쓴 답에는 표시를 남긴다. 봇 계정이 사람 계정과 같을 수 있어서
                    // "내 글"이 아니라 "이 표시가 있는 글"을 무시해야 한다.
                    .body(Map.of("channel_id", channelId, "message", message, "props", Map.of(WORKLOG_PROP, true)))
                    .retrieve()
                    .body(Map.class);
            return created == null ? null : String.valueOf(created.get("id"));
        } catch (HttpClientErrorException e) {
            throw translate(e);
        }
    }

    /**
     * 글에 이모지 반응을 단다 — 사람이 누르기만 하면 되도록 봇이 미리 달아 둔다 (버튼 대신).
     *
     * <p>버튼(interactive message)은 <b>Mattermost 서버가 우리를</b> 불러야 해서 사내망 설정이
     * 필요했다. 반응은 방향이 반대라 — 우리가 쓰고 우리가 읽는다 — 아무 설정 없이 된다.
     */
    public void addReaction(String baseUrl, String token, String userId, String postId, String emojiName) {
        try {
            restClient
                    .post()
                    .uri(baseUrl + "/api/v4/reactions")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("user_id", userId, "post_id", postId, "emoji_name", emojiName))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            throw translate(e);
        }
    }

    /** 그 글에 달린 반응 전부 — 누가 어떤 이모지를 눌렀는지. */
    public List<MmReaction> reactions(String baseUrl, String token, String postId) {
        MmReaction[] found = get(
                baseUrl + "/api/v4/posts/" + postId + "/reactions", token, MmReaction[].class);
        return found == null ? List.of() : List.of(found);
    }

    public record MmReaction(String user_id, String post_id, String emoji_name, long create_at) {}

    private <T> T get(String url, String token, Class<T> type) {
        try {
            return restClient.get().uri(url).header("Authorization", "Bearer " + token).retrieve().body(type);
        } catch (HttpClientErrorException e) {
            throw translate(e);
        }
    }

    private static RuntimeException translate(HttpClientErrorException e) {
        if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            return new Unauthorized();
        }
        return e;
    }

    /** 세션이 죽었다 — 다시 로그인하면 된다. */
    public static class Unauthorized extends RuntimeException {
        Unauthorized() {
            super("Mattermost 세션이 만료됐다.");
        }
    }

    public record MmUser(String id, String username) {}

    public record MmTeam(String id, String name) {}

    public record MmChannel(String id, String name, String display_name, String type) {}

    public record MmPost(
            String id,
            String user_id,
            String channel_id,
            String message,
            long create_at,
            String type,
            Map<String, Object> props) {

        /** 우리 봇이 쓴 답인지. */
        public boolean fromWorklogBot() {
            return props != null && Boolean.TRUE.equals(props.get(WORKLOG_PROP));
        }

        /**
         * 사람이 아닌 것이 쓴 글인지 — 우리 봇의 답, Incoming Webhook, 봇 계정.
         *
         * <p>"OOO의 업무일지가 요약되었습니다" 라는 웹훅 알림에 봇이 "업무일지" 와 이름을 보고
         * 일지를 통째로 답한 적이 있다 (9/11). Mattermost 는 웹훅 글에 {@code from_webhook},
         * 봇 계정 글에 {@code from_bot} 을 문자열 "true" 로 붙인다.
         */
        public boolean fromAnyBot() {
            if (fromWorklogBot()) {
                return true;
            }
            return props != null && (isTrue(props.get("from_webhook")) || isTrue(props.get("from_bot")));
        }

        private static boolean isTrue(Object value) {
            return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
        }
    }

    record PostList(List<String> order, Map<String, MmPost> posts) {}
}
