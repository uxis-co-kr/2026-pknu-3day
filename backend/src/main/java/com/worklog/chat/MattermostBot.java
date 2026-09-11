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
    /**
     * 채널별 "요약본 보낼까요?" 대기 (V10.1). 알림을 쓴 뒤 그 채널에 온 사람의 예/아니오에만 반응한다 —
     * 채널의 온갖 "예" 에 요약본을 던지면 곤란하다. 답이 오거나 시간이 지나면 지운다.
     */
    private final Map<String, PendingConfirm> pendingConfirms = new HashMap<>();
    /** 대기 시한. 이보다 지나면 예/아니오가 와도 모른 척한다. */
    static final long CONFIRM_TTL_MS = 30 * 60 * 1000L;

    private static final Set<String> YES = Set.of(
            "예", "네", "넵", "넹", "응", "어", "ㅇㅇ", "ㅇ", "yes", "y", "ok", "오케이", "전송", "보내줘", "보내주세요", "보내");
    private static final Set<String> NO = Set.of(
            "아니오", "아니요", "아니", "아냐", "아뇨", "노", "no", "n", "ㄴㄴ", "ㄴ", "취소", "안보내", "보내지마");

    /** 봇이 미리 달아 두는 "예" 이모지. 사람은 누르기만 하면 된다. */
    static final String YES_EMOJI = "white_check_mark";
    static final String NO_EMOJI = "x";
    /** 사람이 손으로 고를 수도 있으니 비슷한 것도 같은 뜻으로 본다. */
    private static final Set<String> YES_EMOJIS = Set.of(YES_EMOJI, "heavy_check_mark", "+1", "thumbsup", "o", "ok_hand");
    private static final Set<String> NO_EMOJIS = Set.of(NO_EMOJI, "-1", "thumbsdown", "no_entry", "negative_squared_cross_mark");

    /**
     * @param postId 알림 글 — 여기 달린 반응을 본다
     * @param botEmojis 봇이 미리 달아 둔 이모지. 봇 계정이 사람 계정과 같을 때
     *     (시연이 그렇다) 사람이 누르면 <b>토글로 사라지므로</b>, 사라진 것도 답으로 본다
     */
    record PendingConfirm(Long draftId, long since, String postId, Set<String> botEmojis) {}

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
            // 알림 글에 달린 ✅ ❌ — 버튼 대신 쓰는 길 (9/11). 읽기를 끈 채널이라도, 대표 채널의
            // 알림에는 답해야 한다. 우리가 직접 건 대기만 보므로 채널 설정과 무관하다.
            checkReactions(cfg);
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
        pendingConfirms.clear();
        token = null;
        sessionKey = null;
        myUserId = null;
        myUsername = null;
        channels = List.of();
        channelsLoadedAt = 0;
    }

    /**
     * 대표 채널에 글을 쓴다 — 사원이 [Mattermost 전송] 을 눌렀을 때의 "요약되었습니다" 알림 (V10.1).
     *
     * @return 대표 채널이 없거나 봇이 로그인돼 있지 않으면 false. 그러면 부르는 쪽이 웹훅으로 간다
     */
    public synchronized boolean postToPrimaryChannel(String message) {
        return postToPrimaryChannel(message, null);
    }

    /**
     * @param draftId 알림 뒤의 "예" 에 보낼 초안. 주면 그 채널에 확인 대기를 건다 (V10.1)
     */
    public synchronized boolean postToPrimaryChannel(String message, Long draftId) {
        ChatBotSettingsService.Effective cfg = settings.effective();
        String channelId = cfg.primaryChannelId();
        if (channelId == null || channelId.isBlank()) {
            return false;
        }
        if (token == null) {
            log.warn("대표 채널이 정해져 있지만 봇이 로그인돼 있지 않아 알리지 못한다.");
            return false;
        }
        try {
            String postId = client.createPost(cfg.baseUrl(), token, channelId, message);
            if (draftId != null) {
                // 사람이 누르기만 하면 되도록 봇이 ✅ ❌ 를 미리 달아 둔다. 실패해도 알림 자체는 나갔으니
                // 글로 쓴 예/아니오로 받으면 된다.
                Set<String> added = new LinkedHashSet<>();
                for (String emoji : List.of(YES_EMOJI, NO_EMOJI)) {
                    try {
                        client.addReaction(cfg.baseUrl(), token, myUserId, postId, emoji);
                        added.add(emoji);
                    } catch (Exception e) {
                        log.warn("반응 {} 을 달지 못했다: {}", emoji, describe(e));
                    }
                }
                // 뒤에 오는 반응·예/아니오는 이 알림에 대한 답이다. 새 알림이 오면 그것으로 바뀐다.
                pendingConfirms.put(channelId, new PendingConfirm(draftId, System.currentTimeMillis(), postId, added));
            }
            return true;
        } catch (Exception e) {
            lastError = describe(e);
            log.warn("대표 채널 {} 에 쓰지 못했다: {}", channelId, lastError);
            return false;
        }
    }

    /**
     * 채널 하나에 글을 쓴다 — 버튼 콜백이 요약본을 올릴 때 쓴다 (V10.1).
     *
     * @return 봇이 로그인돼 있지 않거나 쓰지 못하면 false
     */
    public synchronized boolean postToChannel(String channelId, String message) {
        if (token == null || channelId == null || channelId.isBlank()) {
            return false;
        }
        try {
            client.createPost(settings.effective().baseUrl(), token, channelId, message);
            return true;
        } catch (Exception e) {
            log.warn("채널 {} 에 쓰지 못했다: {}", channelId, describe(e));
            return false;
        }
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

    /**
     * 알림 글에 달린 반응을 본다 — 버튼 대신 쓰는 길 (9/11).
     *
     * <p>두 가지를 답으로 본다. (1) <b>봇이 아닌 사람</b>이 ✅·❌ 를 누른 것. (2) 봇이 미리 달아 둔
     * 반응이 <b>사라진</b> 것 — 봇 계정이 사람 계정과 같으면 같은 이모지를 두 번 달 수 없어서, 누르면
     * 토글로 지워진다. 시연이 그 경우다 (@ungsikjo 가 봇이자 사람).
     */
    private void checkReactions(ChatBotSettingsService.Effective cfg) {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, PendingConfirm> entry : new java.util.ArrayList<>(pendingConfirms.entrySet())) {
            String channelId = entry.getKey();
            PendingConfirm pending = entry.getValue();
            if (pending.postId() == null) {
                continue;
            }
            if (now - pending.since() > CONFIRM_TTL_MS) {
                pendingConfirms.remove(channelId);
                continue;
            }
            List<MattermostClient.MmReaction> reactions;
            try {
                reactions = client.reactions(cfg.baseUrl(), token, pending.postId());
            } catch (Exception e) {
                log.debug("반응을 읽지 못했다: {}", describe(e));
                continue;
            }

            Boolean yes = null;
            for (MattermostClient.MmReaction r : reactions) {
                if (r.user_id() != null && r.user_id().equals(myUserId)) {
                    continue; // 봇이 미리 달아 둔 것
                }
                if (YES_EMOJIS.contains(r.emoji_name())) {
                    yes = true;
                    break;
                }
                if (NO_EMOJIS.contains(r.emoji_name())) {
                    yes = false;
                    break;
                }
            }
            if (yes == null && !pending.botEmojis().isEmpty()) {
                // 봇이 달아 둔 것이 사라졌다 = 같은 계정의 사람이 눌러 토글했다
                Set<String> still = new LinkedHashSet<>();
                reactions.stream()
                        .filter(r -> r.user_id() != null && r.user_id().equals(myUserId))
                        .forEach(r -> still.add(r.emoji_name()));
                if (pending.botEmojis().contains(YES_EMOJI) && !still.contains(YES_EMOJI)) {
                    yes = true;
                } else if (pending.botEmojis().contains(NO_EMOJI) && !still.contains(NO_EMOJI)) {
                    yes = false;
                }
            }
            if (yes == null) {
                continue;
            }
            pendingConfirms.remove(channelId);
            respondToConfirm(cfg, channelId, pending.draftId(), yes);
        }
    }

    /** 예/아니오 답 하나를 처리한다 — 반응으로 왔든 글로 왔든 같다. */
    private void respondToConfirm(
            ChatBotSettingsService.Effective cfg, String channelId, Long draftId, boolean yes) {
        if (!yes) {
            log.info("요약본 전송 취소 — 초안 {}", draftId);
            client.createPost(cfg.baseUrl(), token, channelId, "전송을 취소했습니다.");
            return;
        }
        Optional<String> summary = answers.answerDraft(draftId);
        if (summary.isEmpty()) {
            client.createPost(cfg.baseUrl(), token, channelId, "요약본을 찾지 못했습니다. 일지가 지워졌을 수 있습니다.");
            return;
        }
        log.info("요약본 전송 — 초안 {}", draftId);
        client.createPost(cfg.baseUrl(), token, channelId, "요약본을 전송합니다.");
        client.createPost(cfg.baseUrl(), token, channelId, summary.get());
        stats.computeIfAbsent(channelId, k -> new ChannelStat()).record();
    }

    private void pollChannel(ChatBotSettingsService.Effective cfg, MattermostClient.MmChannel channel) {
        long since = lastSeen.getOrDefault(channel.id(), System.currentTimeMillis());
        List<MattermostClient.MmPost> posts = client.postsSince(cfg.baseUrl(), token, channel.id(), since);
        for (MattermostClient.MmPost post : posts) {
            lastSeen.merge(channel.id(), post.create_at(), Math::max);
            if (!answered.add(post.id())) {
                continue;
            }
            if (post.fromAnyBot()) {
                // 봇이 쓴 답·웹훅 알림·봇 계정 글 — 사람이 물은 것이 아니다. 답에 또 답하면 무한 반복이고,
                // "요약되었습니다" 알림에 일지를 답하면 채널이 시끄럽다. 봇 계정이 사람 계정과 같을 수 있어 user_id 로 거르지 않는다
                continue;
            }
            if (post.type() != null && !post.type().isBlank()) {
                continue; // system_join_channel 같은 시스템 글
            }
            if (handleConfirmReply(cfg, channel, post)) {
                continue;
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

    /**
     * 알림 뒤의 예/아니오 (V10.1). 대기가 없거나 시한이 지났거나 알림보다 앞선 글이면 건드리지 않는다.
     *
     * @return 이 글을 처리했으면 true — 질문 파서로 넘기지 않는다
     */
    private boolean handleConfirmReply(
            ChatBotSettingsService.Effective cfg, MattermostClient.MmChannel channel, MattermostClient.MmPost post) {
        PendingConfirm pending = pendingConfirms.get(channel.id());
        if (pending == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - pending.since() > CONFIRM_TTL_MS) {
            pendingConfirms.remove(channel.id());
            return false;
        }
        if (post.create_at() < pending.since()) {
            return false; // 알림보다 먼저 쓴 글
        }
        String reply = normalizeReply(post.message());
        if (YES.contains(reply) || NO.contains(reply)) {
            pendingConfirms.remove(channel.id());
            respondToConfirm(cfg, channel.id(), pending.draftId(), YES.contains(reply));
            return true;
        }
        return false; // 예/아니오가 아니다 — 대기는 그대로 두고 보통 글로 본다
    }

    /** "예!", "네~", " Yes. " 를 같은 답으로 본다. */
    static String normalizeReply(String message) {
        if (message == null) {
            return "";
        }
        return message.strip()
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[\\s.!?~,、。]+$", "")
                .replaceAll("^[\\s]+", "");
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
        m.put("primaryChannelId", cfg.primaryChannelId());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MattermostClient.MmChannel c : channels) {
            ChannelStat st = stats.get(c.id());
            Map<String, Object> row = new HashMap<>();
            row.put("id", c.id());
            row.put("name", c.name());
            row.put("displayName", c.display_name());
            row.put("type", c.type());
            row.put("watching", cfg.watches(c.id()));
            row.put("primary", c.id().equals(cfg.primaryChannelId()));
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
