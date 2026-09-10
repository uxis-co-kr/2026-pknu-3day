package com.worklog.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worklog.activity.Activity;
import com.worklog.activity.ActivityRepository;
import com.worklog.activity.ActivityType;
import com.worklog.activity.SummaryStatus;
import com.worklog.auth.User;
import com.worklog.github.Repo;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class SummaryServiceTest {

    private ActivityRepository activityRepository;
    private LlmProvider provider;
    private LlmSettingService settingService;
    private LlmProviderResolver resolverMock;
    private SummaryService service;

    @BeforeEach
    void setUp() {
        activityRepository = mock(ActivityRepository.class);
        provider = mock(LlmProvider.class);
        when(provider.id()).thenReturn("stub");

        resolverMock = mock(LlmProviderResolver.class);
        when(resolverMock.resolve()).thenReturn(provider);

        settingService = mock(LlmSettingService.class);
        service = new SummaryService(
                activityRepository, resolverMock, new PromptLoader(), settingService);
    }

    private static Activity commit(String message, String diff) {
        Repo repo = new Repo();
        repo.setFullName("uxis-co-kr/2026-pknu-3day");

        Activity activity = new Activity();
        activity.setId(1L);
        activity.setRepo(repo);
        activity.setType(ActivityType.COMMIT);
        activity.setExternalId("abc1234");
        activity.setTitle(message);
        activity.setMessage(message);
        activity.setRawDiff(diff);
        activity.setFilesChanged(2);
        activity.setOccurredAt(OffsetDateTime.now());
        return activity;
    }

    private void givenTargets(Activity... activities) {
        when(activityRepository.findSummaryTargets(anyInt(), any(Pageable.class)))
                .thenReturn(List.of(activities));
    }

    @Test
    @DisplayName("요약에 성공하면 summary 를 채우고 DONE 으로 바꾼다")
    void marksDoneOnSuccess() {
        Activity activity = commit("feat: 출석 API 추가", "--- a.ts\n+1");
        givenTargets(activity);
        when(provider.complete(any())).thenReturn("  출석 API 를 추가했다.  ");

        assertThat(service.runOnce()).isEqualTo(1);
        assertThat(activity.getSummary()).isEqualTo("출석 API 를 추가했다.");
        assertThat(activity.getSummaryStatus()).isEqualTo(SummaryStatus.DONE);
        assertThat(activity.getSummaryRetries()).isZero();
    }

    @Test
    @DisplayName("실패하면 재시도 횟수를 올리고 FAILED 로 둔다 — 예외를 밖으로 던지지 않는다")
    void countsRetryOnFailure() {
        Activity activity = commit("fix: 버그", "--- a.ts");
        givenTargets(activity);
        when(provider.complete(any())).thenThrow(new RuntimeException("LLM 연결 실패"));

        assertThat(service.runOnce()).isZero();
        assertThat(activity.getSummary()).isNull();
        assertThat(activity.getSummaryStatus()).isEqualTo(SummaryStatus.FAILED);
        assertThat(activity.getSummaryRetries()).isEqualTo(1);
    }

    @Test
    @DisplayName("빈 응답도 실패로 본다")
    void treatsBlankResponseAsFailure() {
        Activity activity = commit("fix: 버그", "--- a.ts");
        givenTargets(activity);
        when(provider.complete(any())).thenReturn("   ");

        assertThat(service.runOnce()).isZero();
        assertThat(activity.getSummaryStatus()).isEqualTo(SummaryStatus.FAILED);
    }

    @Test
    @DisplayName("이미 요약이 있으면 LLM 을 부르지 않고 DONE 으로만 바꾼다")
    void skipsAlreadySummarized() {
        Activity activity = commit("feat: 무언가", "--- a.ts");
        activity.setSummary("이미 있는 요약");
        givenTargets(activity);

        assertThat(service.runOnce()).isEqualTo(1);
        assertThat(activity.getSummaryStatus()).isEqualTo(SummaryStatus.DONE);
        verify(provider, never()).complete(any());
    }

    @Test
    @DisplayName("재시도 상한 3회를 조회 조건으로 넘긴다 — 4회째부터는 아예 집히지 않는다")
    void passesRetryLimitToQuery() {
        when(activityRepository.findSummaryTargets(anyInt(), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(service.runOnce()).isZero();
        verify(activityRepository).findSummaryTargets(eq(3), any(Pageable.class));
        assertThat(SummaryService.MAX_RETRIES).isEqualTo(3);
    }

    @Test
    @DisplayName("활동 소유자가 고른 프로바이더를 쓴다 (PRD F9 — 사용자 설정 우선)")
    void usesPerUserProvider() {
        LlmProvider userChoice = mock(LlmProvider.class);
        when(userChoice.id()).thenReturn("qwen3");
        when(userChoice.complete(any())).thenReturn("사용자가 고른 모델의 요약");
        when(settingService.providerOf(3L)).thenReturn("qwen3");
        when(resolverMock.resolve("qwen3")).thenReturn(userChoice);

        Activity activity = commit("feat: 무언가", "--- a.ts");
        activity.setUser(user(3L));
        givenTargets(activity);

        assertThat(service.runOnce()).isEqualTo(1);
        assertThat(activity.getSummary()).isEqualTo("사용자가 고른 모델의 요약");
        verify(provider, never()).complete(any());
    }

    @Test
    @DisplayName("소유자에게 설정이 없으면 전역 설정으로 떨어진다")
    void fallsBackToGlobalWhenUserHasNoSetting() {
        when(settingService.providerOf(3L)).thenReturn(null);
        when(resolverMock.resolve((String) null)).thenReturn(provider);
        when(provider.complete(any())).thenReturn("전역 설정 요약");

        Activity activity = commit("feat: 무언가", "--- a.ts");
        activity.setUser(user(3L));
        givenTargets(activity);

        assertThat(service.runOnce()).isEqualTo(1);
        assertThat(activity.getSummary()).isEqualTo("전역 설정 요약");
    }

    @Test
    @DisplayName("소유자가 없는(미가입 계정) 활동은 전역 설정을 쓴다 — 설정을 조회하지도 않는다")
    void unmappedActivityUsesGlobal() {
        when(provider.complete(any())).thenReturn("전역 설정 요약");
        Activity activity = commit("feat: 무언가", "--- a.ts");
        givenTargets(activity);

        assertThat(service.runOnce()).isEqualTo(1);
        verify(settingService, never()).providerOf(any());
    }

    @Test
    @DisplayName("같은 사용자의 활동이 여러 건이어도 설정은 한 번만 조회한다")
    void cachesSettingLookupPerBatch() {
        when(settingService.providerOf(3L)).thenReturn(null);
        when(resolverMock.resolve((String) null)).thenReturn(provider);
        when(provider.complete(any())).thenReturn("요약");

        Activity a = commit("feat: 하나", "--- a.ts");
        a.setUser(user(3L));
        Activity b = commit("feat: 둘", "--- b.ts");
        b.setUser(user(3L));
        givenTargets(a, b);

        assertThat(service.runOnce()).isEqualTo(2);
        verify(settingService, org.mockito.Mockito.times(1)).providerOf(3L);
    }

    private static User user(Long id) {
        User u = new User();
        u.setId(id);
        u.setLogin("user" + id);
        return u;
    }

    @Test
    @DisplayName("프롬프트에 리포·메시지·diff 가 들어간다")
    void buildsPrompt() {
        Activity activity = commit("feat: 출석 API 추가", "--- src/api/attendance.ts\n+code");

        LlmRequest request = service.buildRequest(activity);

        assertThat(request.system()).contains("업무 일지 작성 보조자");
        assertThat(request.user())
                .contains("리포: uxis-co-kr/2026-pknu-3day")
                .contains("커밋 메시지: feat: 출석 API 추가")
                .contains("변경 파일: src/api/attendance.ts")
                .contains("+code");
        assertThat(request.var("fileCount", "?")).isEqualTo("2");
    }

    @Test
    @DisplayName("PR 은 diff 가 없어도 제목과 본문으로 프롬프트를 만든다")
    void buildsPromptForPullRequest() {
        Activity pr = commit(null, null);
        pr.setType(ActivityType.PR_MERGED);
        pr.setTitle("PR #2 머지: API 명세 반영");
        pr.setMessage("확정 화면이 요구하는 필드를 반영했다.");

        LlmRequest request = service.buildRequest(pr);

        assertThat(request.user())
                .contains("PR #2 머지: API 명세 반영")
                .contains("확정 화면이 요구하는 필드를 반영했다.");
    }
}
