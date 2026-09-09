package com.worklog.github;

/**
 * 리포 동기화 상태 (PRD 7. GET /repos).
 *
 * <p>{@code SYNCING} 은 DB 에 저장하지 않는다 — 수집기가 진행 중인 리포 집합을 들고 있어
 * 응답 시점에 판단한다. 저장하면 프로세스가 죽었을 때 영원히 SYNCING 으로 남는다.
 */
public enum SyncStatus {
    OK,
    SYNCING,
    FAILED
}
