package com.worklog.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worklog.auth.User;
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

    private DraftRepository draftRepository;
    private NotifyService notifyService;
    private DraftNotifyController controller;

    @BeforeEach
    void setUp() {
        draftRepository = mock(DraftRepository.class);
        notifyService = mock(NotifyService.class);
        controller = new DraftNotifyController(draftRepository, notifyService);
    }

    private Draft givenDraft(DraftStatus status) {
        User user = new User();
        user.setId(1L);
        user.setLogin("UngsikJo");

        Draft draft = new Draft();
        draft.setId(DRAFT_ID);
        draft.setUser(user);
        draft.setWorkDate(LocalDate.of(2026, 9, 10));
        draft.setStatus(status);
        draft.setContentMd("# 업무 일지");
        when(draftRepository.findById(DRAFT_ID)).thenReturn(Optional.of(draft));
        return draft;
    }

    @Test
    @DisplayName("확정한 초안은 전송된다")
    void sendsConfirmedDraft() {
        Draft draft = givenDraft(DraftStatus.CONFIRMED);
        when(notifyService.notifyDraftContent(draft)).thenReturn(true);

        var response = controller.notifyDraft(DRAFT_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().sent()).isTrue();
    }

    @Test
    @DisplayName("확정하지 않은 초안은 409 로 막고 전송 자체를 시도하지 않는다")
    void rejectsUnconfirmedDraft() {
        givenDraft(DraftStatus.DRAFT);

        assertThatThrownBy(() -> controller.notifyDraft(DRAFT_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getCode()).isEqualTo("DRAFT_NOT_CONFIRMED");
                });
        verify(notifyService, never()).notifyDraftContent(any());
    }

    @Test
    @DisplayName("확정 검사가 전송 실패보다 먼저다 — webhook 미설정 503 에 가려지면 안 된다")
    void confirmCheckComesBeforeSendFailure() {
        givenDraft(DraftStatus.DRAFT);
        // webhook 이 없어 전송이 실패하는 상황을 만들어도
        when(notifyService.notifyDraftContent(any())).thenReturn(false);

        assertThatThrownBy(() -> controller.notifyDraft(DRAFT_ID))
                .isInstanceOf(ApiException.class)
                // 503 NOTIFY_FAILED 가 아니라 409 가 나와야 한다
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("DRAFT_NOT_CONFIRMED"));
    }

    @Test
    @DisplayName("확정본인데 webhook 이 없으면 503")
    void reportsSendFailure() {
        Draft draft = givenDraft(DraftStatus.CONFIRMED);
        when(notifyService.notifyDraftContent(draft)).thenReturn(false);

        assertThatThrownBy(() -> controller.notifyDraft(DRAFT_ID))
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

        assertThatThrownBy(() -> controller.notifyDraft(DRAFT_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("DRAFT_NOT_FOUND"));
    }
}
