package com.worklog.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** GET /repos/{owner}/{repo} 와 GET /user/repos 응답 중 쓰는 필드만. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubRepoDto(
        Long id,
        String name,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("default_branch") String defaultBranch,
        /** 아카이브된 리포는 전체 등록에서 뺀다 — 커밋이 더 생기지 않는다. */
        Boolean archived,
        Owner owner) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Owner(String login) {}
}
