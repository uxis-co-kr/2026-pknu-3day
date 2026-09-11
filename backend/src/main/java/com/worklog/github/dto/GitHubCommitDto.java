package com.worklog.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * GET /repos/{o}/{r}/commits 와 /commits/{sha} 응답. 목록 응답에는 files·stats 가 없고
 * 상세 응답에만 있으므로 한 레코드로 둘 다 받는다.
 *
 * <p>{@code author} 는 GitHub 계정과 연결되지 않은 커밋에서 null 이다. 그때는
 * {@code commit.author.name} 만 남으므로 활동은 external_login 으로만 기록된다 (PRD F1-5).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubCommitDto(
        String sha,
        @JsonProperty("html_url") String htmlUrl,
        Commit commit,
        Author author,
        Stats stats,
        List<File> files) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Commit(String message, CommitAuthor author) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommitAuthor(String name, String email, OffsetDateTime date) {}

    /** 커밋을 올린 GitHub 계정. 매칭되지 않으면 null. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Author(Long id, String login) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Stats(Integer additions, Integer deletions, Integer total) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record File(
            String filename,
            /** added · modified · removed · renamed */
            String status,
            Integer additions,
            Integer deletions,
            Integer changes,
            String patch) {}

    /** 커밋 메시지 제목 줄. */
    public String title() {
        String message = commit == null || commit.message() == null ? "" : commit.message().strip();
        int nl = message.indexOf('\n');
        return nl < 0 ? message : message.substring(0, nl).strip();
    }

    public OffsetDateTime occurredAt() {
        return commit == null || commit.author() == null ? null : commit.author().date();
    }

    /** GitHub 계정이 연결돼 있으면 그 login, 아니면 커밋에 적힌 이름. */
    public String authorLogin() {
        if (author != null && author.login() != null) {
            return author.login();
        }
        return commit == null || commit.author() == null ? null : commit.author().name();
    }
}
