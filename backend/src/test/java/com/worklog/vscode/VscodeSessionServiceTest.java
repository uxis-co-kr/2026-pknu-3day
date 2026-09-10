package com.worklog.vscode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.worklog.auth.User;
import com.worklog.auth.UserRepository;
import com.worklog.github.Repo;
import com.worklog.github.RepoRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VscodeSessionServiceTest {

    private static final Long USER_ID = 3L;
    private static final String REMOTE = "https://github.com/withly/punchcheck.git";
    private static final String BRANCH = "feature/attendance";
    private static final LocalDate WORK_DATE = LocalDate.of(2026, 9, 9);

    @Mock private VscodeSessionRepository sessions;
    @Mock private UserRepository users;
    @Mock private RepoRepository repos;

    private VscodeSessionService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new VscodeSessionService(sessions, users, repos);
        user = new User();
        user.setId(USER_ID);
        user.setLogin("taeil");
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        when(sessions.save(any(VscodeSession.class))).thenAnswer(i -> i.getArgument(0));
    }

    private SessionRequest request(String planNote) {
        return new SessionRequest(
                REMOTE,
                BRANCH,
                WORK_DATE,
                List.of(new UncommittedFile("src/api/attendance.ts", 40, 3, "@@ ...")),
                List.of(new TodoItem("src/api/attendance.ts", 42, "중복 출석 검증")),
                planNote,
                List.of(),
                List.of(new AiSessionSummary(
                        "sess-1",
                        "2026-09-09T10:00:00+09:00",
                        "2026-09-09T11:00:00+09:00",
                        2,
                        List.of("출석 중복 검증 로직 봐 줘", "테스트도 붙여 줘"))),
                OffsetDateTime.parse("2026-09-09T12:00:00+09:00"));
    }

    @Test
    @DisplayName("확장이 보낸 AI 대화를 그대로 저장한다")
    void keepsAiSessions() {
        VscodeSession saved = service.upsert(USER_ID, request("오늘 계획"));

        assertThat(saved.getAiSessions()).hasSize(1);
        assertThat(saved.getAiSessions().get(0).prompts())
                .containsExactly("출석 중복 검증 로직 봐 줘", "테스트도 붙여 줘");
    }

    @Test
    @DisplayName("처음 보고하면 새 세션을 만들고 UPSERT 키를 그대로 채운다")
    void createsNewSession() {
        when(sessions.findByUserIdAndRemoteUrlAndBranchAndWorkDate(USER_ID, REMOTE, BRANCH, WORK_DATE))
                .thenReturn(Optional.empty());

        VscodeSession saved = service.upsert(USER_ID, request("오후에 출석 중복 검증 로직 마무리"));

        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getRemoteUrl()).isEqualTo(REMOTE);
        assertThat(saved.getBranch()).isEqualTo(BRANCH);
        assertThat(saved.getWorkDate()).isEqualTo(WORK_DATE);
        assertThat(saved.getUncommittedFiles()).hasSize(1);
        assertThat(saved.getTodos()).hasSize(1);
        assertThat(saved.getReportedAt()).isNotNull();
    }

    @Test
    @DisplayName("같은 (user, remoteUrl, branch, 날짜) 는 새 행을 만들지 않고 기존 행을 갱신한다")
    void updatesExistingSession() {
        VscodeSession existing = new VscodeSession();
        existing.setId(21L);
        existing.setUser(user);
        existing.setRemoteUrl(REMOTE);
        existing.setBranch(BRANCH);
        existing.setWorkDate(WORK_DATE);
        existing.setUncommittedFiles(List.of());
        existing.setReportedAt(OffsetDateTime.parse("2026-09-09T13:00:00+09:00"));
        when(sessions.findByUserIdAndRemoteUrlAndBranchAndWorkDate(USER_ID, REMOTE, BRANCH, WORK_DATE))
                .thenReturn(Optional.of(existing));

        VscodeSession saved = service.upsert(USER_ID, request(null));

        assertThat(saved).isSameAs(existing);
        assertThat(saved.getId()).isEqualTo(21L);
        assertThat(saved.getUncommittedFiles()).hasSize(1);
        assertThat(saved.getReportedAt()).isAfter(OffsetDateTime.parse("2026-09-09T13:00:00+09:00"));
    }

    @Test
    @DisplayName("계획 메모를 안 보내오면 서버에 남은 메모를 지우지 않는다")
    void keepsPlanNoteWhenAbsent() {
        VscodeSession existing = new VscodeSession();
        existing.setUser(user);
        existing.setPlanNote("오후에 출석 중복 검증 로직 마무리");
        when(sessions.findByUserIdAndRemoteUrlAndBranchAndWorkDate(USER_ID, REMOTE, BRANCH, WORK_DATE))
                .thenReturn(Optional.of(existing));

        assertThat(service.upsert(USER_ID, request(null)).getPlanNote())
                .isEqualTo("오후에 출석 중복 검증 로직 마무리");
        assertThat(service.upsert(USER_ID, request("   ")).getPlanNote())
                .isEqualTo("오후에 출석 중복 검증 로직 마무리");
    }

    @Test
    @DisplayName("등록된 리포면 연결하고, 등록 안 된 리포면 repo 없이 저장한다")
    void matchesRegisteredRepoOnly() {
        when(sessions.findByUserIdAndRemoteUrlAndBranchAndWorkDate(any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        Repo repo = new Repo();
        repo.setId(1L);
        repo.setFullName("withly/punchcheck");
        when(repos.findByFullName("withly/punchcheck")).thenReturn(Optional.of(repo));
        assertThat(service.upsert(USER_ID, request(null)).getRepo()).isSameAs(repo);

        when(repos.findByFullName("withly/unknown")).thenReturn(Optional.empty());
        SessionRequest unknown = new SessionRequest(
                "https://github.com/withly/unknown.git", BRANCH, WORK_DATE,
                List.of(), List.of(), null, List.of(), List.of(), null);
        assertThat(service.upsert(USER_ID, unknown).getRepo()).isNull();
    }
}
