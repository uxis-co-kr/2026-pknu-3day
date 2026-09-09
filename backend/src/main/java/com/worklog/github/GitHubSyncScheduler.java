package com.worklog.github;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 10분마다 전체 리포를 동기화한다 (PRD F1-2, {@code GITHUB_SYNC_CRON}).
 *
 * <p>리포별로 비동기 작업을 던지고, 겹치는 실행은 {@link GitHubCollector} 가 걸러낸다.
 */
@Component
public class GitHubSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(GitHubSyncScheduler.class);

    private final RepoRepository repoRepository;
    private final GitHubCollector collector;

    public GitHubSyncScheduler(RepoRepository repoRepository, GitHubCollector collector) {
        this.repoRepository = repoRepository;
        this.collector = collector;
    }

    @Scheduled(cron = "${worklog.github.sync-cron}")
    public void syncAll() {
        var repos = repoRepository.findAll();
        if (repos.isEmpty()) {
            return;
        }
        log.info("정기 동기화 시작 — 리포 {}개", repos.size());
        repos.forEach(repo -> collector.syncAsync(repo.getId()));
    }
}
