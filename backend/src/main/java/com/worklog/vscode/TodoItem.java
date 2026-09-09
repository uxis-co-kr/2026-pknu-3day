package com.worklog.vscode;

/**
 * vscode_sessions.todos JSONB 의 원소 — 변경된 파일 안의 TODO/FIXME 주석.
 */
public record TodoItem(String path, Integer line, String text) {}
