package com.worklog.vscode;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * GET /vscode/sessions 응답. 프론트 목업 `frontend/src/mocks/vscode-sessions.json` 과
 * 필드명·구조가 같아야 한다 (홈 화면의 미커밋 세션 행, 초안 근거 패널이 그대로 읽는다).
 */
public record SessionResponse(
        Long id,
        Long userId,
        RepoRef repo,
        String remoteUrl,
        String branch,
        LocalDate workDate,
        List<UncommittedFile> uncommittedFiles,
        List<TodoItem> todos,
        String planNote,
        List<EditTimelineEntry> editTimeline,
        String summary,
        OffsetDateTime lastCommitAt,
        OffsetDateTime reportedAt) {

    public record RepoRef(Long id, String fullName) {}

    public static SessionResponse from(VscodeSession s) {
        return new SessionResponse(
                s.getId(),
                s.getUser().getId(),
                s.getRepo() == null ? null : new RepoRef(s.getRepo().getId(), s.getRepo().getFullName()),
                s.getRemoteUrl(),
                s.getBranch(),
                s.getWorkDate(),
                s.getUncommittedFiles(),
                s.getTodos(),
                s.getPlanNote(),
                s.getEditTimeline(),
                s.getSummary(),
                s.getLastCommitAt(),
                s.getReportedAt());
    }
}
