package com.worklog.notify;

import java.time.LocalDate;
import com.worklog.config.KstDates;

import com.worklog.auth.User;
import com.worklog.draft.Draft;
import com.worklog.vscode.VscodeSession;
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
 * <p>두 가지만 나간다 — 미커밋 리마인드, 그리고 사원이 [Mattermost 전송] 을 눌렀을 때의
 * 요약 알림. <b>초안을 만든 것 자체는 알리지 않는다</b> (9/11 결정). AI 생성을 누를 때마다
 * 관리자 채널에 글이 쌓였는데, 아직 사람이 손대지 않은 초안이라 알릴 것이 못 된다.
 *
 * <p>webhook URL 은 사용자별 설정 → 전역 설정 → 환경변수 순으로 찾는다. 다만 요약 알림은
 * 관리자가 콘솔에서 고른 대표 채널(또는 전역 웹훅)로만 간다 — 개인 채널로 새면 안 된다.
 */
@Service
public class NotifyService {

    private static final Logger log = LoggerFactory.getLogger(NotifyService.class);

    private final Notifier notifier;
    private final NotifySettingRepository settingRepository;
    private final com.worklog.chat.MattermostBot bot;
    private final String defaultWebhookUrl;
    private final String frontendUrl;

    public NotifyService(
            Notifier notifier,
            NotifySettingRepository settingRepository,
            com.worklog.chat.MattermostBot bot,
            @Value("${worklog.notify.mattermost-webhook-url:}") String defaultWebhookUrl,
            @Value("${worklog.frontend-url}") String frontendUrl) {
        this.notifier = notifier;
        this.settingRepository = settingRepository;
        this.bot = bot;
        this.defaultWebhookUrl = defaultWebhookUrl;
        this.frontendUrl = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
    }

    /**
     * 이벤트 2 — 미커밋 리마인드 (PRD F7).
     *
     * <p>{@code notify_settings.remind_uncommitted} 가 꺼진 사용자는 보내지 않는다.
     */
    @Transactional(readOnly = true)
    public boolean notifyUncommitted(VscodeSession session, java.time.OffsetDateTime now) {
        Long userId = session.getUser() == null ? null : session.getUser().getId();
        if (!remindEnabled(userId)) {
            log.debug("사용자 {} 는 미커밋 리마인드를 껐다.", userId);
            return false;
        }
        String repo = session.getRepo() != null ? session.getRepo().getFullName() : session.getRemoteUrl();
        int files = session.getUncommittedFiles() == null ? 0 : session.getUncommittedFiles().size();

        String text = "⚠️ %s@%s에 미커밋 변경 %d파일이 %d시간째 있습니다."
                .formatted(repo, session.getBranch(), files, RemindPolicy.hoursSinceLastCommit(session, now));
        return send(userId, text);
    }

    /** 기본값은 켜짐. 설정 행이 없으면 보낸다. */
    boolean remindEnabled(Long userId) {
        if (userId == null) {
            return false;
        }
        return settingRepository
                .findByUserId(userId)
                .map(NotifySetting::getRemindUncommitted)
                .orElse(true);
    }

    /**
     * 이벤트 3 — 사용자가 "Mattermost 전송"을 눌렀을 때 (PRD F7, 9/11 변경).
     *
     * <p>전에는 초안 Markdown 전체를 게시했다. 이제는 <b>관리자에게 "요약이 끝났다" 고 알리는 한 줄</b>이다 —
     * 본문은 링크 너머에 있고, 관리자가 요약본을 받도록 정해 둔 채팅방(콘솔의 전역 웹훅)으로 간다.
     * 사용자별 웹훅은 그 사람 개인 채널이라 여기서는 쓰지 않는다.
     */
    @Transactional(readOnly = true)
    public boolean notifyDraftSummarized(Draft draft) {
        String message = summarizedMessage(draft, KstDates.today());
        // 대표 채널(V12)이 정해져 있으면 봇 계정이 그 채널에 쓴다 — 관리자가 콘솔에서 고른 곳이다.
        // 봇은 답을 읽을 수 있으니 예/아니오를 묻고, "예" 면 요약본을 이어서 보낸다. 웹훅은 못 읽으므로 묻지 않는다.
        if (bot != null && bot.postToPrimaryChannel(withConfirmQuestion(message), draft.getId())) {
            return true;
        }
        String url = adminWebhookUrl();
        if (url == null) {
            log.info("대표 채널도 전역 웹훅도 설정되지 않아 알리지 않는다. (초안 {})", draft.getId());
            return false;
        }
        return notifier.send(url, message);
    }

    /** "조웅식의 오늘(2026-09-11)의 업무일지가 요약되었습니다. 확인해주시기 바랍니다." + 링크. */
    String summarizedMessage(Draft draft, LocalDate today) {
        return "%s의 %s의 업무일지가 요약되었습니다. 확인해주시기 바랍니다.\n🔗 %s"
                .formatted(displayName(draft.getUser()), dayLabel(draft.getWorkDate(), today), draftLink(draft));
    }

    /**
     * 링크 줄 앞에 예/아니오 안내를 끼운다.
     *
     * <p>봇이 글 아래에 ✅ ❌ 를 미리 달아 두므로 누르기만 하면 된다. 버튼(interactive message)은
     * Mattermost 가 <b>우리를</b> 불러야 해서 사내망 설정이 필요했다 — 반응은 그 설정 없이 된다.
     */
    static String withConfirmQuestion(String message) {
        int link = message.indexOf("\n🔗 ");
        String question = "아래 ✅ 를 누르시면 요약본을 보내 드립니다. ❌ 는 취소입니다. (**예**/**아니오**라고 답해도 됩니다)";
        return link < 0
                ? message + "\n" + question
                : message.substring(0, link) + "\n" + question + message.substring(link);
    }

    /** 오늘(2026-09-11) · 어제(2026-09-10) · 2026-09-09 — 어느 날 것인지 한눈에. */
    static String dayLabel(LocalDate workDate, LocalDate today) {
        if (workDate.equals(today)) {
            return "오늘(" + workDate + ")";
        }
        if (workDate.equals(today.minusDays(1))) {
            return "어제(" + workDate + ")";
        }
        return workDate.toString();
    }

    /** 관리자가 요약본을 받도록 정해 둔 채팅방 — 전역 설정 → 환경변수. */
    String adminWebhookUrl() {
        Optional<String> global = settingRepository
                .findGlobal()
                .map(NotifySetting::getMattermostWebhookUrl)
                .filter(url -> !url.isBlank());
        if (global.isPresent()) {
            return global.get();
        }
        return defaultWebhookUrl == null || defaultWebhookUrl.isBlank() ? null : defaultWebhookUrl;
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

    private String draftLink(Draft draft) {
        return "%s/drafts/%d".formatted(frontendUrl, draft.getId());
    }

    private static String displayName(User user) {
        return user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getLogin();
    }
}
