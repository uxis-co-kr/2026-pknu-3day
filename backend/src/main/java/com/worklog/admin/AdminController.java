package com.worklog.admin;

import com.worklog.admin.dto.PeopleDirectoryResponse;
import com.worklog.auth.AuthenticatedUser;
import com.worklog.auth.User;
import com.worklog.auth.UserRepository;
import com.worklog.config.ApiException;
import com.worklog.llm.LlmSettingService;
import com.worklog.notify.NotifySetting;
import com.worklog.notify.NotifySettingRepository;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    public AdminController(
            PeopleDirectoryService directoryService,
            NotifySettingRepository notifySettingRepository,
            LlmSettingService llmSettingService,
            UserRepository userRepository) {
        this.directoryService = directoryService;
        this.notifySettingRepository = notifySettingRepository;
        this.llmSettingService = llmSettingService;
        this.userRepository = userRepository;
    }

    /** 콘솔 첫 화면이 무엇을 보여 줄 수 있는지 알려 준다. */
    @GetMapping("/overview")
    @Transactional(readOnly = true)
    public OverviewResponse overview(@AuthenticationPrincipal AuthenticatedUser principal) {
        PeopleDirectoryResponse directory = directoryService.directory();
        return new OverviewResponse(
                principal.login(),
                userRepository.count(),
                directory.employees().size(),
                directory.unclaimedContributors().size(),
                directory.wapleConfigured(),
                globalWebhookConfigured(),
                llmSettingService.get(principal.id()).provider());
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

    public record OverviewResponse(
            String adminLogin,
            long accountCount,
            int employeeCount,
            int unclaimedContributorCount,
            boolean wapleConfigured,
            boolean globalWebhookConfigured,
            String llmProvider) {}

    public record LinkEmployeeRequest(Long coSeq, Long empSeq) {}

    public record GlobalNotifyRequest(String mattermostWebhookUrl, Boolean remindUncommitted) {}

    public record GlobalNotifyResponse(String mattermostWebhookUrl, Boolean remindUncommitted) {}
}
