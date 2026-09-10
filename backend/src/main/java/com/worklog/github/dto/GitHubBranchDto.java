package com.worklog.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * GET /repos/{owner}/{name}/branches 의 한 항목.
 *
 * <p>{@code commit.sha} 는 그 브랜치의 head 다. 이미 수집한 sha 면 지난 동기화 이후
 * 움직이지 않았다는 뜻이라 커밋 목록을 부르지 않아도 된다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubBranchDto(String name, Commit commit) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Commit(String sha) {}

    public String headSha() {
        return commit == null ? null : commit.sha();
    }
}
