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

    /** 내가 등록한 리포만 (9/10 결정). 남의 리포는 그 사람 목록에만 있다. */
    @GetMapping
    public List<RepoResponse> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        Map<Long, Long> todayCommits = todayCommitCounts();
        return repoService.listMine(principal.id()).stream()
                .map(repo -> RepoResponse.from(
                        repo,
                        todayCommits.getOrDefault(repo.getId(), 0L),
                        syncStatusOf(repo)))
                .toList();
    }

    /**
     * 등록된 리포 이름 전부 — 확장이 쓴다 (BACKLOG2 §2-4).
     *
     * <p>확장은 "이 폴더의 작업을 서버로 보낼까" 를 이 목록으로 가린다. 개인 프로젝트를 열어
     * 두었다고 그것까지 보내지 않기 위해서다 (§4 결정 7). 그런데 위 {@link #list} 는 <b>내가
     * 등록한 것만</b> 주므로, 그것으로 가리면 남이 등록한 리포에서 일하는 팀원의 기록이 통째로
     * 버려진다 — 리포는 한 사람만 등록할 수 있어 두 번째 사람은 등록할 길도 없다.
     *
     * <p>그래서 이 경로는 등록자를 가리지 않는다. 나가는 것은 이름뿐이다.
     */
    @GetMapping("/known")
    public List<String> known() {
        return repoService.knownFullNames();
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

    /**
     * 내 GitHub 리포를 한 번에 등록하고 바로 수집을 건다 (9/10 "전체 등록").
     *
     * <p>등록만 하고 두면 다음 스케줄까지 화면이 비어 있다. 방금 연결한 사람에게는 그것이
     * 고장으로 보인다.
     */
    @PostMapping("/import")
    public ImportResponse importMine(@AuthenticationPrincipal AuthenticatedUser principal) {
        List<Repo> added = repoService.importMine(principal.id());
        added.forEach(repo -> collector.syncAsync(repo.getId(), true));
        return new ImportResponse(added.size(), added.stream().map(Repo::getFullName).toList());
    }

    /**
     * 내 리포를 한 번에 동기화한다 (9/10 "전체 동기화").
     *
     * <p>{@code full=true} 면 최근 며칠을 다시 훑는다. GitHub 을 막 연결해 예전 활동까지
     * 끌어올 때 쓴다.
     */
    @PostMapping("/sync-all")
    public ResponseEntity<ImportResponse> syncAll(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "false") boolean full) {
        List<Repo> mine = repoService.listMine(principal.id());
        mine.forEach(repo -> collector.syncAsync(repo.getId(), full));
        return ResponseEntity.accepted()
                .body(new ImportResponse(mine.size(), mine.stream().map(Repo::getFullName).toList()));
    }

    /** 몇 개를 다뤘는지 화면이 알려 줄 수 있게 이름까지 준다. */
    public record ImportResponse(int count, List<String> repos) {}

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        repoService.delete(principal.id(), id);
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
