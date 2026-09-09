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
        OffsetDateTime lastCommitAt) {}
