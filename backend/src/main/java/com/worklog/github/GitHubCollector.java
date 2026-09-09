package com.worklog.github;

import com.worklog.activity.Activity;
import com.worklog.activity.ActivityRepository;
import com.worklog.activity.ActivityType;
import com.worklog.activity.SummaryStatus;
import com.worklog.auth.User;
import com.worklog.auth.UserRepository;
import com.worklog.auth.UserService;
import com.worklog.github.dto.GitHubCommitDto;
import com.worklog.github.dto.GitHubPullRequestDto;
import java.time.OffsetDateTime;
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
        try {
            sync(repoId, full);
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
     * @param full true 면 last_synced_at 을 무시하고 최근 7일을 다시 훑는다. 수집 대상을 새로
     *     추가했을 때(예: PR) 기존 리포는 last_synced_at 이 이미 앞서 있어 증분으로는 영영
     *     들어오지 않으므로 백필 통로가 필요하다.
     * @return 새로 저장한 활동 수
     */
    public int sync(Long repoId, boolean full) {
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
                    ? repo.getLastSyncedAt()
                    : syncStartedAt.minusDays(FIRST_SYNC_DAYS);

            List<GitHubCommitDto> commits =
                    gitHubApiClient.listCommits(repo.getOwner(), repo.getName(), since, token);
            Set<String> known = new HashSet<>(
                    activityRepository.findExternalIds(repo.getId(), ActivityType.COMMIT));

            int saved = 0;
            for (GitHubCommitDto summary : commits) {
                if (summary.sha() == null || !known.add(summary.sha())) {
                    continue; // 이미 저장한 커밋은 상세 호출조차 하지 않는다 (rate limit, PRD 11).
                }
                GitHubCommitDto detail =
                        gitHubApiClient.getCommit(repo.getOwner(), repo.getName(), summary.sha(), token);
                if (detail == null) {
                    continue;
                }
                saved += save(toActivity(repo, detail));
            }

            saved += collectPullRequests(repo, since, token);

            repo.setLastSyncedAt(syncStartedAt);
            repo.setLastSyncStatus(SyncStatus.OK);
            repo.setLastSyncError(null);
            repoRepository.save(repo);
            log.info("리포 {} 동기화 완료 — 커밋 {}건 조회, 신규 활동 {}건", repo.getFullName(), commits.size(), saved);
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
        activity.setTitle(merged ? "PR #%d 머지: %s".formatted(pr.number(), pr.title()) : pr.title());
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
}
