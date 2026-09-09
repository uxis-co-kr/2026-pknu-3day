package com.worklog.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/**
 * GET /repos/{o}/{r}/pulls 응답 중 쓰는 필드만 (PRD F1).
 *
 * <p>{@code mergedAt} 이 채워진 PR 은 {@code PR_OPENED} 와 {@code PR_MERGED} 두 활동을 만든다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPullRequestDto(
        Integer number,
        String title,
        String body,
        String state,
        @JsonProperty("html_url") String htmlUrl,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("updated_at") OffsetDateTime updatedAt,
        @JsonProperty("merged_at") OffsetDateTime mergedAt,
        User user,
        @JsonProperty("merged_by") User mergedBy,
        Ref head,
        Ref base) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(Long id, String login) {}

    /** 브랜치 참조. {@code ref} 가 브랜치 이름이다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ref(String ref, String sha) {}

    public boolean isMerged() {
        return mergedAt != null;
    }

    /** 활동의 external_id — PR 번호. 커밋의 sha 자리에 대응한다 (결정 ⑨). */
    public String externalId() {
        return String.valueOf(number);
    }

    public String branch() {
        return head == null ? null : head.ref();
    }

    public String openedByLogin() {
        return user == null ? null : user.login();
    }

    /**
     * 머지한 사람. GitHub 이 목록 응답에서 merged_by 를 주지 않는 경우가 있어
     * 없으면 PR 작성자로 대신한다.
     */
    public String mergedByLogin() {
        if (mergedBy != null && mergedBy.login() != null) {
            return mergedBy.login();
        }
        return openedByLogin();
    }
}
