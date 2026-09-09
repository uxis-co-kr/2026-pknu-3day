package com.worklog.github;

import com.worklog.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 수집 대상으로 등록된 GitHub 리포지터리 (PRD F1).
 */
@Entity
@Table(name = "repos")
@Getter
@Setter
@NoArgsConstructor
public class Repo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner", nullable = false, length = 120)
    private String owner;

    @Column(nullable = false, length = 200)
    private String name;

    /** "owner/name" — 등록 시 사용자가 입력하는 값이자 고유 키. */
    @Column(name = "full_name", nullable = false, unique = true, length = 320)
    private String fullName;

    @Column(name = "default_branch", length = 200)
    private String defaultBranch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registered_by")
    private User registeredBy;

    /** 다음 수집의 since 파라미터. null 이면 최근 7일을 훑는다. */
    @Column(name = "last_synced_at")
    private OffsetDateTime lastSyncedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
