package com.worklog.draft;

import com.worklog.auth.User;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 하루치 업무 일지 초안 (PRD F3).
 *
 * <p><b>공동 소유 엔티티.</b> 1일차 오전 공통 작업으로 확정했으므로, 필드 추가·변경은 두 담당자 합의 후에만 한다
 * (PRD 2. 컨플릭트 방지). 생성 로직은 담당자 2의 {@code DraftGenerator}, 조회·수정·확정 API 는 담당자 1이 맡는다.
 *
 * <p>같은 (user, workDate) 에 재생성하면 덮어쓰지 않고 version 을 올려 새 행을 만든다.
 */
@Entity
@Table(
        name = "drafts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_drafts_user_date_version",
                columnNames = {"user_id", "work_date", "version"}))
@Getter
@Setter
@NoArgsConstructor
public class Draft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** KST 기준 업무 일자. */
    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(nullable = false)
    private Integer version = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DraftStatus status = DraftStatus.DRAFT;

    @Column(name = "content_md", nullable = false, columnDefinition = "text")
    private String contentMd;

    /** 이 초안의 근거가 된 activity id 목록. 초안 편집 화면 우측에 함께 보여준다. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "source_activity_ids", nullable = false, columnDefinition = "bigint[]")
    private Long[] sourceActivityIds = new Long[0];

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "source_session_ids", nullable = false, columnDefinition = "bigint[]")
    private Long[] sourceSessionIds = new Long[0];

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
