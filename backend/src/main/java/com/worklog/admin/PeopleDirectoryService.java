package com.worklog.admin;

import com.worklog.activity.ActivityRepository;
import com.worklog.admin.dto.PeopleDirectoryResponse;
import com.worklog.auth.ApiKeyRepository;
import com.worklog.auth.User;
import com.worklog.auth.UserRepository;
import com.worklog.github.GitHubCollector;
import com.worklog.github.Repo;
import com.worklog.github.RepoRepository;
import com.worklog.github.SyncStatus;
import com.worklog.vscode.VscodeSessionRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 콘솔의 직원·계정 현황 (TODO_0910 §1-3).
 *
 * <p>사원 한 줄에 "이 사람이 이 서비스를 쓸 준비가 됐는가"를 모아 보여 준다 —
 * 서비스 계정 · VS Code 연동 · GitHub 연동 · 활동 수. 셋 중 하나라도 비면 그 사람의
 * 업무 일지는 온전하지 않다.
 */
@Service
public class PeopleDirectoryService {

    private final WapleClient wapleClient;
    private final WapleProperties wapleProperties;
    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final VscodeSessionRepository sessionRepository;
    private final RepoRepository repoRepository;
    private final GitHubCollector collector;

    public PeopleDirectoryService(
            WapleClient wapleClient,
            WapleProperties wapleProperties,
            UserRepository userRepository,
            ActivityRepository activityRepository,
            ApiKeyRepository apiKeyRepository,
            VscodeSessionRepository sessionRepository,
            RepoRepository repoRepository,
            GitHubCollector collector) {
        this.wapleClient = wapleClient;
        this.wapleProperties = wapleProperties;
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.sessionRepository = sessionRepository;
        this.repoRepository = repoRepository;
        this.collector = collector;
    }

    @Transactional(readOnly = true)
    public PeopleDirectoryResponse directory() {
        Context ctx = loadContext();
        List<User> users = userRepository.findAll();

        Map<Long, User> byEmpSeq = new HashMap<>();
        users.stream()
                .filter(u -> u.getEmpSeq() != null)
                .forEach(u -> byEmpSeq.put(u.getEmpSeq(), u));

        Long companySeq = wapleProperties.getCompanySeq();
        List<PeopleDirectoryResponse.EmployeeRow> employees = new ArrayList<>();
        // 임시 사원 목록은 회사 번호가 없어도 읽는다 (사내망 밖에서 쓰는 용도).
        if (companySeq != null || !wapleClient.isConfigured()) {
            for (WapleClient.Employee e : wapleClient.employees(companySeq == null ? 0L : companySeq)) {
                User linked = byEmpSeq.get(e.empSeq());
                employees.add(new PeopleDirectoryResponse.EmployeeRow(
                        e.empSeq(), e.empNm(), linked == null ? null : toRow(linked, ctx)));
            }
        }

        List<PeopleDirectoryResponse.AccountRow> unlinked = users.stream()
                .filter(u -> u.getEmpSeq() == null)
                .map(u -> toRow(u, ctx))
                .sorted((a, b) -> Long.compare(b.activityCount(), a.activityCount()))
                .toList();

        return new PeopleDirectoryResponse(
                wapleClient.isConfigured(), companySeq, employees, unlinked, unclaimed());
    }

    /** 사용자별 집계를 한 번에 모은다 — 사원마다 쿼리를 돌리면 N+1 이 된다. */
    private Context loadContext() {
        Map<Long, Long> activity = new HashMap<>();
        for (Object[] row : activityRepository.countAllByUser()) {
            activity.put((Long) row[0], (Long) row[1]);
        }
        Map<Long, Map<Long, Long>> perRepo = new HashMap<>();
        for (Object[] row : activityRepository.countByUserAndRepo()) {
            perRepo.computeIfAbsent((Long) row[0], k -> new HashMap<>())
                    .put((Long) row[1], (Long) row[2]);
        }
        Map<Long, Long> apiKeys = new HashMap<>();
        for (Object[] row : apiKeyRepository.countByUser()) {
            apiKeys.put((Long) row[0], (Long) row[1]);
        }
        Map<Long, Long> sessions = new HashMap<>();
        for (Object[] row : sessionRepository.countAllByUser()) {
            sessions.put((Long) row[0], (Long) row[1]);
        }
        Map<Long, Map<Long, Long>> sessionsPerRepo = new HashMap<>();
        for (Object[] row : sessionRepository.countByUserAndRepo()) {
            sessionsPerRepo.computeIfAbsent((Long) row[0], k -> new HashMap<>())
                    .put((Long) row[1], (Long) row[2]);
        }
        // 등록자별로 나누지 않고 id 로 찾는다 — 등록하지 않은 사람의 리포도 붙여야 한다.
        Map<Long, Repo> reposById = new LinkedHashMap<>();
        for (Repo repo : repoRepository.findAllWithRegistrant()) {
            reposById.put(repo.getId(), repo);
        }
        return new Context(activity, perRepo, apiKeys, sessions, sessionsPerRepo, reposById);
    }

    private List<PeopleDirectoryResponse.ContributorRow> unclaimed() {
        List<PeopleDirectoryResponse.ContributorRow> rows = new ArrayList<>();
        for (Object[] row : activityRepository.countUnclaimedContributors()) {
            rows.add(new PeopleDirectoryResponse.ContributorRow(
                    (String) row[0], (Long) row[1], (OffsetDateTime) row[2]));
        }
        return rows;
    }

    private PeopleDirectoryResponse.AccountRow toRow(User user, Context ctx) {
        Long userId = user.getId();
        Map<Long, Long> perRepo = ctx.activityByUserAndRepo().getOrDefault(userId, Map.of());

        Map<Long, Long> perRepoSessions = ctx.sessionsByUserAndRepo().getOrDefault(userId, Map.of());

        // 등록했거나, 활동이 잡혔거나, VS 기록을 보낸 리포 — 셋을 합쳐 "연결된 리포" 로 본다.
        // 등록만 세면 팀원은 어느 리포에도 붙어 있지 않은 것처럼 보인다 (BACKLOG2 §2-4).
        Set<Long> linked = new LinkedHashSet<>();
        ctx.reposById().values().stream()
                .filter(r -> r.getRegisteredBy() != null && r.getRegisteredBy().getId().equals(userId))
                .forEach(r -> linked.add(r.getId()));
        linked.addAll(perRepo.keySet());
        linked.addAll(perRepoSessions.keySet());

        List<PeopleDirectoryResponse.RepoRow> repos = linked.stream()
                .map(id -> ctx.reposById().get(id))
                .filter(java.util.Objects::nonNull)
                .map(r -> new PeopleDirectoryResponse.RepoRow(
                        r.getId(),
                        r.getFullName(),
                        r.getDefaultBranch(),
                        r.getLastSyncedAt(),
                        syncStatusOf(r).name(),
                        r.getRegisteredBy() != null && r.getRegisteredBy().getId().equals(userId),
                        perRepo.getOrDefault(r.getId(), 0L),
                        perRepoSessions.getOrDefault(r.getId(), 0L)))
                // 등록한 리포를 앞에, 그다음 활동이 많은 순.
                .sorted((a, b) -> a.registered() != b.registered()
                        ? Boolean.compare(b.registered(), a.registered())
                        : Long.compare(b.activityCount(), a.activityCount()))
                .toList();

        return new PeopleDirectoryResponse.AccountRow(
                userId,
                user.getLogin(),
                user.getName(),
                user.getAvatarUrl(),
                user.getRole().name(),
                // 토큰이 있어야 수집기가 그 사람 권한으로 리포를 읽는다.
                user.getGithubTokenEnc() != null,
                // 키만 발급하고 확장을 안 쓰는 경우가 흔하다. 실제로 보낸 적이 있어야 연동으로 본다.
                ctx.sessionsByUser().getOrDefault(userId, 0L) > 0,
                ctx.apiKeysByUser().getOrDefault(userId, 0L),
                ctx.sessionsByUser().getOrDefault(userId, 0L),
                // 행 수가 아니라 저장소 수다. 같은 저장소라도 브랜치·날짜가 다르면 행이 는다 —
                // 그 수를 "저장소" 라고 적으면 두 곳에서 일한 사람이 셋으로 보인다 (9/11).
                repos.stream().filter(r -> r.sessionCount() > 0).count(),
                user.getEmpSeq(),
                ctx.activityByUser().getOrDefault(userId, 0L),
                user.getCreatedAt(),
                repos);
    }

    private SyncStatus syncStatusOf(Repo repo) {
        return collector.isSyncing(repo.getId()) ? SyncStatus.SYNCING : repo.getLastSyncStatus();
    }

    private record Context(
            Map<Long, Long> activityByUser,
            Map<Long, Map<Long, Long>> activityByUserAndRepo,
            Map<Long, Long> apiKeysByUser,
            Map<Long, Long> sessionsByUser,
            Map<Long, Map<Long, Long>> sessionsByUserAndRepo,
            Map<Long, Repo> reposById) {}
}
