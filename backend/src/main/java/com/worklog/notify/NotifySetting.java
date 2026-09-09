package com.worklog.notify;

import com.worklog.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mattermost 알림 설정 (PRD F7). user 가 null 인 행은 전역 기본값이며 DB 에서 1건으로 제한된다.
 */
@Entity
@Table(name = "notify_settings")
@Getter
@Setter
@NoArgsConstructor
public class NotifySetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(name = "mattermost_webhook_url", columnDefinition = "text")
    private String mattermostWebhookUrl;

    @Column(name = "remind_uncommitted", nullable = false)
    private Boolean remindUncommitted = true;
}
