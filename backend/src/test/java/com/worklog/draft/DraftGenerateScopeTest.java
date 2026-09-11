package com.worklog.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worklog.auth.AuthMethod;
import com.worklog.auth.AuthenticatedUser;
import com.worklog.auth.UserRole;
import com.worklog.config.ApiException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * 일지 <b>생성</b>도 조회와 같은 규칙을 따라야 한다 (BACKLOG2 §2-2).
 *
 * <p>조회는 {@code DataScope} 로 막아 두었는데 생성은 요청 본문의 {@code userId} 를 그대로
 * 믿었다. 그래서 일반 회원이 남의 이름으로 일지를 만들고, <b>그 사람 활동이 담긴 본문까지
 * 응답으로 받아 볼 수 있었다</b> (9/11 점검에서 실제로 확인).
 */
class DraftGenerateScopeTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 11);
    private static final AuthenticatedUser MEMBER =
            new AuthenticatedUser(6L, "me", AuthMethod.JWT, UserRole.MEMBER);
    private static final AuthenticatedUser ADMIN =
            new AuthenticatedUser(5L, "admin", AuthMethod.JWT, UserRole.ADMIN);

    private final DraftGenerator generator = mock(DraftGenerator.class);
    private final PeriodDraftGenerator periodGenerator = mock(PeriodDraftGenerator.class);
    private final DraftGenerateController controller =
            new DraftGenerateController(generator, periodGenerator);

    @Test
    @DisplayName("일반 회원이 남의 id 로 하루치를 만들려 하면 403")
    void memberCannotGenerateForOthers() {
        assertThatThrownBy(() ->
                controller.generate(MEMBER, new DraftGenerateController.GenerateRequest(DAY, 7L)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(generator, never()).generate(any(), any());
    }

    @Test
    @DisplayName("주간·저장소별도 같다 — 남의 id 는 403")
    void memberCannotGeneratePeriodForOthers() {
        var request = new DraftGenerateController.PeriodRequest(DAY, null, 7L, 3L, true);

        assertThatThrownBy(() -> controller.generateWeekly(MEMBER, request))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> controller.generateRepo(MEMBER, request))
                .isInstanceOf(ApiException.class);
        verify(periodGenerator, never()).weekly(any(), any());
        verify(periodGenerator, never()).byRepo(any(), any(), any(), anyBooleanValue());
    }

    @Test
    @DisplayName("자기 것은 만든다 — id 를 생략하면 본인")
    void generatesOwn() {
        when(generator.generate(eq(6L), eq(DAY))).thenReturn(Optional.empty());

        controller.generate(MEMBER, new DraftGenerateController.GenerateRequest(DAY, null));

        verify(generator).generate(6L, DAY);
    }

    /** 관리자 콘솔은 팀원 일지를 다룬다. 그 길까지 막으면 콘솔이 못 돈다. */
    @Test
    @DisplayName("관리자는 남의 id 로도 만든다")
    void adminGeneratesForOthers() {
        when(generator.generate(eq(7L), eq(DAY))).thenReturn(Optional.empty());

        controller.generate(ADMIN, new DraftGenerateController.GenerateRequest(DAY, 7L));

        verify(generator).generate(7L, DAY);
    }

    private static boolean anyBooleanValue() {
        return org.mockito.ArgumentMatchers.anyBoolean();
    }
}
