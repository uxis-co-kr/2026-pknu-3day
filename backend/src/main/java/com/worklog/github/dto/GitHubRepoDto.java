package com.worklog.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** GET /repos/{owner}/{repo} 응답 중 쓰는 필드만. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubRepoDto(
        Long id,
        String name,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("default_branch") String defaultBranch,
        Owner owner) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Owner(String login) {}
}
