package com.worklog.chat;

import com.worklog.config.ApiException;
import java.net.ConnectException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

/**
 * 봇 계정으로 채널을 읽고, 업무 일지를 묻는 말에 답을 쓴다.
 *
 * <p>몇 초마다 "내가 들어가 있는 채널"의 새 글을 본다. WebSocket 이 더 빠르지만 끊김·재접속을
 * 다뤄야 하고, 채팅은 3초 늦어도 아무도 모른다. 기동 시점 이전의 글은 답하지 않는다 —
 * 서버를 다시 띄울 때마다 지난 질문에 우르르 답하면 곤란하다.
 *
 * <p>설정은 매 주기 {@link ChatBotSettingsService} 에 묻는다. 관리자가 콘솔에서 계정을 바꾸면
 * 다음 주기에 그 계정으로 다시 로그인한다.
 */
@Component
public class MattermostBot {

    private static final Logger log = LoggerFactory.getLogger(MattermostBot.class);

    /** 채널 목록은 글보다 훨씬 드물게 바뀐다 — 새 채널에 초대되면 이 안에 알아챈다. */
    private static final long CHANNEL_REFRESH_MS = 60_000;

    private final ChatBotSettingsService settings;
    private final MattermostClient client;
    private final WorkLogAnswerService answers;

    private String token;
    private String sessionKey; // 어느 설정으로 로그인했는지 — 바뀌면 다시 로그인한다
    private String myUserId;
    private String myUsername;
    private List<MattermostClient.MmChannel> channels = List.of();
    private long channelsLoadedAt;
    /** 채널별로 마지막에 본 글 시각(epoch ms). */
    private final Map<String, Long> lastSeen = new HashMap<>();
    /** 같은 글에 두 번 답하지 않는다 — since 조회는 경계의 글을 다시 줄 수 있다. */
    private final Set<String> answered = new LinkedHashSet<>();
    private final Map<String, ChannelStat> stats = new HashMap<>();
    private Instant lastPollAt;
    private String lastError;
    private long lastFailureLogAt;

    public MattermostBot(ChatBotSettingsService settings, MattermostClient client, WorkLogAnswerService answers) {
        this.settings = settings;
        this.client = client;
        this.answers = answers;
    }

    @Scheduled(fixedDelayString = "${worklog.chat.bot.poll-interval-ms:3000}", initialDelay = 5000)
    public synchronized void poll() {
        ChatBotSettingsService.Effective cfg = settings.effective();
        if (!cfg.configured()) {
            if (token != null) {
                log.info("Mattermost 봇을 끈다.");
                disconnect();
            }
            return;
        }
        try {
            ensureSession(cfg);
            refreshChannelsIfStale(cfg);
            for (MattermostClient.MmChannel channel : channels) {
                if (cfg.watches(channel.id())) {
                    pollChannel(cfg, channel);
                }
            }
            lastPollAt = Instant.now();
            lastError = null;
        } catch (MattermostClient.Unauthorized e) {
            log.info("Mattermost 세션이 만료돼 다시 로그인한다.");
            token = null;
        } catch (Exception e) {
            lastError = describe(e);
            // 연결이 잠깐 끊긴 것으로 로그를 도배하지 않는다.
            long now = System.currentTimeMillis();
            if (now - lastFailureLogAt > 60_000) {
                log.warn("Mattermost 봇 오류: {}", lastError);
                lastFailureLogAt = now;
            }
        }
    }

    /**
     * 관리자가 콘솔에서 "연결"을 눌렀을 때 — 기다리지 않고 지금 로그인해 본다.
     *
     * @throws ApiException 로그인이 안 되면 이유를 담아서. 화면이 그대로 보여 준다
     */
    public synchronized Map<String, Object> connectNow() {
        ChatBotSettingsService.Effective cfg = settings.effective();
        if (!cfg.configured()) {
            throw ApiException.badRequest("BOT_NOT_CONFIGURED", "서버 주소·아이디·비밀번호를 먼저 저장해 주세요.");
        }
        disconnect();
        try {
            ensureSession(cfg);
            channelsLoadedAt = 0;
            refreshChannelsIfStale(cfg);
            lastPollAt = Instant.now();
            lastError = null;
        } catch (MattermostClient.Unauthorized e) {
            lastError = "아이디 또는 비밀번호가 맞지 않습니다.";
            throw new ApiException(HttpStatus.BAD_REQUEST, "MATTERMOST_LOGIN_FAILED", lastError);
        } catch (Exception e) {
            lastError = describe(e);
            throw new ApiException(HttpStatus.BAD_REQUEST, "MATTERMOST_UNREACHABLE", lastError);
        }
        return status();
    }

    public synchronized void disconnect() {
        token = null;
        sessionKey = null;
        myUserId = null;
        myUsername = null;
        channels = List.of();
        channelsLoadedAt = 0;
    }

    /** 지금 알고 있는 채널 id — 채널별 읽기 설정을 명시 목록으로 바꿀 때 쓴다. */
    public synchronized Set<String> knownChannelIds() {
        Set<String> ids = new LinkedHashSet<>();
        channels.forEach(c -> ids.add(c.id()));
        return ids;
    }

    private void ensureSession(ChatBotSettingsService.Effective cfg) {
        String key = cfg.baseUrl() + "|" + cfg.loginId() + "|" + cfg.password().hashCode();
        if (token != null && key.equals(sessionKey)) {
            return;
        }
        token = client.login(cfg.baseUrl(), cfg.loginId(), cfg.password());
        sessionKey = key;
        MattermostClient.MmUser me = client.me(cfg.baseUrl(), token);
        myUserId = me.id();
        myUsername = me.username();
        channelsLoadedAt = 0;
        log.info("Mattermost 로그인 성공 — @{}", myUsername);
    }

    private void refreshChannelsIfStale(ChatBotSettingsService.Effective cfg) {
        long now = System.currentTimeMillis();
        if (now - channelsLoadedAt < CHANNEL_REFRESH_MS) {
            return;
        }
        channels = client.myChannels(cfg.baseUrl(), token, myUserId);
        channelsLoadedAt = now;
        // 처음 보는 채널은 "지금부터" 본다.
        for (MattermostClient.MmChannel c : channels) {
            lastSeen.putIfAbsent(c.id(), now);
        }
        log.info("Mattermost 채널 {}곳을 본다.", channels.size());
    }

    private void pollChannel(ChatBotSettingsService.Effective cfg, MattermostClient.MmChannel channel) {
        long since = lastSeen.getOrDefault(channel.id(), System.currentTimeMillis());
        List<MattermostClient.MmPost> posts = client.postsSince(cfg.baseUrl(), token, channel.id(), since);
        for (MattermostClient.MmPost post : posts) {
            lastSeen.merge(channel.id(), post.create_at(), Math::max);
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
            client.createPost(cfg.baseUrl(), token, channel.id(), answer.get());
            stats.computeIfAbsent(channel.id(), k -> new ChannelStat()).record();
        }
        if (answered.size() > 5_000) {
            answered.clear();
        }
    }

    /** 상태 화면용 — 켜졌는지, 로그인됐는지, 채널마다 읽는지·몇 번 답했는지. */
    public synchronized Map<String, Object> status() {
        ChatBotSettingsService.Effective cfg = settings.effective();
        Map<String, Object> m = new HashMap<>();
        m.put("enabled", cfg.configured());
        m.put("connected", token != null);
        m.put("botUsername", myUsername);
        m.put("baseUrl", cfg.baseUrl());
        m.put("loginId", cfg.loginId());
        m.put("source", cfg.source());
        m.put("lastPollAt", lastPollAt == null ? null : lastPollAt.toString());
        m.put("lastError", lastError);
        m.put("checkedAt", Instant.now().toString());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MattermostClient.MmChannel c : channels) {
            ChannelStat st = stats.get(c.id());
            Map<String, Object> row = new HashMap<>();
            row.put("id", c.id());
            row.put("name", c.name());
            row.put("displayName", c.display_name());
            row.put("type", c.type());
            row.put("watching", cfg.watches(c.id()));
            row.put("answeredCount", st == null ? 0 : st.count);
            row.put("lastAnsweredAt", st == null || st.last == null ? null : st.last.toString());
            rows.add(row);
        }
        m.put("channels", rows);
        return m;
    }

    private static String describe(Exception e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root instanceof ConnectException || e instanceof ResourceAccessException) {
            return "Mattermost 서버에 닿지 않습니다: " + root.getMessage();
        }
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static final class ChannelStat {
        long count;
        Instant last;

        void record() {
            count++;
            last = Instant.now();
        }
    }
}
