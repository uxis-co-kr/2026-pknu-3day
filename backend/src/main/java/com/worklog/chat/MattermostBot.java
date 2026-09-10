package com.worklog.chat;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 봇 계정으로 채널을 읽고, 업무 일지를 묻는 말에 답을 쓴다.
 *
 * <p>몇 초마다 "내가 들어가 있는 채널"의 새 글을 본다. WebSocket 이 더 빠르지만 끊김·재접속을
 * 다뤄야 하고, 채팅은 3초 늦어도 아무도 모른다. 기동 시점 이전의 글은 답하지 않는다 —
 * 서버를 다시 띄울 때마다 지난 질문에 우르르 답하면 곤란하다.
 */
@Component
public class MattermostBot {

    private static final Logger log = LoggerFactory.getLogger(MattermostBot.class);

    /** 채널 목록은 글보다 훨씬 드물게 바뀐다 — 새 채널에 초대되면 이 안에 알아챈다. */
    private static final long CHANNEL_REFRESH_MS = 60_000;

    private final MattermostBotProperties props;
    private final MattermostClient client;
    private final WorkLogAnswerService answers;

    private String token;
    private String myUserId;
    private String myUsername;
    private List<MattermostClient.MmChannel> channels = List.of();
    private long channelsLoadedAt;
    /** 채널별로 마지막에 본 글 시각(epoch ms). */
    private final Map<String, Long> lastSeen = new HashMap<>();
    /** 같은 글에 두 번 답하지 않는다 — since 조회는 경계의 글을 다시 줄 수 있다. */
    private final Set<String> answered = new LinkedHashSet<>();
    private long lastFailureLogAt;

    public MattermostBot(MattermostBotProperties props, MattermostClient client, WorkLogAnswerService answers) {
        this.props = props;
        this.client = client;
        this.answers = answers;
        if (props.isConfigured()) {
            log.info("Mattermost 봇 켬 — {} 로 {} 에 로그인해 채널을 읽는다.", props.getLoginId(), props.getBaseUrl());
        } else {
            log.info("Mattermost 봇 끔 — MATTERMOST_BOT_LOGIN_ID / PASSWORD 가 비어 있다.");
        }
    }

    public boolean isEnabled() {
        return props.isConfigured();
    }

    public boolean isConnected() {
        return token != null;
    }

    public String botUsername() {
        return myUsername;
    }

    public List<String> watchedChannels() {
        return channels.stream().map(c -> c.display_name() == null || c.display_name().isBlank() ? c.name() : c.display_name()).toList();
    }

    @Scheduled(fixedDelayString = "${worklog.chat.bot.poll-interval-ms:3000}", initialDelay = 5000)
    public void poll() {
        if (!props.isConfigured()) {
            return;
        }
        try {
            ensureSession();
            refreshChannelsIfStale();
            for (MattermostClient.MmChannel channel : channels) {
                pollChannel(channel);
            }
        } catch (MattermostClient.Unauthorized e) {
            log.info("Mattermost 세션이 만료돼 다시 로그인한다.");
            token = null;
        } catch (Exception e) {
            // 연결이 잠깐 끊긴 것으로 로그를 도배하지 않는다.
            long now = System.currentTimeMillis();
            if (now - lastFailureLogAt > 60_000) {
                log.warn("Mattermost 봇 오류: {}", e.getMessage());
                lastFailureLogAt = now;
            }
        }
    }

    private void ensureSession() {
        if (token != null) {
            return;
        }
        token = client.login(props.getBaseUrl(), props.getLoginId(), props.getPassword());
        MattermostClient.MmUser me = client.me(props.getBaseUrl(), token);
        myUserId = me.id();
        myUsername = me.username();
        channelsLoadedAt = 0;
        log.info("Mattermost 로그인 성공 — @{}", myUsername);
    }

    private void refreshChannelsIfStale() {
        long now = System.currentTimeMillis();
        if (now - channelsLoadedAt < CHANNEL_REFRESH_MS) {
            return;
        }
        channels = client.myChannels(props.getBaseUrl(), token, myUserId);
        channelsLoadedAt = now;
        // 처음 보는 채널은 "지금부터" 본다.
        for (MattermostClient.MmChannel c : channels) {
            lastSeen.putIfAbsent(c.id(), now);
        }
        log.info("Mattermost 채널 {}곳을 본다: {}", channels.size(), watchedChannels());
    }

    private void pollChannel(MattermostClient.MmChannel channel) {
        long since = lastSeen.getOrDefault(channel.id(), System.currentTimeMillis());
        List<MattermostClient.MmPost> posts = client.postsSince(props.getBaseUrl(), token, channel.id(), since);
        for (MattermostClient.MmPost post : posts) {
            lastSeen.merge(channel.id(), post.create_at(), Math::max);
            if (post.create_at() <= since && answered.contains(post.id())) {
                continue;
            }
            if (!answered.add(post.id())) {
                continue;
            }
            if (post.fromWorklogBot()) {
                continue; // 봇이 쓴 답 — 답에 또 답하면 무한 반복이다. 봇 계정이 사람 계정과 같을 수 있어 user_id 로 거르지 않는다
            }
            if (post.type() != null && !post.type().isBlank()) {
                continue; // system_join_channel 같은 시스템 글
            }
            Optional<String> answer = answers.answer(post.message());
            if (answer.isEmpty()) {
                continue;
            }
            log.info("채팅 질의 응답 — #{}: {}", channel.display_name(), post.message());
            client.createPost(props.getBaseUrl(), token, channel.id(), answer.get());
        }
        if (answered.size() > 5_000) {
            answered.clear();
        }
    }

    /** 상태 화면용. */
    public Map<String, Object> status() {
        Map<String, Object> m = new HashMap<>();
        m.put("enabled", isEnabled());
        m.put("connected", isConnected());
        m.put("botUsername", myUsername);
        m.put("channels", watchedChannels());
        m.put("baseUrl", props.getBaseUrl());
        m.put("checkedAt", Instant.now().toString());
        return m;
    }
}
