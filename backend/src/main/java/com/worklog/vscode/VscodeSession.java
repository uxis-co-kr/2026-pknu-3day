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

    /** 확장의 계획 문서에 적은 오늘 계획. markdown 한 통이 하루 계획 하나다. */
    @Column(name = "plan_note", columnDefinition = "text")
    private String planNote;

    /**
     * 파일별 저장 횟수·시각. <b>2026-09-11 부터 수집하지 않는다.</b>
     *
     * <p>VS Code 의 저장 이벤트는 편집기에서 저장할 때만 와서, 파일을 디스크에 곧바로 쓰는
     * AI 도구의 변경이 한 건도 남지 않았다. 자리는 {@link #unsavedFiles} 가 물려받았고,
     * 지난 기록을 지우지 않으려고 컬럼만 남겨 둔다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "edit_timeline", nullable = false, columnDefinition = "jsonb")
    private List<EditTimelineEntry> editTimeline = new ArrayList<>();

    /** 고쳐 놓고 아직 저장하지 않은 파일 (V12). git 에 잡히지 않는 유일한 구간이다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unsaved_files", nullable = false, columnDefinition = "jsonb")
    private List<UnsavedFile> unsavedFiles = new ArrayList<>();

    /** 그 폴더에서 오간 AI 대화 (V9). 확장이 보낸다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_sessions", nullable = false, columnDefinition = "jsonb")
    private List<AiSessionSummary> aiSessions = new ArrayList<>();

    /**
     * 커밋했지만 아직 push 하지 않은 커밋 (V11).
     *
     * <p>{@code null} 은 <b>셀 수 없음</b>이다 — 한 번도 push 하지 않은 브랜치는 비교할
     * 업스트림이 없다. 빈 배열(미푸시 없음)과 뜻이 다르므로 기본값을 주지 않는다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unpushed_commits", columnDefinition = "jsonb")
    private List<UnpushedCommit> unpushedCommits;

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

    /**
     * 이 세션에 <b>적을 것이 있는가</b>.
     *
     * <p>확장은 10분마다 보낸다. 열어만 두고 아무것도 하지 않은 날에도 (저장소·브랜치·날짜만
     * 담긴) 빈 행이 생긴다. "행이 있다" 를 "일한 기록이 있다" 로 읽으면 아무것도 하지 않은
     * 날에도 AI 가 일지를 지어낸다 (9/11 확인).
     *
     * <p>미푸시 커밋의 {@code null} 은 "셀 수 없음"(업스트림 없는 브랜치)이지 기록이 아니다.
     * 저장 이벤트({@code editTimeline})도 세지 않는다 — 은퇴한 필드라 옛 행에만 남아 있고,
     * 그 찌꺼기 한 줄 때문에 아무것도 하지 않은 날의 초안이 열리면 안 된다 (9/11).
     */
    public boolean hasContent() {
        return notEmpty(uncommittedFiles)
                || notEmpty(todos)
                || notEmpty(unsavedFiles)
                || notEmpty(aiSessions)
                || notEmpty(unpushedCommits)
                || (planNote != null && !planNote.isBlank());
    }

    private static boolean notEmpty(List<?> list) {
        return list != null && !list.isEmpty();
    }
}
