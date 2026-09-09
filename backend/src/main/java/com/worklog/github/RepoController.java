package com.worklog.github;

import com.worklog.auth.AuthenticatedUser;
import com.worklog.auth.User;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 리포 관리 (PRD 7. /repos).
 */
@RestController
@RequestMapping("/repos")
public class RepoController {

    private final RepoService repoService;
    private final GitHubCollector collector;

    public RepoController(RepoService repoService, GitHubCollector collector) {
        this.repoService = repoService;
        this.collector = collector;
    }

    @GetMapping
    public List<RepoResponse> list() {
        return repoService.list().stream().map(RepoResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<RepoResponse> register(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody @jakarta.validation.Valid RegisterRequest request) {
        Repo repo = repoService.register(principal.id(), request.fullName());
        return ResponseEntity.status(HttpStatus.CREATED).body(RepoResponse.from(repo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repoService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** 수집은 오래 걸릴 수 있으므로 던지고 바로 202 로 답한다 (PRD F1-5). */
    @PostMapping("/{id}/sync")
    public ResponseEntity<Void> sync(@PathVariable Long id) {
        repoService.get(id); // 없는 리포면 404
        collector.syncAsync(id);
        return ResponseEntity.accepted().build();
    }

    public record RegisterRequest(@NotBlank String fullName) {}

    public record RepoResponse(
            Long id,
            String fullName,
            String defaultBranch,
            OffsetDateTime lastSyncedAt,
            RegisteredBy registeredBy) {

        static RepoResponse from(Repo repo) {
            User user = repo.getRegisteredBy();
            return new RepoResponse(
                    repo.getId(),
                    repo.getFullName(),
                    repo.getDefaultBranch(),
                    repo.getLastSyncedAt(),
                    user == null ? null : new RegisteredBy(user.getId(), user.getLogin()));
        }

        public record RegisteredBy(Long id, String login) {}
    }
}
