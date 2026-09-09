package com.worklog.vscode;

/**
 * vscode_sessions.uncommitted_files JSONB 의 원소 (PRD 7. POST /vscode/sessions).
 */
public record UncommittedFile(String path, Integer additions, Integer deletions, String diff) {}
