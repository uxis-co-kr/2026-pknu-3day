package com.worklog.draft;

import com.worklog.config.ApiException;
import com.worklog.notify.NotifyService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 초안 Mattermost 전송 (PRD 7. POST /drafts/{id}/notify — F7, 담당자 2).
 *
 * <p>담당자 1의 {@code DraftController} 와 파일을 나눠 둔다 (PRD 2).
 */
@RestController
@RequestMapping("/drafts")
public class DraftNotifyController {

    private final DraftRepository draftRepository;
    private final NotifyService notifyService;

    public DraftNotifyController(DraftRepository draftRepository, NotifyService notifyService) {
        this.draftRepository = draftRepository;
        this.notifyService = notifyService;
    }

    @PostMapping("/{id}/notify")
    @Transactional(readOnly = true)
    public ResponseEntity<NotifyResponse> notifyDraft(@PathVariable Long id) {
        Draft draft = draftRepository
                .findById(id)
                .orElseThrow(() -> ApiException.notFound("DRAFT_NOT_FOUND", "초안을 찾을 수 없습니다."));

        if (!notifyService.notifyDraftContent(draft)) {
            // 설정이 없거나 전송에 실패한 경우. 화면이 "설정하세요"를 띄울 수 있게 구분해 알린다.
            throw new ApiException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "NOTIFY_FAILED",
                    "Mattermost 전송에 실패했습니다. /settings 에서 webhook URL 을 확인해 주세요.");
        }
        return ResponseEntity.ok(new NotifyResponse(true));
    }

    public record NotifyResponse(boolean sent) {}
}
