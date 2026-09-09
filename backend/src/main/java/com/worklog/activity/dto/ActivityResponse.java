package com.worklog.activity.dto;

import com.worklog.activity.Activity;
import com.worklog.activity.ActivityType;
import com.worklog.auth.User;
import com.worklog.github.Repo;
import java.time.OffsetDateTime;

/**
 * GET /activities 의 항목 (PRD 7. 대표 응답 스키마).
 *
 * <p>담당자 1의 목업 JSON 과 필드명이 완전히 같아야 한다. 가입하지 않은 GitHub 계정의 활동은
 * {@code user} 가 null 이고 {@code externalLogin} 만 채워진다 (PRD F1-5).
 */
public record ActivityResponse(
        Long id,
        ActivityType type,
        RepoSummary repo,
        UserSummary user,
        String externalLogin,
        String externalId,
        String sha,
        String title,
        String url,
        String branch,
        Integer filesChanged,
        Integer additions,
        Integer deletions,
        String summary,
        String summaryStatus,
        OffsetDateTime occurredAt) {

    public static ActivityResponse from(Activity activity) {
        Repo repo = activity.getRepo();
        User user = activity.getUser();
        return new ActivityResponse(
                activity.getId(),
                activity.getType(),
                repo == null ? null : new RepoSummary(repo.getId(), repo.getFullName()),
                user == null
                        ? null
                        : new UserSummary(
                                user.getId(), user.getLogin(), user.getName(), user.getAvatarUrl()),
                activity.getExternalLogin(),
                activity.getExternalId(),
                activity.getSha(),
                activity.getTitle(),
                activity.getUrl(),
                activity.getBranch(),
                activity.getFilesChanged(),
                activity.getAdditions(),
                activity.getDeletions(),
                activity.getSummary(),
                activity.getSummaryStatus() == null ? null : activity.getSummaryStatus().name(),
                activity.getOccurredAt());
    }

    public record RepoSummary(Long id, String fullName) {}

    public record UserSummary(Long id, String login, String name, String avatarUrl) {}
}
