package com.worklog.vscode;

import com.worklog.auth.User;
import com.worklog.github.Repo;
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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * VS Code 확장이 보고한 미커밋 작업 세션 (PRD F6).
 *
 * <p>엔티티는 1일차 오전 공통 작업으로 만들었지만, 수신 API({@code POST /vscode/sessions}) 와 조회는
 * 담당자 1의 영역이다. (user, remoteUrl, branch, workDate) 기준으로 UPSERT 한다.
 */
@Entity
@Table(
        name = "vscode_sessions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_vscode_sessions",
                columnNames = {"user_id", "remote_url", "branch", "work_date"}))
@Getter
@Setter
@NoArgsConstructor
public class VscodeSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** remoteUrl 로 매칭된 등록 리포. 등록되지 않은 리포면 null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id")
    private Repo repo;

    @Column(name = "remote_url", nullable = false, columnDefinition = "text")
    private String remoteUrl;

    @Column(nullable = false, length = 200)
    private String branch;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "uncommitted_files", nullable = false, columnDefinition = "jsonb")
    private List<UncommittedFile> uncommittedFiles = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<TodoItem> todos = new ArrayList<>();

    /** 명령 팔레트 "WorkLog: 오늘 계획 기록" 으로 적은 메모. */
    @Column(name = "plan_note", columnDefinition = "text")
    private String planNote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "edit_timeline", nullable = false, columnDefinition = "jsonb")
    private List<EditTimelineEntry> editTimeline = new ArrayList<>();

    @Column(columnDefinition = "text")
    private String summary;

    /** 커밋 리마인드(F7-2) 판단에 쓰는 마지막 커밋 시각. */
    @Column(name = "last_commit_at")
    private OffsetDateTime lastCommitAt;

    @Column(name = "reported_at", nullable = false)
    private OffsetDateTime reportedAt;

    @PrePersist
    void onCreate() {
        if (reportedAt == null) {
            reportedAt = OffsetDateTime.now();
        }
    }
}
