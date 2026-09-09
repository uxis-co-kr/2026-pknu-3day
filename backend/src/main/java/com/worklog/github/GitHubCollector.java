package com.worklog.github;

import com.worklog.activity.Activity;
import com.worklog.activity.ActivityRepository;
import com.worklog.activity.ActivityType;
import com.worklog.activity.SummaryStatus;
import com.worklog.auth.User;
import com.worklog.auth.UserRepository;
import com.worklog.auth.UserService;
import com.worklog.github.dto.GitHubCommitDto;
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
 * <p>오늘은 커밋만 모은다. PR / 머지 수집은 2일차(2-6).
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

    @Async
    public void syncAsync(Long repoId) {
        try {
            sync(repoId);
        } catch (Exception e) {
            // 비동기라 예외를 받아줄 호출자가 없다. 로그만 남기고 다음 스케줄에 다시 시도한다.
            log.error("리포 {} 동기화 실패", repoId, e);
        }
    }

    /**
     * 수집 본체. HTTP 호출이 길어 하나의 트랜잭션으로 묶지 않는다 — 커밋 한 건의 저장이
     * 각각의 트랜잭션이고, 중간에 실패해도 그때까지 저장된 활동은 남는다. 다음 동기화는
     * last_synced_at 이 갱신되지 않았으므로 같은 구간을 다시 훑고, 중복은 UNIQUE 로 걸러진다.
     *
     * @return 새로 저장한 활동 수
     */
    public int sync(Long repoId) {
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
            OffsetDateTime since = repo.getLastSyncedAt() != null
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
                try {
                    activityRepository.save(toActivity(repo, detail));
                    saved++;
                } catch (DataIntegrityViolationException e) {
                    // 동시에 같은 커밋이 들어온 경우. UNIQUE 제약이 최종 방어선이다.
                    log.debug("커밋 {} 는 이미 저장돼 있다.", summary.sha());
                }
            }

            repo.setLastSyncedAt(syncStartedAt);
            repoRepository.save(repo);
            log.info("리포 {} 동기화 완료 — 조회 {}건, 신규 {}건", repo.getFullName(), commits.size(), saved);
            return saved;
        } finally {
            inProgress.remove(repoId);
        }
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
