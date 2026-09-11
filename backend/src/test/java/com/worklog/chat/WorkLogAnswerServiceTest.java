package com.worklog.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.worklog.activity.Activity;
import com.worklog.activity.ActivityRepository;
import com.worklog.activity.ActivityType;
import com.worklog.admin.PeopleDirectoryService;
import com.worklog.admin.dto.PeopleDirectoryResponse;
import com.worklog.auth.User;
import com.worklog.auth.UserRepository;
import com.worklog.auth.UserRole;
import com.worklog.config.KstDates;
import com.worklog.draft.Draft;
import com.worklog.draft.DraftRepository;
import com.worklog.draft.DraftStatus;
import com.worklog.github.Repo;
import com.worklog.vscode.VscodeSessionRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkLogAnswerServiceTest {

    private UserRepository users;
    private PeopleDirectoryService people;
    private DraftRepository drafts;
    private ActivityRepository activities;
    private VscodeSessionRepository sessions;
    private WorkLogAnswerService service;
    private User ungsik;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        people = mock(PeopleDirectoryService.class);
        drafts = mock(DraftRepository.class);
        activities = mock(ActivityRepository.class);
        sessions = mock(VscodeSessionRepository.class);
        com.worklog.llm.LlmProviderResolver resolver = mock(com.worklog.llm.LlmProviderResolver.class);
        when(resolver.resolve(any())).thenReturn(new com.worklog.llm.MockLlmProvider());
        com.worklog.llm.LlmSettingService llmSettings = mock(com.worklog.llm.LlmSettingService.class);
        service = new WorkLogAnswerService(
                users, people, drafts, activities, sessions, resolver, llmSettings,
                new com.worklog.llm.PromptLoader(), "http://front/");

        ungsik = new User();
        ungsik.setId(1L);
        ungsik.setLogin("UngsikJo");
        ungsik.setName("ungsikJo");
        ungsik.setEmpSeq(9999L);
        when(users.findAll()).thenReturn(List.of(ungsik));
        when(people.directory()).thenReturn(new PeopleDirectoryResponse(
                true, 29L,
                List.of(new PeopleDirectoryResponse.EmployeeRow(9999L, "조웅식", null),
                        new PeopleDirectoryResponse.EmployeeRow(9998L, "배태일", null)),
                List.of(), List.of()));
        when(drafts.findFirstByUserIdAndWorkDateOrderByVersionDesc(anyLong(), any())).thenReturn(Optional.empty());
        when(activities.findForUserBetween(anyLong(), any(), any())).thenReturn(List.of());
        when(sessions.findByUserIdAndWorkDate(anyLong(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("업무 일지를 묻는 말이 아니면 답하지 않는다")
    void staysQuiet() {
        assertThat(service.answer("점심 뭐 먹지")).isEmpty();
    }

    @Test
    @DisplayName("초안이 있으면 초안을 준다 — 확정본이면 그렇게 표시하고 링크를 붙인다")
    void answersFromDraft() {
        Draft d = new Draft();
        d.setId(9L);
        d.setUser(ungsik);
        d.setWorkDate(KstDates.today());
        d.setVersion(2);
        d.setStatus(DraftStatus.CONFIRMED);
        d.setContentMd("# 2026-09-10 업무 일지 — ungsikJo\n\n## 완료한 작업\n- [r] 로그인 고침\n\n## 메모\n(직접 작성)\n");
        when(drafts.findFirstByUserIdAndWorkDateOrderByVersionDesc(eq(1L), eq(KstDates.today()))).thenReturn(Optional.of(d));

        String answer = service.answer("조웅식의 오늘 업무일지를 요약해서 보내줘").orElseThrow();

        assertThat(answer).startsWith("**조웅식 · " + KstDates.today() + " 업무 일지** _(확정본)_");
        assertThat(answer).contains("- [r] 로그인 고침");
        assertThat(answer).doesNotContain("# 2026-09-10 업무 일지 —").doesNotContain("직접 작성");
        assertThat(answer).contains("http://front/drafts/9");
    }

    @Test
    @DisplayName("초안이 없으면 활동으로 그 자리에서 조립하고, 초안을 만들지는 않는다")
    void answersFromActivitiesWithoutSavingDraft() {
        Repo repo = new Repo();
        repo.setFullName("uxis/worklog");
        Activity a = new Activity();
        a.setType(ActivityType.COMMIT);
        a.setTitle("fix: 로그인");
        a.setSummary("로그인 405 를 고쳤다");
        a.setSha("abcdef1234567890");
        a.setRepo(repo);
        a.setOccurredAt(OffsetDateTime.now());
        when(activities.findForUserBetween(eq(1L), any(), any())).thenReturn(List.of(a));

        String answer = service.answer("조웅식 어제 업무일지").orElseThrow();

        assertThat(answer).contains("초안 미생성").contains("활동 1건");
        assertThat(answer).contains("로그인 405 를 고쳤다");
        org.mockito.Mockito.verify(drafts, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("기간을 물으면 날짜마다 초안 또는 조립한 일지를 모아 주고, 기록 없는 날은 건너뛴다")
    void answersRange() {
        Draft d = new Draft();
        d.setId(11L);
        d.setUser(ungsik);
        d.setWorkDate(LocalDate.of(2026, 9, 8));
        d.setVersion(1);
        d.setStatus(DraftStatus.CONFIRMED);
        d.setContentMd("# 2026-09-08 업무 일지\n\n## 완료한 작업\n- [r] 관리자 콘솔 뼈대\n\n## 메모\n(직접 작성)\n");
        when(drafts.findLatestBetween(LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 10))).thenReturn(List.of(d));

        Repo repo = new Repo();
        repo.setFullName("uxis/worklog");
        Activity a = new Activity();
        a.setType(ActivityType.COMMIT);
        a.setTitle("fix: 로그인");
        a.setSummary("로그인 405 를 고쳤다");
        a.setSha("abcdef1234567890");
        a.setRepo(repo);
        a.setOccurredAt(OffsetDateTime.parse("2026-09-10T10:00:00+09:00"));
        when(activities.findForUserBetween(eq(1L), eq(KstDates.startOf(LocalDate.of(2026, 9, 10))), any()))
                .thenReturn(List.of(a));

        String answer = service.answer("조웅식 9월 8일~9월 10일 업무일지 요약해서 줘").orElseThrow();

        assertThat(answer).startsWith("**조웅식 · 2026-09-08 ~ 2026-09-10 업무 일지** _(3일 중 2일 기록)_");
        assertThat(answer).contains("### 2026-09-08 _(확정본)_").contains("관리자 콘솔 뼈대").contains("http://front/drafts/11");
        assertThat(answer).contains("### 2026-09-10 _(초안 미생성 — 활동 1건)_").contains("로그인 405 를 고쳤다");
        assertThat(answer).doesNotContain("2026-09-09");
        // mock 프로바이더에서는 기간 요약을 얹지 않는다
        assertThat(answer).doesNotContain("기간 요약");
        org.mockito.Mockito.verify(drafts, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("이름을 여럿 대면 문장에 나온 순서대로 한 사람씩 답하고, 같은 사람을 두 이름으로 불러도 한 번만")
    void answersSeveralPeopleInOrder() {
        Draft d = new Draft();
        d.setId(9L);
        d.setUser(ungsik);
        d.setWorkDate(KstDates.today());
        d.setVersion(1);
        d.setStatus(DraftStatus.DRAFT);
        d.setContentMd("# x\n\n## 완료한 작업\n- [r] 로그인 고침\n");
        when(drafts.findFirstByUserIdAndWorkDateOrderByVersionDesc(eq(1L), eq(KstDates.today()))).thenReturn(Optional.of(d));

        String a = service.answer("배태일 조웅식 오늘 업무일지").orElseThrow();
        assertThat(a.indexOf("**배태일** 님은")).isLessThan(a.indexOf("**조웅식 · "));
        assertThat(a).contains("\n\n---\n\n").contains("로그인 고침");

        String b = service.answer("조웅식이랑 배태일 오늘 업무일지").orElseThrow();
        assertThat(b.indexOf("**조웅식 · ")).isLessThan(b.indexOf("**배태일** 님은"));

        // "조웅식" 과 "UngsikJo" 는 같은 사람 — 한 번만
        String c = service.answer("조웅식 UngsikJo 오늘 업무일지").orElseThrow();
        assertThat(c).doesNotContain("---");
        assertThat(c.indexOf("로그인 고침")).isEqualTo(c.lastIndexOf("로그인 고침"));
    }

    @Test
    @DisplayName("기간에 기록이 하나도 없으면 그렇게 말한다")
    void rangeWithoutRecords() {
        when(drafts.findLatestBetween(any(), any())).thenReturn(List.of());
        String answer = service.answer("조웅식 이번 주 업무일지").orElseThrow();
        assertThat(answer).contains("기록된 활동이 없습니다").contains(KstDates.today().toString());
    }

    @Test
    @DisplayName("'조웅식 업무요약' 처럼 느슨한 말도 이름이 있으면 답한다")
    void weakIntentWithNameAnswers() {
        assertThat(service.answer("조웅식 업무요약")).isPresent();
        assertThat(service.answer("조웅식 오늘 뭐함")).isPresent();
    }

    @Test
    @DisplayName("느슨한 말에 이름이 없으면 끼어들지 않는다 — '회의 요약 올립니다'")
    void weakIntentWithoutNameStaysQuiet() {
        assertThat(service.answer("회의 요약 올립니다")).isEmpty();
        assertThat(service.answer("오늘 업무 정리해서 공유해요")).isEmpty();
    }

    @Test
    @DisplayName("활동이 없으면 그렇게 말한다")
    void nothingRecorded() {
        String answer = service.answer("조웅식 오늘 업무일지").orElseThrow();
        assertThat(answer).contains("기록된 활동이 없습니다");
    }

    @Test
    @DisplayName("사원 명단에만 있고 계정이 없으면 그렇게 말한다")
    void employeeWithoutAccount() {
        String answer = service.answer("배태일 오늘 업무일지").orElseThrow();
        assertThat(answer).contains("배태일").contains("계정이 없어");
    }

    @Test
    @DisplayName("이름을 못 찾으면 쓰는 법과 찾을 수 있는 이름을 알려 준다 — 한 사람은 사원 명단 이름 하나로, 관리자는 빼고")
    void unknownPerson() {
        User admin = new User();
        admin.setId(3L);
        admin.setName("관리자");
        admin.setRole(UserRole.ADMIN);
        when(users.findAll()).thenReturn(List.of(ungsik, admin));

        String answer = service.answer("오늘 업무일지 요약해줘").orElseThrow();

        assertThat(answer).contains("누구의 일지인지").contains("조웅식");
        assertThat(answer).doesNotContain("ungsikJo").doesNotContain("관리자");
    }

    @Test
    @DisplayName("GitHub 로그인으로 물어도 찾고, 답은 사원 명단 이름으로 한다")
    void githubLoginAlsoWorks() {
        String answer = service.answer("UngsikJo 오늘 업무일지").orElseThrow();
        assertThat(answer).contains("기록된 활동이 없습니다");
    }
}
