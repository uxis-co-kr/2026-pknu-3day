package com.worklog.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worklog.activity.Activity;
import com.worklog.activity.ActivityRepository;
import com.worklog.activity.ActivityType;
import com.worklog.auth.User;
import com.worklog.auth.UserRepository;
import com.worklog.github.Repo;
import com.worklog.vscode.VscodeSessionRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DraftGeneratorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 10);
    private static final Long USER_ID = 3L;

    private ActivityRepository activityRepository;
    private VscodeSessionRepository sessionRepository;
    private DraftRepository draftRepository;
    private DraftGenerator generator;

    @BeforeEach
    void setUp() {
        activityRepository = mock(ActivityRepository.class);
        sessionRepository = mock(VscodeSessionRepository.class);
        draftRepository = mock(DraftRepository.class);
        UserRepository userRepository = mock(UserRepository.class);

        User user = new User();
        user.setId(USER_ID);
        user.setLogin("taeil");
        user.setName("배태일");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(draftRepository.save(any(Draft.class))).thenAnswer(i -> i.getArgument(0));
        when(sessionRepository.findByUserIdAndWorkDate(anyLong(), any())).thenReturn(List.of());

        generator = new DraftGenerator(
                activityRepository, sessionRepository, draftRepository, userRepository);
    }

    private static Activity commit(Long id, String summary) {
        Repo repo = new Repo();
        repo.setFullName("uxis-co-kr/2026-pknu-3day");

        Activity a = new Activity();
        a.setId(id);
        a.setRepo(repo);
        a.setType(ActivityType.COMMIT);
        a.setExternalId("sha" + id);
        a.setSha("sha" + id);
        a.setTitle("feat: 작업 " + id);
        a.setSummary(summary);
        a.setOccurredAt(OffsetDateTime.now());
        return a;
    }

    private void givenActivities(Activity... activities) {
        when(activityRepository.findForUserBetween(eq(USER_ID), any(), any()))
                .thenReturn(List.of(activities));
    }

    @Test
    @DisplayName("활동이 있으면 초안을 만들고 근거 id 를 채운다")
    void generatesDraft() {
        givenActivities(commit(101L, "출석 API 추가"), commit(102L, "검증 보강"));

        Draft draft = generator.generate(USER_ID, DAY).orElseThrow();

        assertThat(draft.getWorkDate()).isEqualTo(DAY);
        assertThat(draft.getStatus()).isEqualTo(DraftStatus.DRAFT);
        assertThat(draft.getContentMd()).contains("# 2026-09-10 업무 일지 — 배태일");
        assertThat(draft.getSourceActivityIds()).containsExactly(101L, 102L);
        assertThat(draft.getSourceSessionIds()).isEmpty();
    }

    @Test
    @DisplayName("활동도 세션도 없으면 초안을 만들지 않는다 — 컨트롤러가 204 로 답한다")
    void skipsWhenNothingHappened() {
        givenActivities();

        assertThat(generator.generate(USER_ID, DAY)).isEmpty();
        verify(draftRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 초안이 있으면 덮어쓰지 않고 버전을 올린다")
    void incrementsVersion() {
        givenActivities(commit(101L, "요약"));
        when(draftRepository.findMaxVersion(USER_ID, DAY)).thenReturn(1);

        assertThat(generator.generate(USER_ID, DAY).orElseThrow().getVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("첫 초안은 버전 1")
    void firstVersionIsOne() {
        givenActivities(commit(101L, "요약"));
        when(draftRepository.findMaxVersion(USER_ID, DAY)).thenReturn(0);

        assertThat(generator.generate(USER_ID, DAY).orElseThrow().getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("확정된 초안이 있는 사용자는 자동 생성에서 건너뛴다")
    void skipsConfirmedInScheduledRun() {
        when(activityRepository.findUserIdsWithActivityBetween(any(), any()))
                .thenReturn(List.of(USER_ID));
        when(draftRepository.existsByUserIdAndWorkDateAndStatus(USER_ID, DAY, DraftStatus.CONFIRMED))
                .thenReturn(true);

        assertThat(generator.generateForAll(DAY)).isZero();
        verify(draftRepository, never()).save(any());
    }

    @Test
    @DisplayName("확정본이 없으면 자동 생성 대상이다")
    void generatesForUnconfirmedUsers() {
        when(activityRepository.findUserIdsWithActivityBetween(any(), any()))
                .thenReturn(List.of(USER_ID));
        when(draftRepository.existsByUserIdAndWorkDateAndStatus(USER_ID, DAY, DraftStatus.CONFIRMED))
                .thenReturn(false);
        givenActivities(commit(101L, "요약"));

        assertThat(generator.generateForAll(DAY)).isEqualTo(1);
        verify(draftRepository).save(any(Draft.class));
    }

    @Test
    @DisplayName("이름이 없으면 GitHub login 을 쓴다")
    void fallsBackToLogin() {
        UserRepository userRepository = mock(UserRepository.class);
        User noName = new User();
        noName.setId(USER_ID);
        noName.setLogin("taeil");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(noName));
        when(draftRepository.save(any(Draft.class))).thenAnswer(i -> i.getArgument(0));
        givenActivities(commit(101L, "요약"));

        DraftGenerator g = new DraftGenerator(
                activityRepository, sessionRepository, draftRepository, userRepository);

        assertThat(g.generate(USER_ID, DAY).orElseThrow().getContentMd())
                .contains("업무 일지 — taeil");
    }
}
