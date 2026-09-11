package com.worklog.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 봇 연결 설정. DB 에 1건만 둔다 — 봇은 하나다. */
@Entity
@Table(name = "chat_bot_settings")
@Getter
@Setter
@NoArgsConstructor
public class ChatBotSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "base_url", nullable = false, columnDefinition = "text")
    private String baseUrl;

    @Column(name = "login_id", nullable = false, length = 200)
    private String loginId;

    @Column(name = "password_enc", nullable = false, columnDefinition = "text")
    private String passwordEnc;

    @Column(nullable = false)
    private Boolean enabled = true;

    /** 쉼표로 이은 채널 id. null 이면 전부. */
    @Column(name = "watched_channel_ids", columnDefinition = "text")
    private String watchedChannelIds;

    /** 업무 일지 요약 알림이 가는 대표 채널 (V10.1). NULL 이면 전역 웹훅. */
    @Column(name = "primary_channel_id", length = 100)
    private String primaryChannelId;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }
}
