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
        /** 파일별 저장 이벤트. 2026-09-11 이전 기록에만 들어 있다. */
        List<EditTimelineEntry> editTimeline,
        /** 고쳐 놓고 저장하지 않은 파일 (V12). */
        List<UnsavedFile> unsavedFiles,
        /** 그 폴더에서 오간 AI 대화 (V9). 없으면 빈 배열. */
        List<AiSessionSummary> aiSessions,
        /** 미푸시 커밋 (V11). null 이면 업스트림이 없어 셀 수 없다는 뜻이다. */
        List<UnpushedCommit> unpushedCommits,
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
                s.getUnsavedFiles(),
                s.getAiSessions(),
                s.getUnpushedCommits(),
                s.getSummary(),
                s.getLastCommitAt(),
                s.getReportedAt());
    }
}
