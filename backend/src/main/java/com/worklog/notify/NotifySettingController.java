package com.worklog.notify;

import com.worklog.auth.AuthenticatedUser;
import com.worklog.auth.User;
import com.worklog.auth.UserRepository;
import com.worklog.config.ApiException;
import com.worklog.notify.MattermostNotifier;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림 설정 (PRD 7. GET/PUT /settings/notify).
 */
@RestController
@RequestMapping("/settings/notify")
public class NotifySettingController {

    private final NotifySettingRepository settingRepository;
    private final UserRepository userRepository;

    public NotifySettingController(
            NotifySettingRepository settingRepository, UserRepository userRepository) {
        this.settingRepository = settingRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public NotifySettingResponse get(@AuthenticationPrincipal AuthenticatedUser principal) {
        return settingRepository
                .findByUserId(principal.id())
                .map(NotifySettingResponse::from)
                .orElseGet(() -> new NotifySettingResponse(null, true));
    }

    @PutMapping
    @Transactional
    public NotifySettingResponse update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody NotifySettingRequest request) {

        NotifySetting setting = settingRepository.findByUserId(principal.id()).orElseGet(() -> {
            User user = userRepository
                    .findById(principal.id())
                    .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
            NotifySetting created = new NotifySetting();
            created.setUser(user);
            return created;
        });

        String url = blankToNull(request.mattermostWebhookUrl());
        if (url != null && !MattermostNotifier.looksLikeWebhookUrl(url)) {
            throw ApiException.badRequest(
                    "NOT_A_WEBHOOK_URL",
                    "Incoming Webhook 주소가 아닙니다. Mattermost 통합 > Incoming Webhooks 에서 "
                            + "만든 \".../hooks/...\" 주소를 넣어 주세요.");
        }
        setting.setMattermostWebhookUrl(url);
        if (request.remindUncommitted() != null) {
            setting.setRemindUncommitted(request.remindUncommitted());
        }
        return NotifySettingResponse.from(settingRepository.save(setting));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record NotifySettingRequest(String mattermostWebhookUrl, Boolean remindUncommitted) {}

    public record NotifySettingResponse(String mattermostWebhookUrl, Boolean remindUncommitted) {

        static NotifySettingResponse from(NotifySetting setting) {
            return new NotifySettingResponse(
                    setting.getMattermostWebhookUrl(), setting.getRemindUncommitted());
        }
    }
}
