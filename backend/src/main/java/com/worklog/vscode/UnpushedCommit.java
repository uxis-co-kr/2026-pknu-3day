package com.worklog.vscode;

import java.time.OffsetDateTime;

/**
 * vscode_sessions.unpushed_commits JSONB 의 원소 — 커밋했지만 아직 push 하지 않은 것.
 *
 * <p>이 구간은 GitHub 수집기가 보지 못한다. 원격에 없으니 API 에 나오지 않는다. 확장이
 * 보내 주지 않으면 "커밋까지 해 둔 일" 이 어디에도 남지 않는다 (미커밋도 아니고 GitHub
 * 활동도 아니다).
 *
 * @param sha 짧은 해시 (`%h`)
 * @param subject 커밋 제목 한 줄
 * @param at 커밋 시각
 */
public record UnpushedCommit(String sha, String subject, OffsetDateTime at) {}
