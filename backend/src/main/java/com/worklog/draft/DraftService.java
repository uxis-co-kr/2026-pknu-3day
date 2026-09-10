package com.worklog.draft;

import com.worklog.activity.Activity;
import com.worklog.activity.ActivityRepository;
import com.worklog.activity.dto.ActivityResponse;
import com.worklog.config.ApiException;
import com.worklog.draft.dto.DraftDetailResponse;
import com.worklog.draft.dto.DraftSummaryResponse;
import com.worklog.vscode.SessionResponse;
import com.worklog.vscode.VscodeSession;
import com.worklog.vscode.VscodeSessionRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 초안 조회·수정·확정 (PRD F3b, 담당자 1). 생성은 담당자 2의 {@link DraftGenerator} 다.
 *
 * <p>응답 매핑을 전부 트랜잭션 안에서 끝낸다. Activity 의 repo/user, Session 의 repo 가 LAZY 라
 * 컨트롤러에서 매핑하면 LazyInitializationException 이 난다.
 */
@Service
public class DraftService {

    private final DraftRepository drafts;
    private final ActivityRepository activities;
    private final VscodeSessionRepository sessions;

    public DraftService(
            DraftRepository drafts, ActivityRepository activities, VscodeSessionRepository sessions) {
        this.drafts = drafts;
        this.activities = activities;
        this.sessions = sessions;
    }

    /**
     * 그날의 초안 목록. 같은 (사용자, 날짜) 에 여러 버전이 있으면 최신 버전만 준다 —
     * 홈 화면은 사용자당 배지 하나를 그린다.
     */
    @Transactional(readOnly = true)
    public List<DraftSummaryResponse> list(LocalDate date, Long userId, DraftStatus status) {
        return drafts.findLatestByWorkDate(date).stream()
                .filter(d -> userId == null || d.getUser().getId().equals(userId))
                .filter(d -> status == null || d.getStatus() == status)
                .sorted(Comparator.comparing(d -> d.getUser().getId()))
                .map(DraftSummaryResponse::from)
                .toList();
    }

    /**
     * 기간 조회 — 업무 일지 목록 화면이 쓴다 (날짜 하나가 아니라 여러 날).
     *
     * <p>정렬은 쿼리가 이미 최근 날짜 먼저로 해 둔다.
     */
    @Transactional(readOnly = true)
    public List<DraftSummaryResponse> listBetween(
            LocalDate from, LocalDate to, Long userId, DraftStatus status) {
        return drafts.findLatestBetween(from, to).stream()
                .filter(d -> userId == null || d.getUser().getId().equals(userId))
                .filter(d -> status == null || d.getStatus() == status)
                .map(DraftSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DraftDetailResponse detail(Long id) {
        return toDetail(find(id));
    }

    /** 본문 저장. 본인 것만, 확정 전에만 (PRD 7). */
    @Transactional
    public DraftDetailResponse updateContent(Long id, Long requesterId, String contentMd) {
        Draft draft = find(id);
        requireOwner(draft, requesterId);
        // 완료(확정) 버튼을 없앴다 (9/10 결정). 잠글 상태가 없으므로 언제든 고칠 수 있다.
        // 자동 생성이 덮는 것은 user_edited 가 막는다 (V6).
        draft.setContentMd(contentMd);
        // 한 번이라도 저장했으면 사람이 쓴 일지다. 스케줄러가 덮지 않는다 (V6).
        draft.setUserEdited(true);
        return toDetail(drafts.save(draft));
    }

    /**
     * 확정. 이미 확정된 초안을 다시 확정해도 오류로 보지 않는다 — 화면에서 버튼이 잠기지만
     * 재전송·중복 클릭으로 두 번 올 수 있고, 결과 상태가 같으므로 그대로 돌려준다.
     */
    @Transactional
    public DraftDetailResponse confirm(Long id, Long requesterId) {
        Draft draft = find(id);
        requireOwner(draft, requesterId);
        if (draft.getStatus() != DraftStatus.CONFIRMED) {
            draft.setStatus(DraftStatus.CONFIRMED);
            draft.setConfirmedAt(OffsetDateTime.now());
            draft = drafts.save(draft);
        }
        return toDetail(draft);
    }

    private Draft find(Long id) {
        return drafts.findById(id)
                .orElseThrow(() -> ApiException.notFound("DRAFT_NOT_FOUND", "초안을 찾을 수 없습니다."));
    }

    private void requireOwner(Draft draft, Long requesterId) {
        if (!draft.getUser().getId().equals(requesterId)) {
            throw ApiException.forbidden("NOT_DRAFT_OWNER", "본인의 초안만 수정할 수 있습니다.");
        }
    }

    private DraftDetailResponse toDetail(Draft draft) {
        List<ActivityResponse> sourceActivities =
                activities.findAllById(Arrays.asList(draft.getSourceActivityIds())).stream()
                        .sorted(Comparator.comparing(Activity::getOccurredAt))
                        .map(ActivityResponse::from)
                        .toList();
        List<SessionResponse> sourceSessions =
                sessions.findAllById(Arrays.asList(draft.getSourceSessionIds())).stream()
                        .sorted(Comparator.comparing(VscodeSession::getReportedAt))
                        .map(SessionResponse::from)
                        .toList();
        return DraftDetailResponse.of(draft, sourceActivities, sourceSessions);
    }
}
