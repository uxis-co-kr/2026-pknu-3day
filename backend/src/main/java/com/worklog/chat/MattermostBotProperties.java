package com.worklog.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 채널을 읽고 답을 쓰는 봇 계정 (TODO_0910 — Mattermost 질의 응답).
 *
 * <p>Outgoing Webhook 은 Mattermost 관리자가 켜 줘야 쓸 수 있다. 이 방식은 <b>일반 회원 계정</b>
 * 하나면 된다 — 로그인 API 는 권한이 필요 없고, 연결도 우리 쪽에서 연다. 계정을 채널에
 * 넣어 두면 그 채널의 말을 읽는다.
 */
@ConfigurationProperties(prefix = "worklog.chat.bot")
public class MattermostBotProperties {

    /** Mattermost 주소. 예: http://61.32.164.99:18065 */
    private String baseUrl = "";

    /** 봇으로 쓸 회원 아이디(또는 이메일). 비우면 봇을 켜지 않는다. */
    private String loginId = "";

    private String password = "";

    /** 새 글을 살피는 간격. 채팅이라 몇 초면 충분하다. */
    private long pollIntervalMs = 3000;

    public boolean isConfigured() {
        return !baseUrl.isBlank() && !loginId.isBlank() && !password.isBlank();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
    }

    public String getLoginId() {
        return loginId;
    }

    public void setLoginId(String loginId) {
        this.loginId = loginId == null ? "" : loginId.trim();
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password == null ? "" : password;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }
}
