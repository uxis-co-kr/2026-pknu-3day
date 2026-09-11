package com.worklog.vscode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 확장이 보내는 페이로드 (PRD 7. POST /vscode/sessions).
 * 필드명은 vscode-extension/src/types.ts 및 프론트 목업과 정확히 같아야 한다.
 */
public record SessionRequest(
        @NotBlank String remoteUrl,
        @NotBlank String branch,
        @NotNull LocalDate workDate,
        List<UncommittedFile> uncommittedFiles,
        List<TodoItem> todos,
        String planNote,
        List<EditTimelineEntry> editTimeline,
        /** 그 폴더에서 오간 AI 대화 (V9). 없으면 빈 배열. */
        List<AiSessionSummary> aiSessions,
        /** 아직 원격에 없는 커밋 (V10). 업스트림이 없어 확장이 안 보내면 빈 배열로 본다. */
        List<UnpushedCommit> unpushedCommits,
        OffsetDateTime lastCommitAt) {}
