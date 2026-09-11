package com.worklog.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.worklog.activity.Activity;
import com.worklog.activity.ActivityRepository;
import com.worklog.activity.ActivityType;
import com.worklog.auth.User;
import com.worklog.auth.UserRepository;
import com.worklog.config.ApiException;
import com.worklog.github.Repo;
import com.worklog.github.RepoRepository;
import com.worklog.llm.LlmProvider;
import com.worklog.llm.LlmProviderResolver;
import com.worklog.llm.LlmRequest;
import com.worklog.llm.LlmSettingService;
import com.worklog.llm.PromptLoader;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 주간·저장소별 업무일지 생성 (V15). */
class PeriodDraftGeneratorTest {

    private static final long USER_ID = 1L;
    private static final long REPO_ID = 7L;
    /** 2026-09-07 은 월요일. 주는 월~일(9/7~9/13)로 맞춰진다. */
    private static final LocalDate FROM = LocalDate.of(2026, 9, 7);
    private static final LocalDate TO = LocalDate.of(2026, 9, 13);

    private DraftRepository drafts;
    private ActivityRepository activities;
    private RepoRepository repos;
    private LlmProvider provider;
    private PeriodDraftGenerator generator;
    private User user;
    private Repo repo;

    @BeforeEach
    void setUp() {
        drafts = mock(DraftRepository.class);
        activities = mock(ActivityRepository.class);
        repos = mock(RepoRepository.class);
        UserRepository users = mock(UserRepository.class);
        LlmProviderResolver resolver = mock(LlmProviderResolver.class);
        LlmSettingService llmSettings = mock(LlmSettingService.class);
        provider = mock(LlmProvider.class);

        user = new User();
        user.setId(USER_ID);
        user.setName("조웅식");
        repo = new Repo();
        repo.setId(REPO_ID);
        repo.setFullName("uxis/worklog");

        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        when(repos.findById(REPO_ID)).thenReturn(Optional.of(repo));
        when(resolver.resolve(any())).thenReturn(provider);
        when(llmSettings.providerOf(anyLong())).thenReturn("gemma4");
        when(provider.complete(any())).thenReturn("## 이번 기간에 한 일\n- 로그인을 고쳤다 (9/8~9/9)");
        when(drafts.nextVersion(anyLong(), any(), any(), any())).thenReturn(1);
        when(drafts.save(any(Draft.class))).thenAnswer(i -> i.getArgument(0));

        generator = new PeriodDraftGenerator(
                drafts, activities, users, repos, resolver, llmSettings, new PromptLoader());
    }

    private static Draft daily(LocalDate date, String body) {
        Draft d = new Draft();
        User u = new User();
        u.setId(USER_ID);
        u.setName("조웅식");
        d.setUser(u);
        d.setKind(DraftKind.DAILY);
        d.setWorkDate(date);
        d.setVersion(1);
        d.setContentMd("# %s 업무 일지 — 조웅식\n\n## 완료한 작업\n- %s\n\n## 메모\n(직접 작성)\n".formatted(date, body));
        return d;
    }

    private static Activity activity(ActivityType type, String summary, LocalDate day, Repo r, User u) {
        Activity a = new Activity();
        a.setId((long) summary.hashCode());
        a.setRepo(r);
        a.setUser(u);
        a.setType(type);
        a.setSha("abcdef1234567");
        a.setExternalId("12");
        a.setSummary(summary);
        a.setTitle(summary);
        a.setOccurredAt(OffsetDateTime.parse(day + "T10:00:00+09:00"));
        return a;
    }

    @Test
    @DisplayName("주간 — 그 기간의 하루치 일지를 재료로 삼고, 기간·종류를 담아 저장한다")
    void weeklyUsesDailyDrafts() {
        when(drafts.findLatestBetween(FROM, TO)).thenReturn(List.of(
                daily(LocalDate.of(2026, 9, 9), "요약 파이프라인"),
                daily(LocalDate.of(2026, 9, 8), "로그인 고침")));

        Draft made = generator.weekly(USER_ID, FROM);

        assertThat(made.getKind()).isEqualTo(DraftKind.WEEKLY);
        assertThat(made.getPeriodStart()).isEqualTo(FROM);
        assertThat(made.getPeriodEnd()).isEqualTo(TO);
        assertThat(made.getWorkDate()).isEqualTo(FROM); // 기존 날짜 조회가 그대로 돌게
        assertThat(made.getRepo()).isNull();
        assertThat(made.getContentMd()).startsWith("# 2026-09-07 ~ 09-13 주간 업무일지 — 조웅식");

        ArgumentCaptor<LlmRequest> req = ArgumentCaptor.forClass(LlmRequest.class);
        org.mockito.Mockito.verify(provider).complete(req.capture());
        // 날짜 순서대로, 제목 줄과 "직접 작성" 메모는 빼고 넣는다
        String sent = req.getValue().user();
        assertThat(sent.indexOf("2026-09-08")).isLessThan(sent.indexOf("2026-09-09"));
        assertThat(sent).contains("- 로그인 고침").doesNotContain("직접 작성");
    }

    @Test
    @DisplayName("주간 — 하루치가 없으면 활동에서 바로 만든다")
    void weeklyFallsBackToActivities() {
        when(drafts.findLatestBetween(FROM, TO)).thenReturn(List.of());
        when(activities.findForUserBetween(anyLong(), any(), any())).thenReturn(List.of(
                activity(ActivityType.COMMIT, "로그인 고침", LocalDate.of(2026, 9, 8), repo, user)));

        Draft made = generator.weekly(USER_ID, FROM);

        assertThat(made.getKind()).isEqualTo(DraftKind.WEEKLY);
        ArgumentCaptor<LlmRequest> req = ArgumentCaptor.forClass(LlmRequest.class);
        org.mockito.Mockito.verify(provider).complete(req.capture());
        assertThat(req.getValue().user()).contains("로그인 고침").contains("커밋");
    }

    @Test
    @DisplayName("주간 — 하루치도 활동도 없으면 만들지 않고 이유를 말한다")
    void weeklyWithoutMaterial() {
        when(drafts.findLatestBetween(FROM, TO)).thenReturn(List.of());
        when(activities.findForUserBetween(anyLong(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> generator.weekly(USER_ID, FROM))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("쓸 재료가 없습니다");
    }

    @Test
    @DisplayName("저장소별 — 그 저장소의 활동을 재료로 삼고 저장소를 담는다")
    void byRepoUsesRepoActivities() {
        when(activities.findBetweenForRepoDraft(any(), any(), any(), any())).thenReturn(List.of(
                activity(ActivityType.COMMIT, "수집기 고침", LocalDate.of(2026, 9, 8), repo, user),
                activity(ActivityType.PR_MERGED, "PR 머지", LocalDate.of(2026, 9, 9), repo, user)));
        when(provider.complete(any())).thenReturn("## 이 저장소에서 한 일\n- 수집기를 고쳤다 (abcdef1)");

        Draft made = generator.byRepo(USER_ID, REPO_ID, FROM, false);

        assertThat(made.getKind()).isEqualTo(DraftKind.REPO);
        assertThat(made.getRepo()).isSameAs(repo);
        assertThat(made.getPeriodStart()).isEqualTo(FROM);
        assertThat(made.getContentMd()).startsWith("# uxis/worklog — 2026-09-07 ~ 09-13 저장소별 업무일지");
        assertThat(made.getSourceActivityIds()).hasSize(2);

        ArgumentCaptor<LlmRequest> req = ArgumentCaptor.forClass(LlmRequest.class);
        org.mockito.Mockito.verify(provider).complete(req.capture());
        assertThat(req.getValue().user()).contains("uxis/worklog").contains("커밋 1건").contains("머지 1건");
    }

    @Test
    @DisplayName("저장소별 — 그 기간에 기록이 없으면 만들지 않는다")
    void byRepoWithoutMaterial() {
        when(activities.findBetweenForRepoDraft(any(), any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> generator.byRepo(USER_ID, REPO_ID, FROM, false))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("기록이 없습니다");
    }

    @Test
    @DisplayName("어느 날을 골라도 그 주 월~일로 맞춘다 — 같은 주면 같은 일지가 된다")
    void normalizesToWeek() {
        when(drafts.findLatestBetween(any(), any()))
                .thenReturn(List.of(daily(LocalDate.of(2026, 9, 9), "로그인 고침")));

        // 수요일(9/9)을 골라도 월요일(9/7)로
        Draft made = generator.weekly(USER_ID, LocalDate.of(2026, 9, 9));
        assertThat(made.getPeriodStart()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(made.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 9, 13));
        assertThat(made.getWorkDate()).isEqualTo(LocalDate.of(2026, 9, 7));

        // 일요일(9/13)을 골라도 같은 주
        assertThat(generator.weekly(USER_ID, LocalDate.of(2026, 9, 13)).getPeriodStart())
                .isEqualTo(LocalDate.of(2026, 9, 7));

        // 제목과 각주에 기간이 들어간다
        assertThat(made.getContentMd())
                .startsWith("# 2026-09-07 ~ 09-13 주간 업무일지")
                .contains("_기간: 2026-09-07(월) ~ 2026-09-13(일)_");
    }


    @Test
    @DisplayName("LLM 이 실패하면 초안을 만들지 않는다 — 묶지 못한 글은 값어치가 없다")
    void llmFailureIsNotSaved() {
        when(drafts.findLatestBetween(FROM, TO)).thenReturn(List.of(daily(LocalDate.of(2026, 9, 8), "x")));
        when(provider.complete(any())).thenThrow(new IllegalStateException("모델이 죽었다"));

        assertThatThrownBy(() -> generator.weekly(USER_ID, FROM))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("AI 요약에 실패");
        org.mockito.Mockito.verify(drafts, org.mockito.Mockito.never()).save(any());
    }
}
