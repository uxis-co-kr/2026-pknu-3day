package com.worklog.github;

import com.worklog.activity.Activity;
import com.worklog.activity.ActivityRepository;
import com.worklog.activity.ActivityType;
import com.worklog.activity.SummaryStatus;
import com.worklog.auth.User;
import com.worklog.auth.UserRepository;
import com.worklog.auth.UserService;
import com.worklog.github.dto.GitHubBranchDto;
import com.worklog.github.dto.GitHubCommitDto;
import com.worklog.github.dto.GitHubPullRequestDto;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 등록된 리포의 커밋을 activities 로 정규화해 저장한다 (PRD F1).
 *
 * <p>커밋과 PR/머지를 모은다. 같은 PR 이 열림·머지 두 활동으로 저장될 수 있고,
 * 중복은 (repo, type, external_id) UNIQUE 로 걸러진다.
 */
@Component
public class GitHubCollector {

    private static final Logger log = LoggerFactory.getLogger(GitHubCollector.class);

    /** 첫 동기화에서 거슬러 올라갈 기간 (PRD F1 수용 기준). */
    private static final int FIRST_SYNC_DAYS = 7;

    /**
     * 증분 수집에서 since 를 이만큼 앞당긴다.
     *
     * <p>GitHub 의 since 는 <b>커밋 작성 시각</b>으로 거르는데 우리가 넣는 값은 <b>우리가 동기화한
     * 시각</b>이다. 아침에 작성해 두고 점심에 푸시한 커밋은 그 사이 동기화가 한 번이라도 돌면
     * 작성 시각이 since 보다 앞서서 영영 들어오지 않는다. 실제로 09:27 에 작성해 09:35 에 푸시한
     * 커밋이 09:32 동기화 뒤에 누락됐다.
     *
     * <p>겹쳐 조회되는 커밋은 이미 저장한 sha 라 상세 호출 없이 걸러지므로 비용은 목록 조회뿐이다.
     */
    private static final Duration SINCE_LOOKBACK = Duration.ofHours(24);

    private final RepoRepository repoRepository;
    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final GitHubApiClient gitHubApiClient;

    /** 같은 리포에 수동 sync 와 스케줄러가 겹쳐 들어오는 것을 막는다. */
    private final Set<Long> inProgress = ConcurrentHashMap.newKeySet();

    public GitHubCollector(
            RepoRepository repoRepository,
            ActivityRepository activityRepository,
            UserRepository userRepository,
            UserService userService,
            GitHubApiClient gitHubApiClient) {
        this.repoRepository = repoRepository;
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.gitHubApiClient = gitHubApiClient;
    }

    /** 응답 시점의 syncStatus 판단용 (PRD 7. GET /repos). */
    public boolean isSyncing(Long repoId) {
        return inProgress.contains(repoId);
    }

    @Async
    public void syncAsync(Long repoId) {
        syncAsync(repoId, false);
    }

    @Async
    public void syncAsync(Long repoId, boolean full) {
        syncAsync(repoId, full, FIRST_SYNC_DAYS);
    }

    /** @param days full 일 때 거슬러 올라갈 날 수 */
    @Async
    public void syncAsync(Long repoId, boolean full, int days) {
        try {
            sync(repoId, full, days);
        } catch (Exception e) {
            // 비동기라 예외를 받아줄 호출자가 없다. 로그만 남기고 다음 스케줄에 다시 시도한다.
            log.error("리포 {} 동기화 실패", repoId, e);
        }
    }

    public int sync(Long repoId) {
        return sync(repoId, false);
    }

    /**
     * 수집 본체. HTTP 호출이 길어 하나의 트랜잭션으로 묶지 않는다 — 커밋 한 건의 저장이
     * 각각의 트랜잭션이고, 중간에 실패해도 그때까지 저장된 활동은 남는다. 다음 동기화는
     * last_synced_at 이 갱신되지 않았으므로 같은 구간을 다시 훑고, 중복은 UNIQUE 로 걸러진다.
     *
     * @param full true 면 last_synced_at 을 무시하고 {@code days} 일을 다시 훑는다. 수집 대상을
     *     새로 추가했을 때(예: PR) 기존 리포는 last_synced_at 이 이미 앞서 있어 증분으로는 영영
     *     들어오지 않으므로 백필 통로가 필요하다.
     * @return 새로 저장한 활동 수
     */
    public int sync(Long repoId, boolean full) {
        return sync(repoId, full, FIRST_SYNC_DAYS);
    }

    /**
     * @param days {@code full} 일 때 거슬러 올라갈 날 수.
     *
     *     <p>기본 7일로는 <b>한동안 손대지 않은 저장소가 통째로 비어 보인다</b> — 마지막 커밋이
     *     2주 전이면 몇 번을 다시 훑어도 창 밖이라 한 건도 들어오지 않는다 (9/11 확인).
     *     그래서 관리자가 기간을 골라 부를 수 있게 열어 둔다.
     */
    public int sync(Long repoId, boolean full, int days) {
        if (!inProgress.add(repoId)) {
            log.info("리포 {} 는 이미 동기화 중이라 건너뛴다.", repoId);
            return 0;
        }
        try {
            Repo repo = repoRepository.findWithRegistrant(repoId).orElse(null);
            if (repo == null) {
                log.warn("리포 {} 가 없어 동기화를 건너뛴다.", repoId);
                return 0;
            }
            User registrant = repo.getRegisteredBy();
            if (registrant == null) {
                log.warn("리포 {} 에 등록자가 없어 GitHub 토큰을 얻을 수 없다.", repo.getFullName());
                return 0;
            }
            String token = userService.githubTokenOf(registrant);
            if (token == null) {
                log.warn("등록자 {} 에게 GitHub 토큰이 없다.", registrant.getLogin());
                return 0;
            }

            // 수집 중에 들어온 커밋을 놓치지 않도록 "시작" 시각을 다음 since 로 쓴다.
            OffsetDateTime syncStartedAt = OffsetDateTime.now();
            OffsetDateTime since = (!full && repo.getLastSyncedAt() != null)
                    // 푸시가 늦은 커밋을 놓치지 않도록 되돌아본다.
                    ? repo.getLastSyncedAt().minus(SINCE_LOOKBACK)
                    : syncStartedAt.minusDays(Math.max(1, days));

            Set<String> known = new HashSet<>(
                    activityRepository.findExternalIds(repo.getId(), ActivityType.COMMIT));

            List<BranchCommits> commitsByBranch = collectBranches(repo, since, token, known);

            int saved = 0;
            for (BranchCommits bc : commitsByBranch) {
            for (GitHubCommitDto summary : bc.commits()) {
                if (summary.sha() == null || !known.add(summary.sha())) {
                    continue; // 이미 저장한 커밋은 상세 호출조차 하지 않는다 (rate limit, PRD 11).
                }
                GitHubCommitDto detail =
                        gitHubApiClient.getCommit(repo.getOwner(), repo.getName(), summary.sha(), token);
                if (detail == null) {
                    continue;
                }
                Activity activity = toActivity(repo, detail);
                // 어느 브랜치에서 처음 본 커밋인지 남긴다. 기본 브랜치를 먼저 돌므로,
                // 여러 브랜치에 있는 커밋에는 기본 브랜치 이름이 남아 값이 안정적이다.
                activity.setBranch(bc.branch());
                saved += save(activity);
            }
            }

            saved += collectPullRequests(repo, since, token);

            repo.setLastSyncedAt(syncStartedAt);
            repo.setLastSyncStatus(SyncStatus.OK);
            repo.setLastSyncError(null);
            repoRepository.save(repo);
            int scanned = commitsByBranch.stream().mapToInt(b2 -> b2.commits().size()).sum();
            log.info(
                    "리포 {} 동기화 완료 — 브랜치 {}곳에서 커밋 {}건 조회, 신규 활동 {}건",
                    repo.getFullName(),
                    commitsByBranch.size(),
                    scanned,
                    saved);
            return saved;
        } catch (Exception e) {
            // 실패를 기록해 화면이 배지를 띄울 수 있게 한다. last_synced_at 은 갱신하지 않아
            // 다음 시도가 같은 구간을 다시 훑는다.
            markFailed(repoId, e);
            throw e;
        } finally {
            inProgress.remove(repoId);
        }
    }

    /** 브랜치 하나에서 읽어 온 커밋. */
    private record BranchCommits(String branch, List<GitHubCommitDto> commits) {}

    /**
     * 모든 브랜치의 커밋을 모은다 (TODO_0910 §3-4).
     *
     * <p>커밋 조회에 {@code sha} 를 주지 않으면 GitHub 은 기본 브랜치만 돌려준다. 그래서
     * 머지 전 작업 브랜치의 커밋이 업무 일지에서 통째로 빠졌다. 팀 전원이 작업 브랜치에서
     * 일하면 머지하는 날에만 하루치가 몰려 잡힌다.
     *
     * <p>호출 수를 줄이려고 <b>움직인 브랜치만</b> 훑는다. 브랜치 목록 응답의 head sha 가
     * 이미 수집한 값이면 지난 동기화 이후 새 커밋이 없다는 뜻이다. 그래서 실제 호출은
     * "브랜치 목록 1회 + 오늘 손댄 브랜치 수" 로 끝난다.
     *
     * <p>중복은 걱정하지 않아도 된다. 브랜치들이 커밋을 공유해도 호출부의 {@code known}
     * 셋이 상세 조회를 막고, DB 의 {@code UNIQUE (repo_id, type, external_id)} 가 최종적으로
     * 거른다.
     *
     * <p>브랜치 목록을 못 읽으면 기본 브랜치만이라도 훑는다 — 예전 동작이다.
     */
    private List<BranchCommits> collectBranches(
            Repo repo, OffsetDateTime since, String token, Set<String> known) {
        List<GitHubBranchDto> branches;
        try {
            branches = gitHubApiClient.listBranches(repo.getOwner(), repo.getName(), token);
        } catch (Exception e) {
            log.warn("리포 {} 의 브랜치 목록을 읽지 못해 기본 브랜치만 훑는다: {}",
                    repo.getFullName(), e.getMessage());
            return List.of(new BranchCommits(
                    repo.getDefaultBranch(),
                    gitHubApiClient.listCommits(repo.getOwner(), repo.getName(), since, token)));
        }

        List<BranchCommits> result = new ArrayList<>();
        for (String branch : movedBranches(branches, repo.getDefaultBranch(), known)) {
            result.add(new BranchCommits(
                    branch,
                    gitHubApiClient.listCommits(repo.getOwner(), repo.getName(), branch, since, token)));
        }
        return result;
    }

    /**
     * 훑을 브랜치 이름 — 기본 브랜치가 맨 앞이고, head sha 가 이미 알려진 브랜치는 뺀다.
     *
     * <p>아주 오래된 커밋에서 딴 새 브랜치는 head 가 이미 알려진 값이라 건너뛸 수 있다.
     * 다음 푸시로 head 가 바뀌면 그때 잡히므로 스스로 회복된다.
     */
    private List<String> movedBranches(
            List<GitHubBranchDto> branches, String defaultBranch, Set<String> known) {
        List<String> moved = new ArrayList<>();
        for (GitHubBranchDto b : branches) {
            if (b.name() == null || (b.headSha() != null && known.contains(b.headSha()))) {
                continue;
            }
            moved.add(b.name());
        }
        if (defaultBranch != null && moved.remove(defaultBranch)) {
            moved.add(0, defaultBranch);
        }
        return moved;
    }

    private void markFailed(Long repoId, Exception cause) {
        repoRepository.findById(repoId).ifPresent(repo -> {
            repo.setLastSyncStatus(SyncStatus.FAILED);
            String message = cause.getMessage();
            repo.setLastSyncError(message == null ? cause.getClass().getSimpleName() : message);
            repoRepository.save(repo);
        });
    }

    /**
     * PR 을 열림/머지 활동으로 저장한다. 하나의 PR 이 최대 두 행을 만든다 (결정 ⑨).
     *
     * <p>PR 은 diff 를 받지 않는다 — PR 당 API 호출이 한 번 더 필요하고, 요약은 제목과
     * 본문으로 충분하다 (결정 ⑩).
     */
    private int collectPullRequests(Repo repo, OffsetDateTime since, String token) {
        List<GitHubPullRequestDto> pulls =
                gitHubApiClient.listPullRequests(repo.getOwner(), repo.getName(), since, token);
        if (pulls.isEmpty()) {
            return 0;
        }
        Set<String> openedIds =
                new HashSet<>(activityRepository.findExternalIds(repo.getId(), ActivityType.PR_OPENED));
        Set<String> mergedIds =
                new HashSet<>(activityRepository.findExternalIds(repo.getId(), ActivityType.PR_MERGED));

        int saved = 0;
        for (GitHubPullRequestDto pr : pulls) {
            if (pr.number() == null) {
                continue;
            }
            if (openedIds.add(pr.externalId())) {
                saved += save(toPullRequestActivity(repo, pr, ActivityType.PR_OPENED));
            }
            if (pr.isMerged() && mergedIds.add(pr.externalId())) {
                saved += save(toPullRequestActivity(repo, pr, ActivityType.PR_MERGED));
            }
        }
        log.info("리포 {} PR {}건 조회, 신규 활동 {}건", repo.getFullName(), pulls.size(), saved);
        return saved;
    }

    /** UNIQUE 위반은 동시 수집에서 생길 수 있는 정상 경로다. */
    private int save(Activity activity) {
        try {
            activityRepository.save(activity);
            return 1;
        } catch (DataIntegrityViolationException e) {
            log.debug("{} {} 는 이미 저장돼 있다.", activity.getType(), activity.getExternalId());
            return 0;
        }
    }

    private Activity toPullRequestActivity(
            Repo repo, GitHubPullRequestDto pr, ActivityType type) {
        boolean merged = type == ActivityType.PR_MERGED;

        Activity activity = new Activity();
        activity.setRepo(repo);
        activity.setType(type);
        activity.setExternalId(pr.externalId());
        // 제목은 GitHub 이 준 그대로만 저장한다. "PR #N 머지:" 같은 표기는 화면과 초안 템플릿이
        // type·externalId 를 보고 각자 붙이므로, 여기서 붙이면 두 번 붙는다.
        activity.setTitle(pr.title());
        activity.setMessage(pr.body());
        activity.setUrl(pr.htmlUrl());
        activity.setBranch(pr.branch());
        activity.setOccurredAt(merged ? pr.mergedAt() : pr.createdAt());

        String login = merged ? pr.mergedByLogin() : pr.openedByLogin();
        activity.setExternalLogin(login);
        if (login != null) {
            userRepository.findByLogin(login).ifPresent(activity::setUser);
        }

        activity.setSummaryStatus(SummaryStatus.PENDING);
        return activity;
    }

    private Activity toActivity(Repo repo, GitHubCommitDto dto) {
        Activity activity = new Activity();
        activity.setRepo(repo);
        activity.setType(ActivityType.COMMIT);
        activity.setExternalId(dto.sha());
        activity.setSha(dto.sha());
        activity.setTitle(dto.title());
        activity.setMessage(dto.commit() == null ? null : dto.commit().message());
        activity.setUrl(dto.htmlUrl());
        // 오늘은 기본 브랜치 커밋만 모은다. 브랜치별 수집은 2일차.
        activity.setBranch(repo.getDefaultBranch());

        List<GitHubCommitDto.File> files = dto.files() == null ? List.of() : dto.files();
        activity.setFilesChanged(files.size());
        activity.setAdditions(dto.stats() == null || dto.stats().additions() == null ? 0 : dto.stats().additions());
        activity.setDeletions(dto.stats() == null || dto.stats().deletions() == null ? 0 : dto.stats().deletions());
        activity.setRawDiff(DiffTruncator.truncate(files));
        // 화면이 파일 단위로 그릴 목록 (V11, F-2). diff 본문은 위 rawDiff 한 덩어리로 둔다.
        activity.setFiles(files.stream()
                .map(f -> new com.worklog.activity.ChangedFile(
                        f.filename(), f.status(), nullToZero(f.additions()), nullToZero(f.deletions())))
                .toList());

        String login = dto.authorLogin();
        activity.setExternalLogin(login);
        // 가입한 사용자면 연결하고, 아니면 external_login 만 남긴다 (PRD F1-5).
        if (login != null) {
            userRepository.findByLogin(login).ifPresent(activity::setUser);
        }

        activity.setOccurredAt(dto.occurredAt() == null ? OffsetDateTime.now() : dto.occurredAt());
        activity.setSummaryStatus(SummaryStatus.PENDING); // 요약 파이프라인은 2일차 2-7
        return activity;
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
