package com.worklog.draft.dto;

import com.worklog.draft.Draft;
import com.worklog.draft.DraftStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * GET /drafts 의 항목 (PRD 7). 홈 화면의 사용자 카드가 배지와 버튼을 고르는 데만 쓰므로
 * 본문(contentMd)은 싣지 않는다. 담당자 1의 목업 `frontend/src/mocks/drafts.json` 과 같은 모양.
 */
public record DraftSummaryResponse(
        Long id,
        Long userId,
        LocalDate workDate,
        Integer version,
        DraftStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime confirmedAt) {

    public static DraftSummaryResponse from(Draft d) {
        return new DraftSummaryResponse(
                d.getId(),
                d.getUser().getId(),
                d.getWorkDate(),
                d.getVersion(),
                d.getStatus(),
                d.getCreatedAt(),
                d.getUpdatedAt(),
                d.getConfirmedAt());
    }
}
