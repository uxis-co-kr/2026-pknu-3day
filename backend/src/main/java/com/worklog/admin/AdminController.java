package com.worklog.admin;

import com.worklog.activity.ActivityRepository;
import com.worklog.activity.SummaryStatus;
import com.worklog.admin.dto.PeopleDirectoryResponse;
import com.worklog.chat.MattermostBot;
import com.worklog.auth.AuthenticatedUser;
import com.worklog.auth.User;
import com.worklog.auth.UserRepository;
import com.worklog.config.ApiException;
import com.worklog.github.GitHubCollector;
import com.worklog.github.Repo;
import com.worklog.github.RepoRepository;
import com.worklog.llm.LlmSettingService;
import com.worklog.llm.SummaryService;
import com.worklog.notify.NotifySetting;
import com.worklog.notify.NotifySettingRepository;
import com.worklog.notify.MattermostNotifier;
import com.worklog.notify.Notifier;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 콘솔 (TODO_0910 §1-3).
 *
 * <p>{@code /admin/**} 전체가 {@code SecurityConfig} 에서 ADMIN 권한으로 잠겨 있다.
 * 로그인만으로는 들어올 수 없다.
 *
 * <p>콘솔이 다루는 것은 넷이다 — 팀원 전체 내역, LLM 모델, Mattermost 웹훅,
 * 회사 직원 목록과 GitHub 활성화 상태. 앞의 셋은 이미 있는 엔드포인트를 그대로 쓰고
 * (개인 설정이 아니라 <b>전역</b> 설정을 다룬다는 점만 다르다), 넷째가 여기서 새로 생긴다.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final PeopleDirectoryService directoryService;
    private final NotifySettingRepository notifySettingRepository;
    private final LlmSettingService llmSettingService;
    private final UserRepository userRepository;
    private final Notifier notifier;
    private final ActivityRepository activityRepository;
    private final RepoRepository repoRepository;
    private final GitHubCollector collector;
    private final SummaryService summaryService;
    private final MattermostBot mattermostBot;

    public AdminController(
            PeopleDirectoryService directoryService,
            NotifySettingRepository notifySettingRepository,
            LlmSettingService llmSettingService,
            UserRepository userRepository,
            Notifier notifier,
            ActivityRepository activityRepository,
            RepoRepository repoRepository,
            GitHubCollector collector,
            SummaryService summaryService,
            MattermostBot mattermostBot) {
        this.directoryService = directoryService;
        this.notifySettingRepository = notifySettingRepository;
        this.llmSettingService = llmSettingService;
        this.userRepository = userRepository;
        this.notifier = notifier;
        this.activityRepository = activityRepository;
        this.repoRepository = repoRepository;
        this.collector = collector;
        this.summaryService = summaryService;
        this.mattermostBot = mattermostBot;
    }

    /** 콘솔 첫 화면이 무엇을 보여 줄 수 있는지 알려 준다. */
    /** 채널 질의 응답 봇의 상태 — 켜졌는지, 로그인됐는지, 어느 채널을 보는지. */
    @GetMapping("/chat/status")
    public java.util.Map<String, Object> chatStatus() {
        return mattermostBot.status();
    }

    @GetMapping("/overview")
    @Transactional(readOnly = true)
    public OverviewResponse overview(@AuthenticationPrincipal AuthenticatedUser principal) {
        PeopleDirectoryResponse directory = directoryService.directory();
        long pending = 0;
        long failed = 0;
        for (Object[] row : activityRepository.countBySummaryStatus()) {
            SummaryStatus status = (SummaryStatus) row[0];
            long count = (Long) row[1];
            if (status == SummaryStatus.PENDING) {
                pending = count;
            } else if (status == SummaryStatus.FAILED) {
                failed = count;
            }
        }
        List<Repo> repos = repoRepository.findAllWithRegistrant();
        return new OverviewResponse(
                principal.login(),
                userRepository.count(),
                directory.employees().size(),
                directory.unclaimedContributors().size(),
                directory.wapleConfigured(),
                globalWebhookConfigured(),
                llmSettingService.get(principal.id()).provider(),
                repos.size(),
                repos.stream().filter(r -> collector.isSyncing(r.getId())).count(),
                repos.stream().anyMatch(r -> r.getLastSyncStatus() != com.worklog.github.SyncStatus.OK),
                activityRepository.count(),
                pending,
                failed);
    }

    /**
     * 등록된 리포를 한꺼번에 동기화한다 (TODO_0910 §1-2 "전체 동기화").
     *
     * <p>지금은 리포 관리 화면에서 한 건씩 눌러야 한다. 리포가 늘면 손이 많이 가고,
     * 무엇보다 "지금 전부 최신인가" 를 한 번에 맞출 방법이 없다.
     */
    @PostMapping("/repos/sync-all")
    public SyncAllResponse syncAll(
            @RequestParam(defaultValue = "false") boolean full) {
        List<Repo> repos = repoRepository.findAllWithRegistrant();
        repos.forEach(repo -> collector.syncAsync(repo.getId(), full));
        return new SyncAllResponse(repos.size(), full);
    }

    /**
     * 요약을 한 번 더 돌린다.
     *
     * <p>스케줄러가 1분마다 돌지만, 모델을 바꾼 직후나 실패가 쌓였을 때 기다리지 않고
     * 확인하려면 손으로 밀 수 있어야 한다.
     */
    @PostMapping("/summaries/run")
    public SummaryRunResponse runSummaries() {
        return new SummaryRunResponse(summaryService.runOnce());
    }

    /** 회사 직원 목록 + GitHub 활성화 상태 (§1-3 넷째). */
    @GetMapping("/people")
    public PeopleDirectoryResponse people() {
        return directoryService.directory();
    }

    /** 사원과 계정을 잇는다. 동명이인이 있어 이름이 아니라 사원 번호로 잇는다. */
    @PutMapping("/users/{userId}/employee")
    @Transactional
    public PeopleDirectoryResponse.AccountRow linkEmployee(
            @PathVariable Long userId, @RequestBody LinkEmployeeRequest request) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));

        if (request.empSeq() != null) {
            userRepository.findByEmpSeq(request.empSeq()).stream()
                    .filter(other -> !other.getId().equals(userId))
                    .findFirst()
                    .ifPresent(other -> {
                        throw ApiException.conflict(
                                "EMPLOYEE_ALREADY_LINKED",
                                "이미 %s 계정에 연결된 사원입니다.".formatted(other.getLogin()));
                    });
        }
        user.setCoSeq(request.coSeq());
        user.setEmpSeq(request.empSeq());
        userRepository.save(user);
        return directoryService.directory().unlinkedAccounts().stream()
                .filter(row -> row.userId().equals(userId))
                .findFirst()
                .orElseGet(() -> directoryService.directory().employees().stream()
                        .map(PeopleDirectoryResponse.EmployeeRow::account)
                        .filter(a -> a != null && a.userId().equals(userId))
                        .findFirst()
                        .orElse(null));
    }

    /** 전역 Mattermost 웹훅 (§1-3 셋째). 사용자별 설정이 없을 때 이 값이 쓰인다. */
    @GetMapping("/settings/notify")
    @Transactional(readOnly = true)
    public GlobalNotifyResponse getGlobalNotify() {
        return notifySettingRepository
                .findGlobal()
                .map(s -> new GlobalNotifyResponse(s.getMattermostWebhookUrl(), s.getRemindUncommitted()))
                .orElseGet(() -> new GlobalNotifyResponse(null, true));
    }

    @PutMapping("/settings/notify")
    @Transactional
    public GlobalNotifyResponse updateGlobalNotify(@RequestBody GlobalNotifyRequest request) {
        // 시험 전송만 막아 두면, 시험은 성공해도 엉뚱한 주소가 저장된 채로 남는다.
        requireWebhookShape(request.mattermostWebhookUrl());

        NotifySetting setting = notifySettingRepository.findGlobal().orElseGet(NotifySetting::new);
        setting.setUser(null); // 전역 행은 user_id 가 null 이다 (V1 스키마에서 1건으로 제한)
        setting.setMattermostWebhookUrl(blankToNull(request.mattermostWebhookUrl()));
        if (request.remindUncommitted() != null) {
            setting.setRemindUncommitted(request.remindUncommitted());
        }
        notifySettingRepository.save(setting);
        return new GlobalNotifyResponse(
                setting.getMattermostWebhookUrl(), setting.getRemindUncommitted());
    }

    /**
     * 웹훅이 실제로 닿는지 확인한다.
     *
     * <p>저장만으로는 주소가 맞는지 알 수 없다. 초안을 만들어 보는 것 말고는 확인할 방법이
     * 없으면 설정을 틀린 채로 두게 된다. 본문에 주소를 받아 <b>저장하지 않고</b> 그 주소로만
     * 쏘므로, 지금 입력한 값이 맞는지 저장 전에 확인할 수 있다.
     */
    @PostMapping("/settings/notify/test")
    @Transactional(readOnly = true)
    public TestNotifyResponse testNotify(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody(required = false) TestNotifyRequest request) {

        String url = request == null ? null : blankToNull(request.mattermostWebhookUrl());
        if (url == null) {
            // 입력이 없으면 저장된 전역 설정으로 시험한다.
            url = notifySettingRepository
                    .findGlobal()
                    .map(NotifySetting::getMattermostWebhookUrl)
                    .map(AdminController::blankToNull)
                    .orElse(null);
        }
        if (url == null) {
            throw ApiException.badRequest(
                    "WEBHOOK_NOT_SET", "웹훅 주소를 입력하거나 먼저 저장해 주세요.");
        }

        requireWebhookShape(url);

        String text = "✅ WorkLog Drafter 연결 확인 — %s 님이 관리자 콘솔에서 보냈습니다."
                .formatted(principal.login());
        boolean sent = notifier.send(url, text);
        if (!sent) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "NOTIFY_FAILED",
                    "메시지를 보내지 못했습니다. 주소가 맞는지, 채널이 살아 있는지 확인해 주세요.");
        }
        return new TestNotifyResponse(true, text);
    }

    /** 비어 있는 것은 허용한다 — 알림을 끄는 방법이다. 값이 있으면 웹훅 모양이어야 한다. */
    private static void requireWebhookShape(String url) {
        String trimmed = blankToNull(url);
        if (trimmed == null || MattermostNotifier.looksLikeWebhookUrl(trimmed)) {
            return;
        }
        throw ApiException.badRequest(
                "NOT_A_WEBHOOK_URL",
                "Incoming Webhook 주소가 아닙니다. 채널을 연 브라우저 주소가 아니라 "
                        + "Mattermost 통합 > Incoming Webhooks 에서 만든 \".../hooks/...\" 주소를 넣어 주세요.");
    }

    private boolean globalWebhookConfigured() {
        return notifySettingRepository
                .findGlobal()
                .map(NotifySetting::getMattermostWebhookUrl)
                .filter(url -> !url.isBlank())
                .isPresent();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * @param syncingRepoCount 지금 수집 중인 리포 수
     * @param anyRepoSyncFailed 마지막 수집이 실패한 리포가 있는지
     * @param pendingSummaryCount 아직 요약되지 않은 활동 — 밀려 있으면 초안이 부실해진다
     * @param failedSummaryCount 3회까지 실패해 포기한 활동
     */
    public record OverviewResponse(
            String adminLogin,
            long accountCount,
            int employeeCount,
            int unclaimedContributorCount,
            boolean wapleConfigured,
            boolean globalWebhookConfigured,
            String llmProvider,
            int repoCount,
            long syncingRepoCount,
            boolean anyRepoSyncFailed,
            long activityCount,
            long pendingSummaryCount,
            long failedSummaryCount) {}

    public record SyncAllResponse(int repoCount, boolean full) {}

    public record SummaryRunResponse(int summarized) {}

    public record LinkEmployeeRequest(Long coSeq, Long empSeq) {}

    public record GlobalNotifyRequest(String mattermostWebhookUrl, Boolean remindUncommitted) {}

    public record TestNotifyRequest(String mattermostWebhookUrl) {}

    public record TestNotifyResponse(boolean sent, String text) {}

    public record GlobalNotifyResponse(String mattermostWebhookUrl, Boolean remindUncommitted) {}
}
