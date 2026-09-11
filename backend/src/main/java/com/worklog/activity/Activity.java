package com.worklog.activity;

import com.worklog.auth.User;
import com.worklog.github.Repo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * GitHub 에서 수집한 활동 1건 — 커밋 / PR 생성 / PR 머지 (PRD F1).
 *
 * <p>(repo, type, externalId) 가 중복 방지 키다. 같은 커밋을 두 번 동기화해도 row 가 늘지 않아야 한다.
 */
@Entity
@Table(
        name = "activities",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_activities_repo_type_external",
                columnNames = {"repo_id", "type", "external_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repo_id", nullable = false)
    private Repo repo;

    /** 서비스에 가입한 사용자와 매칭된 경우만 채워진다. 미가입자는 null + externalLogin. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "external_login", length = 100)
    private String externalLogin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActivityType type;

    /** 커밋은 sha, PR 은 PR 번호. */
    @Column(name = "external_id", nullable = false, length = 200)
    private String externalId;

    @Column(length = 64)
    private String sha;

    @Column(columnDefinition = "text")
    private String title;

    @Column(columnDefinition = "text")
    private String message;

    @Column(columnDefinition = "text")
    private String url;

    @Column(length = 200)
    private String branch;

    @Column(name = "files_changed", nullable = false)
    private Integer filesChanged = 0;

    @Column(nullable = false)
    private Integer additions = 0;

    @Column(nullable = false)
    private Integer deletions = 0;

    /** 파일당 200줄, 커밋당 3,000자로 잘라 저장한다 (PRD F1-4). */
    @Column(name = "raw_diff", columnDefinition = "text")
    private String rawDiff;

    /** 변경 파일 목록 (V11, F-2). 본문은 rawDiff, 목록·통계는 여기. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private java.util.List<ChangedFile> files = new java.util.ArrayList<>();

    @Column(columnDefinition = "text")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "summary_status", nullable = false, length = 20)
    private SummaryStatus summaryStatus = SummaryStatus.PENDING;

    @Column(name = "summary_retries", nullable = false)
    private Integer summaryRetries = 0;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
