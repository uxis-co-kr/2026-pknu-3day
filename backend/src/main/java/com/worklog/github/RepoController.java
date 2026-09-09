package com.worklog.github;

import com.worklog.activity.ActivityRepository;
import com.worklog.activity.ActivityType;
import com.worklog.auth.AuthenticatedUser;
import com.worklog.auth.User;
import com.worklog.config.KstDates;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final ActivityRepository activityRepository;

    public RepoController(
            RepoService repoService,
            GitHubCollector collector,
            ActivityRepository activityRepository) {
        this.repoService = repoService;
        this.collector = collector;
        this.activityRepository = activityRepository;
    }

    @GetMapping
    public List<RepoResponse> list() {
        Map<Long, Long> todayCommits = todayCommitCounts();
        return repoService.list().stream()
                .map(repo -> RepoResponse.from(
                        repo,
                        todayCommits.getOrDefault(repo.getId(), 0L),
                        syncStatusOf(repo)))
                .toList();
    }

    /** 리포별 오늘(KST) 커밋 수를 한 번의 쿼리로 모은다 — 리포마다 세면 N+1 이 된다. */
    private Map<Long, Long> todayCommitCounts() {
        LocalDate today = KstDates.today();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : activityRepository.countByRepoBetween(
                ActivityType.COMMIT, KstDates.startOf(today), KstDates.endOf(today))) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    /** 진행 중은 저장하지 않고 수집기에 물어본다 — 프로세스가 죽어도 상태가 굳지 않는다. */
    private SyncStatus syncStatusOf(Repo repo) {
        return collector.isSyncing(repo.getId()) ? SyncStatus.SYNCING : repo.getLastSyncStatus();
    }

    @PostMapping
    public ResponseEntity<RepoResponse> register(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody @jakarta.validation.Valid RegisterRequest request) {
        Repo repo = repoService.register(principal.id(), request.fullName());
        // 갓 등록한 리포라 오늘 활동은 아직 0 이고 아직 동기화한 적이 없다.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RepoResponse.from(repo, 0L, SyncStatus.OK));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repoService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 수집은 오래 걸릴 수 있으므로 던지고 바로 202 로 답한다 (PRD F1-5).
     *
     * @param full 최근 7일을 다시 훑는다. 수집 대상이 늘었을 때의 백필용.
     */
    @PostMapping("/{id}/sync")
    public ResponseEntity<Void> sync(
            @PathVariable Long id, @RequestParam(defaultValue = "false") boolean full) {
        repoService.get(id); // 없는 리포면 404
        collector.syncAsync(id, full);
        return ResponseEntity.accepted().build();
    }

    public record RegisterRequest(@NotBlank String fullName) {}

    public record RepoResponse(
            Long id,
            String fullName,
            String defaultBranch,
            OffsetDateTime lastSyncedAt,
            RegisteredBy registeredBy,
            long todayActivityCount,
            SyncStatus syncStatus) {

        static RepoResponse from(Repo repo, long todayActivityCount, SyncStatus syncStatus) {
            User user = repo.getRegisteredBy();
            return new RepoResponse(
                    repo.getId(),
                    repo.getFullName(),
                    repo.getDefaultBranch(),
                    repo.getLastSyncedAt(),
                    user == null ? null : new RegisteredBy(user.getId(), user.getLogin()),
                    todayActivityCount,
                    syncStatus);
        }

        public record RegisteredBy(Long id, String login) {}
    }
}
