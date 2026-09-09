package com.worklog.notify;

import com.worklog.auth.User;
import com.worklog.draft.Draft;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 이벤트 조립 (PRD F7).
 *
 * <p>webhook URL 은 사용자별 설정 → 전역 설정 → 환경변수 순으로 찾는다.
 */
@Service
public class NotifyService {

    private static final Logger log = LoggerFactory.getLogger(NotifyService.class);

    /** 초안 생성 알림에 붙이는 완료 작업 줄 수 (PRD F7-1 — 상위 3줄). */
    private static final int PREVIEW_LINES = 3;

    private final Notifier notifier;
    private final NotifySettingRepository settingRepository;
    private final String defaultWebhookUrl;
    private final String frontendUrl;

    public NotifyService(
            Notifier notifier,
            NotifySettingRepository settingRepository,
            @Value("${worklog.notify.mattermost-webhook-url:}") String defaultWebhookUrl,
            @Value("${worklog.frontend-url}") String frontendUrl) {
        this.notifier = notifier;
        this.settingRepository = settingRepository;
        this.defaultWebhookUrl = defaultWebhookUrl;
        this.frontendUrl = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
    }

    /** 이벤트 1 — 초안 생성 완료 (PRD F7). */
    @Transactional(readOnly = true)
    public boolean notifyDraftCreated(Draft draft) {
        User user = draft.getUser();
        String text = """
                📝 %s의 %s 업무 일지 초안이 생성되었습니다. %s
                %s"""
                .formatted(
                        displayName(user),
                        draft.getWorkDate(),
                        draftLink(draft),
                        previewOf(draft.getContentMd()));
        return send(user.getId(), text);
    }

    /** 이벤트 3 — 사용자가 "Mattermost 전송"을 눌렀을 때 전체 Markdown 게시 (PRD F7). */
    @Transactional(readOnly = true)
    public boolean notifyDraftContent(Draft draft) {
        return send(draft.getUser().getId(), draft.getContentMd());
    }

    private boolean send(Long userId, String text) {
        String url = webhookUrlFor(userId);
        if (url == null) {
            log.info("webhook URL 이 설정되지 않아 알림을 보내지 않는다. (사용자 {})", userId);
            return false;
        }
        return notifier.send(url, text);
    }

    /** 사용자별 → 전역 → 환경변수. */
    String webhookUrlFor(Long userId) {
        Optional<String> perUser = settingRepository
                .findByUserId(userId)
                .map(NotifySetting::getMattermostWebhookUrl)
                .filter(url -> !url.isBlank());
        if (perUser.isPresent()) {
            return perUser.get();
        }
        Optional<String> global = settingRepository
                .findGlobal()
                .map(NotifySetting::getMattermostWebhookUrl)
                .filter(url -> !url.isBlank());
        if (global.isPresent()) {
            return global.get();
        }
        return defaultWebhookUrl == null || defaultWebhookUrl.isBlank() ? null : defaultWebhookUrl;
    }

    /** "완료한 작업" 상위 몇 줄만 미리보기로 붙인다. */
    static String previewOf(String contentMd) {
        List<String> lines = contentMd
                .lines()
                .dropWhile(line -> !line.startsWith("## 완료한 작업"))
                .skip(1)
                .takeWhile(line -> line.startsWith("- "))
                .limit(PREVIEW_LINES)
                .toList();
        return String.join("\n", lines);
    }

    private String draftLink(Draft draft) {
        return "%s/drafts/%d".formatted(frontendUrl, draft.getId());
    }

    private static String displayName(User user) {
        return user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getLogin();
    }
}
