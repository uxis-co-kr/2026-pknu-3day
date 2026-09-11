package com.worklog.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worklog.auth.AuthMethod;
import com.worklog.auth.AuthenticatedUser;
import com.worklog.auth.User;
import com.worklog.auth.UserRole;
import com.worklog.config.ApiException;
import com.worklog.notify.NotifyService;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class DraftNotifyControllerTest {

    private static final Long DRAFT_ID = 7L;
    /** 이 일지의 주인. 전송은 주인과 관리자만 할 수 있다. */
    private static final AuthenticatedUser OWNER =
            new AuthenticatedUser(1L, "UngsikJo", AuthMethod.JWT, UserRole.MEMBER);

    private DraftRepository draftRepository;
    private NotifyService notifyService;
    private DraftNotifyController controller;

    @BeforeEach
    void setUp() {
        draftRepository = mock(DraftRepository.class);
        notifyService = mock(NotifyService.class);
        controller = new DraftNotifyController(draftRepository, notifyService);
    }

    /**
     * 완료(확정) 버튼을 없앤 뒤로 전송 조건은 "사람이 한 번이라도 저장했는가" 다 (9/10 결정).
     * 자동 생성 그대로를 채널에 흘리지 않기 위한 문턱이다.
     */
    private Draft givenDraft(boolean userEdited) {
        return givenDraft(DraftStatus.DRAFT, userEdited);
    }

    private Draft givenDraft(DraftStatus status) {
        return givenDraft(status, status == DraftStatus.CONFIRMED);
    }

    private Draft givenDraft(DraftStatus status, boolean userEdited) {
        User user = new User();
        user.setId(1L);
        user.setLogin("UngsikJo");

        Draft draft = new Draft();
        draft.setId(DRAFT_ID);
        draft.setUser(user);
        draft.setWorkDate(LocalDate.of(2026, 9, 10));
        draft.setStatus(status);
        draft.setUserEdited(userEdited);
        draft.setContentMd("# 업무 일지");
        when(draftRepository.findById(DRAFT_ID)).thenReturn(Optional.of(draft));
        return draft;
    }

    @Test
    @DisplayName("저장한 적 있는 일지는 전송된다")
    void sendsConfirmedDraft() {
        Draft draft = givenDraft(true);
        when(notifyService.notifyDraftSummarized(draft)).thenReturn(true);

        var response = controller.notifyDraft(OWNER, DRAFT_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().sent()).isTrue();
    }

    @Test
    @DisplayName("한 번도 저장하지 않은 일지는 409 로 막고 전송 자체를 시도하지 않는다")
    void rejectsUnconfirmedDraft() {
        givenDraft(false);

        assertThatThrownBy(() -> controller.notifyDraft(OWNER, DRAFT_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getCode()).isEqualTo("DRAFT_NOT_EDITED");
                });
        verify(notifyService, never()).notifyDraftSummarized(any());
    }

    @Test
    @DisplayName("저장 검사가 전송 실패보다 먼저다 — webhook 미설정 503 에 가려지면 안 된다")
    void confirmCheckComesBeforeSendFailure() {
        givenDraft(false);
        // webhook 이 없어 전송이 실패하는 상황을 만들어도
        when(notifyService.notifyDraftSummarized(any())).thenReturn(false);

        assertThatThrownBy(() -> controller.notifyDraft(OWNER, DRAFT_ID))
                .isInstanceOf(ApiException.class)
                // 503 NOTIFY_FAILED 가 아니라 409 가 나와야 한다
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("DRAFT_NOT_EDITED"));
    }

    @Test
    @DisplayName("저장한 일지인데 webhook 이 없으면 503")
    void reportsSendFailure() {
        Draft draft = givenDraft(true);
        when(notifyService.notifyDraftSummarized(draft)).thenReturn(false);

        assertThatThrownBy(() -> controller.notifyDraft(OWNER, DRAFT_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(api.getCode()).isEqualTo("NOTIFY_FAILED");
                });
    }

    @Test
    @DisplayName("없는 초안은 404")
    void missingDraftIs404() {
        when(draftRepository.findById(DRAFT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.notifyDraft(OWNER, DRAFT_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("DRAFT_NOT_FOUND"));
    }

    /**
     * 누구 일지인지 보지 않고 보내고 있었다 — 로그인만 했으면 남의 일지를 채널에 올릴 수
     * 있었다 (9/11 점검). 남의 것은 있는지도 알리지 않는다.
     */
    @Test
    @DisplayName("남의 일지는 전송할 수 없다 — 있는지도 알리지 않는다")
    void refusesOthersDraft() {
        Draft draft = givenDraft(true);
        AuthenticatedUser stranger = new AuthenticatedUser(9L, "other", AuthMethod.JWT, UserRole.MEMBER);

        assertThatThrownBy(() -> controller.notifyDraft(stranger, DRAFT_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(notifyService, never()).notifyDraftSummarized(draft);
    }

    /** 관리자 콘솔은 팀원 일지를 다룬다. 그 길은 열어 둔다. */
    @Test
    @DisplayName("관리자는 남의 일지도 보낼 수 있다")
    void adminMayNotify() {
        Draft draft = givenDraft(true);
        when(notifyService.notifyDraftSummarized(draft)).thenReturn(true);
        AuthenticatedUser admin = new AuthenticatedUser(5L, "admin", AuthMethod.JWT, UserRole.ADMIN);

        assertThat(controller.notifyDraft(admin, DRAFT_ID).getBody().sent()).isTrue();
    }
}
