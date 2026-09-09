package com.worklog.vscode;

import java.time.OffsetDateTime;

/**
 * vscode_sessions.edit_timeline JSONB 의 원소 — 파일별 저장 이벤트 요약.
 */
public record EditTimelineEntry(
        String path, OffsetDateTime firstSavedAt, OffsetDateTime lastSavedAt, Integer saveCount) {}
