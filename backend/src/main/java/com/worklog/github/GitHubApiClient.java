package com.worklog.github;

import com.worklog.config.ApiException;
import com.worklog.github.dto.GitHubCommitDto;
import com.worklog.github.dto.GitHubRepoDto;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * GitHub REST API 호출 (PRD F1).
 *
 * <p>토큰은 리포를 등록한 사용자의 OAuth 토큰이라 호출마다 넘긴다. 클라이언트 자체는 상태가 없다.
 */
@Component
public class GitHubApiClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubApiClient.class);

    private static final String API_BASE = "https://api.github.com";
    private static final int PER_PAGE = 100;
    /** 한 번의 동기화에서 훑을 페이지 상한 — rate limit 방어 (PRD 11). */
    private static final int MAX_PAGES = 10;

    private final RestClient restClient = RestClient.builder().baseUrl(API_BASE).build();

    public GitHubRepoDto getRepo(String owner, String name, String token) {
        try {
            return restClient
                    .get()
                    .uri("/repos/{owner}/{name}", owner, name)
                    .headers(h -> headers(h, token))
                    .retrieve()
                    .body(GitHubRepoDto.class);
        } catch (RestClientResponseException e) {
            // 없는 리포와 권한 없는 리포를 GitHub 은 모두 404 로 답한다.
            if (e.getStatusCode().value() == 404 || e.getStatusCode().value() == 403) {
                throw ApiException.forbidden(
                        "REPO_NOT_ACCESSIBLE", "리포에 접근할 수 없습니다. 이름과 권한을 확인해 주세요.");
            }
            throw githubFailure(e);
        }
    }

    /** since 이후의 커밋 목록. 목록 응답에는 files/stats 가 없다. */
    public List<GitHubCommitDto> listCommits(String owner, String name, OffsetDateTime since, String token) {
        List<GitHubCommitDto> all = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            final int currentPage = page;
            List<GitHubCommitDto> batch;
            try {
                batch = restClient
                        .get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/repos/{owner}/{name}/commits")
                                .queryParam("since", DateTimeFormatter.ISO_INSTANT.format(since))
                                .queryParam("per_page", PER_PAGE)
                                .queryParam("page", currentPage)
                                .build(owner, name))
                        .headers(h -> headers(h, token))
                        .retrieve()
                        .body(new org.springframework.core.ParameterizedTypeReference<>() {});
            } catch (RestClientResponseException e) {
                // 커밋이 하나도 없는 빈 리포는 409 Git Repository is empty 를 낸다.
                if (e.getStatusCode().value() == 409) {
                    return List.of();
                }
                throw githubFailure(e);
            }
            if (batch == null || batch.isEmpty()) {
                break;
            }
            all.addAll(batch);
            if (batch.size() < PER_PAGE) {
                break;
            }
        }
        return all;
    }

    /** 커밋 하나의 상세 — files 와 patch 가 여기에만 있다. */
    public GitHubCommitDto getCommit(String owner, String name, String sha, String token) {
        try {
            return restClient
                    .get()
                    .uri("/repos/{owner}/{name}/commits/{sha}", owner, name, sha)
                    .headers(h -> headers(h, token))
                    .retrieve()
                    .body(GitHubCommitDto.class);
        } catch (RestClientResponseException e) {
            throw githubFailure(e);
        }
    }

    private static void headers(org.springframework.http.HttpHeaders headers, String token) {
        headers.set("Authorization", "Bearer " + token);
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");
    }

    private static ApiException githubFailure(RestClientResponseException e) {
        log.warn("GitHub API 호출 실패 {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
        return new ApiException(
                HttpStatus.BAD_GATEWAY, "GITHUB_API_ERROR", "GitHub API 호출에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    }
}
