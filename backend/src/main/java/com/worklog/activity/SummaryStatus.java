package com.worklog.activity;

/**
 * LLM 요약 파이프라인의 상태 (PRD F2). 실패는 최대 3회까지 재시도한다.
 */
public enum SummaryStatus {
    PENDING,
    DONE,
    FAILED
}
