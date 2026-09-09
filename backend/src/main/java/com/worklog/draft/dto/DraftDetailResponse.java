package com.worklog.draft.dto;

import com.worklog.activity.dto.ActivityResponse;
import com.worklog.draft.Draft;
import com.worklog.draft.DraftStatus;
import com.worklog.vscode.SessionResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * GET /drafts/{id} (PRD 7). 저장된 id 배열을 객체로 펼쳐 초안 편집 화면의 우측 근거 패널이
 * 그대로 그릴 수 있게 한다.
 */
public record DraftDetailResponse(
        Long id,
        Long userId,
        LocalDate workDate,
        Integer version,
        DraftStatus status,
        String contentMd,
        List<ActivityResponse> sourceActivities,
        List<SessionResponse> sourceSessions,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime confirmedAt) {

    public static DraftDetailResponse of(
            Draft d, List<ActivityResponse> activities, List<SessionResponse> sessions) {
        return new DraftDetailResponse(
                d.getId(),
                d.getUser().getId(),
                d.getWorkDate(),
                d.getVersion(),
                d.getStatus(),
                d.getContentMd(),
                activities,
                sessions,
                d.getCreatedAt(),
                d.getUpdatedAt(),
                d.getConfirmedAt());
    }
}
