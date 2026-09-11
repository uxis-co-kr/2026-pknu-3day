package com.worklog.draft;

/**
 * 업무 일지의 종류 (V15).
 *
 * <p>셋 다 쓰는 방식이 같다 — AI 가 초안을 만들고, 사람이 고쳐 저장하고, Mattermost 로 보낸다.
 * 다른 것은 "무엇을 모아 쓰느냐" 뿐이다.
 */
public enum DraftKind {
    /** 하루치. 그날의 커밋·PR·VS Code 기록을 모은다. */
    DAILY,
    /** 기간. 그 기간의 하루치 일지들을 다시 묶는다 — 없으면 활동에서 바로 만든다. */
    WEEKLY,
    /** 저장소별. 그 기간 그 저장소의 커밋·PR 을 모은다. */
    REPO;

    public String label() {
        return switch (this) {
            case DAILY -> "업무 일지";
            case WEEKLY -> "주간 업무일지";
            case REPO -> "저장소별 업무일지";
        };
    }
}
