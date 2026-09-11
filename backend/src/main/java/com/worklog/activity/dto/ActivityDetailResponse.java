package com.worklog.activity.dto;

import com.worklog.activity.Activity;
import com.worklog.activity.ActivityType;
import java.time.OffsetDateTime;

/**
 * GET /activities/{id} — 목록 항목과 같은 필드에 {@code message}, {@code rawDiff} 를 더한다 (PRD 7).
 *
 * <p>목록 항목을 중첩하지 않고 펼쳐 둔다. 클라이언트가 목록과 상세를 같은 방식으로 읽을 수 있어야 한다.
 */
public record ActivityDetailResponse(
        Long id,
        ActivityType type,
        ActivityResponse.RepoSummary repo,
        ActivityResponse.UserSummary user,
        String externalLogin,
        String externalId,
        String sha,
        String title,
        String message,
        String url,
        String branch,
        Integer filesChanged,
        Integer additions,
        Integer deletions,
        String summary,
        String summaryStatus,
        String rawDiff,
        /** 변경 파일 목록 (V10, F-2). 본문은 rawDiff 를 {@code --- path} 로 쪼개 쓴다. */
        java.util.List<com.worklog.activity.ChangedFile> files,
        OffsetDateTime occurredAt) {

    public static ActivityDetailResponse from(Activity activity) {
        ActivityResponse base = ActivityResponse.from(activity);
        return new ActivityDetailResponse(
                base.id(),
                base.type(),
                base.repo(),
                base.user(),
                base.externalLogin(),
                base.externalId(),
                base.sha(),
                base.title(),
                activity.getMessage(),
                base.url(),
                base.branch(),
                base.filesChanged(),
                base.additions(),
                base.deletions(),
                base.summary(),
                base.summaryStatus(),
                activity.getRawDiff(),
                activity.getFiles() == null ? java.util.List.of() : activity.getFiles(),
                base.occurredAt());
    }
}
