package com.worklog.activity;

/**
 * 커밋이 바꾼 파일 하나 (V10, BACKLOG F-2).
 *
 * <p>GitHub 커밋 상세의 {@code files[]} 에서 그대로. diff 본문은 여기 두지 않는다 —
 * {@code raw_diff} 한 덩어리로 있고, 화면은 {@code --- path} 헤더로 쪼개 쓴다.
 *
 * @param status added · modified · removed · renamed (GitHub 이 주는 값 그대로)
 */
public record ChangedFile(String path, String status, Integer additions, Integer deletions) {}
