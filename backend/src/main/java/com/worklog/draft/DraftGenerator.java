package com.worklog.draft;

import com.worklog.activity.Activity;
import com.worklog.activity.ActivityRepository;
import com.worklog.auth.User;
import com.worklog.auth.UserRepository;
import com.worklog.config.ApiException;
import com.worklog.config.KstDates;
import com.worklog.vscode.VscodeSession;
import com.worklog.vscode.VscodeSessionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 하루치 활동을 묶어 업무 일지 초안을 만든다 (PRD F3a — 담당자 2 소유).
 *
 * <p>조회·수정·확정 API 는 담당자 1의 {@code DraftController} 다. 이 클래스는 생성만 한다.
 */
@Service
public class DraftGenerator {

    private static final Logger log = LoggerFactory.getLogger(DraftGenerator.class);

    private final ActivityRepository activityRepository;
    private final VscodeSessionRepository sessionRepository;
    private final DraftRepository draftRepository;
    private final UserRepository userRepository;

    public DraftGenerator(
            ActivityRepository activityRepository,
            VscodeSessionRepository sessionRepository,
            DraftRepository draftRepository,
            UserRepository userRepository) {
        this.activityRepository = activityRepository;
        this.sessionRepository = sessionRepository;
        this.draftRepository = draftRepository;
        this.userRepository = userRepository;
    }

    /**
     * 초안 1건 생성.
     *
     * @return 활동도 세션도 없으면 {@link Optional#empty()} — 컨트롤러가 204 로 답한다 (PRD F3)
     */
    @Transactional
    public Optional<Draft> generate(Long userId, LocalDate workDate) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));

        List<Activity> activities = activityRepository.findForUserBetween(
                userId, KstDates.startOf(workDate), KstDates.endOf(workDate));
        List<VscodeSession> sessions = sessionRepository.findByUserIdAndWorkDate(userId, workDate);

        if (activities.isEmpty() && sessions.isEmpty()) {
            log.info("{} 의 {} 활동이 없어 초안을 만들지 않는다.", user.getLogin(), workDate);
            return Optional.empty();
        }

        Draft draft = new Draft();
        draft.setUser(user);
        draft.setWorkDate(workDate);
        // 덮어쓰지 않고 버전을 올린다. 확정본은 그대로 남는다 (PRD F3).
        draft.setVersion(draftRepository.findMaxVersion(userId, workDate) + 1);
        draft.setStatus(DraftStatus.DRAFT);
        draft.setContentMd(DraftTemplate.render(workDate, displayName(user), activities, sessions));
        draft.setSourceActivityIds(activities.stream().map(Activity::getId).toArray(Long[]::new));
        draft.setSourceSessionIds(sessions.stream().map(VscodeSession::getId).toArray(Long[]::new));

        Draft saved = draftRepository.save(draft);
        log.info(
                "{} 의 {} 초안 v{} 생성 — 활동 {}건, 세션 {}건",
                user.getLogin(),
                workDate,
                saved.getVersion(),
                activities.size(),
                sessions.size());
        return Optional.of(saved);
    }

    /**
     * 그날 활동이 있는 전 사용자에 대해 생성한다 (18:00 스케줄러).
     *
     * <p>이미 확정한 초안이 있으면 건너뛴다 — 사용자가 확정한 내용을 자동 생성이 덮지 않는다.
     *
     * @return 만든 초안 수
     */
    @Transactional
    public int generateForAll(LocalDate workDate) {
        List<Long> userIds = activityRepository.findUserIdsWithActivityBetween(
                KstDates.startOf(workDate), KstDates.endOf(workDate));
        int created = 0;
        for (Long userId : userIds) {
            if (draftRepository.existsByUserIdAndWorkDateAndStatus(
                    userId, workDate, DraftStatus.CONFIRMED)) {
                log.info("사용자 {} 의 {} 초안은 이미 확정돼 있어 건너뛴다.", userId, workDate);
                continue;
            }
            if (generate(userId, workDate).isPresent()) {
                created++;
            }
        }
        return created;
    }

    private static String displayName(User user) {
        return user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getLogin();
    }
}
