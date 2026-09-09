package com.worklog.config;

/**
 * 모든 오류 응답의 본문 형식 (PRD 7. 오류 형식).
 *
 * <p>{@code {"code": "REPO_NOT_ACCESSIBLE", "message": "..."}} + 적절한 HTTP 상태.
 * 컨트롤러가 던지는 예외뿐 아니라 Security 의 401/403 도 이 형식으로 통일한다.
 */
public record ApiError(String code, String message) {}
