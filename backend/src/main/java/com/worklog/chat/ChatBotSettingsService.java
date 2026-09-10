package com.worklog.chat;

import com.worklog.auth.crypto.AesEncryptor;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 봇 연결 설정의 출처를 하나로 모은다 — DB 행이 있으면 그것, 없으면 .env.
 *
 * <p>봇은 매 주기 {@link #effective()} 를 물어보므로, 관리자가 콘솔에서 저장하면 다음 주기에
 * 바로 반영된다. 재시작이 필요 없다.
 */
@Service
public class ChatBotSettingsService {

    private final ChatBotSettingRepository repository;
    private final MattermostBotProperties env;
    private final AesEncryptor encryptor;

    public ChatBotSettingsService(
            ChatBotSettingRepository repository, MattermostBotProperties env, AesEncryptor encryptor) {
        this.repository = repository;
        this.env = env;
        this.encryptor = encryptor;
    }

    /**
     * 지금 봇이 써야 할 설정.
     *
     * @param baseUrl Mattermost 주소
     * @param loginId 봇 계정
     * @param password 평문 비밀번호 — 로그인에만 쓰고 밖으로 내보내지 않는다
     * @param enabled 켜져 있는지. 꺼져 있으면 나머지 값이 있어도 봇은 돌지 않는다
     * @param watched 읽을 채널 id. null 이면 전부
     * @param source 어디서 왔는지 — 화면에 알려 준다 (db / env / none)
     */
    public record Effective(
            String baseUrl, String loginId, String password, boolean enabled, Set<String> watched, String source) {

        public boolean configured() {
            return enabled && baseUrl != null && !baseUrl.isBlank()
                    && loginId != null && !loginId.isBlank()
                    && password != null && !password.isBlank();
        }

        public boolean watches(String channelId) {
            return watched == null || watched.contains(channelId);
        }
    }

    @Transactional(readOnly = true)
    public Effective effective() {
        Optional<ChatBotSetting> row = repository.findFirstByOrderByIdAsc();
        if (row.isPresent()) {
            ChatBotSetting s = row.get();
            return new Effective(
                    s.getBaseUrl(),
                    s.getLoginId(),
                    encryptor.decrypt(s.getPasswordEnc()),
                    Boolean.TRUE.equals(s.getEnabled()),
                    parse(s.getWatchedChannelIds()),
                    "db");
        }
        if (env.isConfigured()) {
            return new Effective(env.getBaseUrl(), env.getLoginId(), env.getPassword(), true, null, "env");
        }
        return new Effective(env.getBaseUrl(), env.getLoginId(), null, false, null, "none");
    }

    /**
     * 저장. 비밀번호를 비우면 저장돼 있던 값(없으면 .env 값)을 그대로 쓴다 — 화면이 비밀번호를
     * 되돌려 받지 못하니, 주소만 고칠 때 다시 치게 하면 안 된다.
     */
    @Transactional
    public Effective save(String baseUrl, String loginId, String password) {
        ChatBotSetting s = repository.findFirstByOrderByIdAsc().orElseGet(ChatBotSetting::new);
        String plain = password == null || password.isBlank() ? effective().password() : password;
        if (plain == null || plain.isBlank()) {
            throw com.worklog.config.ApiException.badRequest("PASSWORD_REQUIRED", "비밀번호를 입력해 주세요.");
        }
        s.setBaseUrl(baseUrl.trim().replaceAll("/+$", ""));
        s.setLoginId(loginId.trim());
        s.setPasswordEnc(encryptor.encrypt(plain));
        s.setEnabled(true);
        repository.save(s);
        return effective();
    }

    @Transactional
    public Effective setEnabled(boolean enabled) {
        Optional<ChatBotSetting> row = repository.findFirstByOrderByIdAsc();
        if (row.isEmpty()) {
            // .env 로만 돌던 봇을 끄는 경우 — 행을 만들어 꺼 둔다. 다시 켜면 .env 값이 필요하다.
            if (!env.isConfigured()) {
                return effective();
            }
            ChatBotSetting s = new ChatBotSetting();
            s.setBaseUrl(env.getBaseUrl());
            s.setLoginId(env.getLoginId());
            s.setPasswordEnc(encryptor.encrypt(env.getPassword()));
            s.setEnabled(enabled);
            repository.save(s);
            return effective();
        }
        row.get().setEnabled(enabled);
        repository.save(row.get());
        return effective();
    }

    /** 채널 하나를 읽을지 말지. "전부" 상태에서 하나를 끄면, 지금 보이는 채널 목록을 명시 목록으로 바꾼다. */
    @Transactional
    public Effective setWatching(String channelId, boolean watching, Set<String> allKnownChannelIds) {
        ChatBotSetting s = repository.findFirstByOrderByIdAsc().orElseGet(() -> {
            ChatBotSetting n = new ChatBotSetting();
            n.setBaseUrl(env.getBaseUrl());
            n.setLoginId(env.getLoginId());
            n.setPasswordEnc(encryptor.encrypt(env.getPassword() == null ? "" : env.getPassword()));
            return n;
        });
        Set<String> watched = parse(s.getWatchedChannelIds());
        if (watched == null) {
            watched = new LinkedHashSet<>(allKnownChannelIds);
        }
        if (watching) {
            watched.add(channelId);
        } else {
            watched.remove(channelId);
        }
        s.setWatchedChannelIds(String.join(",", watched));
        repository.save(s);
        return effective();
    }

    private static Set<String> parse(String csv) {
        if (csv == null) {
            return null;
        }
        Set<String> set = new LinkedHashSet<>();
        Arrays.stream(csv.split(",")).map(String::trim).filter(x -> !x.isEmpty()).forEach(set::add);
        return set;
    }
}
