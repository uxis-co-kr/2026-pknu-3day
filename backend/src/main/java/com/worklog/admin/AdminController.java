package com.worklog.admin;

import com.worklog.activity.ActivityRepository;
import com.worklog.activity.SummaryStatus;
import com.worklog.admin.dto.PeopleDirectoryResponse;
import com.worklog.chat.ChatBotSettingsService;
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
    private final ChatBotSettingsService chatBotSettings;
    private final com.worklog.auth.AdminAccountInitializer adminAccount;
    private final com.worklog.chat.WorkLogAnswerService answerService;
    private final com.worklog.vscode.VscodeSessionRepository sessionRepository;
    private final com.worklog.vscode.AiSessionSummarizer aiSummarizer;

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
            MattermostBot mattermostBot,
            ChatBotSettingsService chatBotSettings,
            com.worklog.auth.AdminAccountInitializer adminAccount,
            com.worklog.chat.WorkLogAnswerService answerService,
            com.worklog.vscode.VscodeSessionRepository sessionRepository,
            com.worklog.vscode.AiSessionSummarizer aiSummarizer) {
        this.sessionRepository = sessionRepository;
        this.aiSummarizer = aiSummarizer;
        this.adminAccount = adminAccount;
        this.answerService = answerService;
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
        this.chatBotSettings = chatBotSettings;
    }

    /** 콘솔 첫 화면이 무엇을 보여 줄 수 있는지 알려 준다. */
    // ── 채널에서 물어보기 (봇) ───────────────────────────────────────────────

    /** 봇의 상태 — 켜졌는지, 로그인됐는지, 채널마다 읽는지·몇 번 답했는지. */
    @GetMapping("/chat/status")
    public java.util.Map<String, Object> chatStatus() {
        return mattermostBot.status();
    }

    /** 저장된 연결 설정. 비밀번호는 돌려주지 않고 있는지만 알려 준다. */
    @GetMapping("/chat/settings")
    public ChatSettingsResponse chatSettings() {
        ChatBotSettingsService.Effective e = chatBotSettings.effective();
        return new ChatSettingsResponse(
                e.baseUrl(), e.loginId(), e.password() != null && !e.password().isBlank(), e.enabled(), e.source());
    }

    /**
     * 연결 — 저장하고 바로 로그인해 본다. 안 되면 400 에 이유가 실려 화면이 그대로 보여 준다.
     * 저장만 하고 나중에 조용히 실패하면 관리자는 어디가 틀렸는지 알 길이 없다.
     */
    @PutMapping("/chat/settings")
    public java.util.Map<String, Object> saveChatSettings(@RequestBody ChatSettingsRequest request) {
        if (request.baseUrl() == null || request.baseUrl().isBlank()
                || request.loginId() == null || request.loginId().isBlank()) {
            throw ApiException.badRequest("CHAT_SETTINGS_INCOMPLETE", "서버 주소와 아이디를 입력해 주세요.");
        }
        chatBotSettings.save(request.baseUrl(), request.loginId(), request.password());
        return mattermostBot.connectNow();
    }

    /** 연결 끊기 — 설정은 남기고 봇만 멈춘다. 다시 "연결"을 누르면 그 설정으로 붙는다. */
    @PostMapping("/chat/disconnect")
    public java.util.Map<String, Object> disconnectChat() {
        chatBotSettings.setEnabled(false);
        mattermostBot.disconnect();
        return mattermostBot.status();
    }

    /**
     * 대표 채널 — 사원이 [Mattermost 전송] 을 누르면 "요약되었습니다" 알림이 가는 채널 (V14).
     * {@code channelId} 를 비우면 해제하고, 그때는 전역 웹훅으로 간다.
     */
    @PutMapping("/chat/primary-channel")
    public java.util.Map<String, Object> setPrimaryChannel(@RequestBody PrimaryChannelRequest request) {
        chatBotSettings.setPrimaryChannel(request.channelId(), mattermostBot.knownChannelIds());
        return mattermostBot.status();
    }

    public record PrimaryChannelRequest(String channelId) {}

    /** 채널 하나를 읽을지 말지. */
    @PutMapping("/chat/channels/{channelId}")
    public java.util.Map<String, Object> setChannelWatching(
            @PathVariable String channelId, @RequestBody ChannelWatchRequest request) {
        chatBotSettings.setWatching(channelId, request.watching(), mattermostBot.knownChannelIds());
        return mattermostBot.status();
    }

    /**
     * 채널에 쓰지 않고 봇에게 바로 묻는다 — 같은 파서·같은 답이다. Mattermost 없이 시험하고,
     * 콘솔에 입력창을 붙일 때 그대로 쓴다.
     */
    @PostMapping("/chat/ask")
    public AskResponse ask(@RequestBody AskRequest request) {
        if (request.text() == null || request.text().isBlank()) {
            throw ApiException.badRequest("TEXT_REQUIRED", "물어볼 말을 적어 주세요.");
        }
        return new AskResponse(request.text(), answerService.answer(request.text()).orElse(null));
    }

    public record AskRequest(String text) {}

    /** @param answer 업무 일지를 묻는 말이 아니면 null — 채널에서도 그때는 답하지 않는다 */
    public record AskResponse(String text, String answer) {}

    public record ChatSettingsResponse(
            String baseUrl, String loginId, boolean passwordSet, boolean enabled, String source) {}

    public record ChatSettingsRequest(String baseUrl, String loginId, String password) {}

    public record ChannelWatchRequest(boolean watching) {}

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
                failed,
                adminAccount.usesDefaultPassword());
    }

    /**
     * 비밀번호를 사원번호로 되돌린다 (DAY3_plan C-3).
     *
     * <p>최초 비밀번호가 사원번호라 남이 먼저 들어갈 수 있다는 것은 회의가 알고 유지한 결정이다.
     * 정책을 뒤집지 않고 <b>사고 뒤 되돌릴 수단</b>만 둔다 — 초기화하면 이전 토큰이 전부 죽고
     * (password_changed_at), 본인이 다시 들어와 바꾼다.
     */
    @PostMapping("/users/{userId}/password-reset")
    @Transactional
    public PasswordResetResponse resetPassword(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long userId) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
        if (user.getLoginId() == null || user.getLoginId().isBlank()) {
            throw ApiException.badRequest("NO_LOCAL_LOGIN", "이 계정은 GitHub 으로만 로그인합니다. 되돌릴 비밀번호가 없습니다.");
        }
        if (user.getRole() == com.worklog.auth.UserRole.ADMIN && !user.getId().equals(principal.id())) {
            // 다른 관리자를 잠그는 길은 두지 않는다. 자기 것은 설정에서 바꾸면 된다.
            throw ApiException.forbidden("CANNOT_RESET_ADMIN", "다른 관리자의 비밀번호는 초기화할 수 없습니다.");
        }
        user.setPasswordHash(com.worklog.auth.PasswordHasher.hash(user.getLoginId()));
        user.setMustChangePassword(true);
        user.setPasswordChangedAt(java.time.OffsetDateTime.now());
        userRepository.save(user);
        return new PasswordResetResponse(user.getId(), user.getLoginId());
    }

    /** @param loginId 초기화된 비밀번호는 이 값(사원번호)과 같다 */
    public record PasswordResetResponse(Long userId, String loginId) {}

    /**
     * 등록된 리포를 한꺼번에 동기화한다 (TODO_0910 §1-2 "전체 동기화").
     *
     * <p>지금은 리포 관리 화면에서 한 건씩 눌러야 한다. 리포가 늘면 손이 많이 가고,
     * 무엇보다 "지금 전부 최신인가" 를 한 번에 맞출 방법이 없다.
     */
    /**
     * @param days {@code full} 일 때 거슬러 올라갈 날 수. 기본 7일로는 한동안 손대지 않은
     *     저장소가 통째로 비어 보인다 — 마지막 커밋이 2주 전이면 창 밖이라 한 건도 들어오지
     *     않는다 (9/11 확인). 1~365 로 묶는다.
     */
    @PostMapping("/repos/sync-all")
    public SyncAllResponse syncAll(
            @RequestParam(defaultValue = "false") boolean full,
            @RequestParam(defaultValue = "7") int days) {
        int window = Math.clamp(days, 1, 365);
        List<Repo> repos = repoRepository.findAllWithRegistrant();
        repos.forEach(repo -> collector.syncAsync(repo.getId(), full, window));
        return new SyncAllResponse(repos.size(), full, window);
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

    /**
     * 요약이 빠진 AI 대화를 채운다.
     *
     * <p>대화 요약은 확장이 보낼 때 채운다. 지난 세션은 다시 전송될 일이 없어 영영 빈 채로
     * 남는다 — 요약 기능이 생기기 전에 끝난 날이 그렇다. VS 내역 탭은 이제 요약만 보여 주므로
     * (9/11), 그 날들이 빈칸으로 남는다. 여기서 한 번 훑어 채운다.
     *
     * <p>세션마다 비동기로 돈다. 돌려주는 수는 "채운 건수" 가 아니라 "훑기 시작한 세션 수" 다.
     */
    @PostMapping("/ai-summaries/run")
    public AiSummaryRunResponse runAiSummaries() {
        List<Long> ids = sessionRepository.findIdsWithUnsummarizedAi();
        ids.forEach(aiSummarizer::summarizeMissing);
        return new AiSummaryRunResponse(ids.size());
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
            long failedSummaryCount,
            /** 관리자 비밀번호가 아직 기본값인가 — 콘솔이 띠를 띄운다 (BACKLOG2 §2-2) */
            boolean defaultAdminPassword) {}

    public record SyncAllResponse(int repoCount, boolean full, int days) {}

    public record SummaryRunResponse(int summarized) {}

    public record AiSummaryRunResponse(int sessions) {}

    public record LinkEmployeeRequest(Long coSeq, Long empSeq) {}

    public record GlobalNotifyRequest(String mattermostWebhookUrl, Boolean remindUncommitted) {}

    public record TestNotifyRequest(String mattermostWebhookUrl) {}

    public record TestNotifyResponse(boolean sent, String text) {}

    public record GlobalNotifyResponse(String mattermostWebhookUrl, Boolean remindUncommitted) {}
}
