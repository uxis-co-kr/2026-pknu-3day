package com.worklog.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worklog.activity.ActivityRepository;
import com.worklog.auth.User;
import com.worklog.config.ApiException;
import com.worklog.vscode.VscodeSessionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class DraftServiceTest {

    private static final Long OWNER = 3L;
    private static final Long OTHER = 4L;

    @Mock private DraftRepository drafts;
    @Mock private ActivityRepository activities;
    @Mock private VscodeSessionRepository sessions;

    private DraftService service;

    @BeforeEach
    void setUp() {
        service = new DraftService(drafts, activities, sessions);
        lenient().when(activities.findAllById(anyIterable())).thenReturn(List.of());
        lenient().when(sessions.findAllById(anyIterable())).thenReturn(List.of());
        lenient().when(drafts.save(any(Draft.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Draft draft(DraftStatus status) {
        User owner = new User();
        owner.setId(OWNER);
        Draft d = new Draft();
        d.setId(7L);
        d.setUser(owner);
        d.setWorkDate(LocalDate.of(2026, 9, 10));
        d.setVersion(1);
        d.setStatus(status);
        d.setContentMd("# 초안");
        when(drafts.findById(7L)).thenReturn(Optional.of(d));
        return d;
    }

    @Test
    @DisplayName("본인 초안은 저장된다")
    void ownerCanSave() {
        draft(DraftStatus.DRAFT);
        assertThat(service.updateContent(7L, OWNER, "# 고친 초안").contentMd()).isEqualTo("# 고친 초안");
    }

    @Test
    @DisplayName("남의 초안은 403")
    void otherCannotSave() {
        draft(DraftStatus.DRAFT);
        assertThatThrownBy(() -> service.updateContent(7L, OTHER, "x"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN)
                .hasFieldOrPropertyWithValue("code", "NOT_DRAFT_OWNER");
        verify(drafts, never()).save(any());
    }

    @Test
    @DisplayName("확정된 초안은 수정할 수 없다 — 409")
    void confirmedIsReadOnly() {
        draft(DraftStatus.CONFIRMED);
        assertThatThrownBy(() -> service.updateContent(7L, OWNER, "x"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "DRAFT_ALREADY_CONFIRMED");
    }

    @Test
    @DisplayName("확정하면 상태와 확정 시각이 남는다")
    void confirms() {
        draft(DraftStatus.DRAFT);
        var res = service.confirm(7L, OWNER);
        assertThat(res.status()).isEqualTo(DraftStatus.CONFIRMED);
        assertThat(res.confirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 확정된 초안을 다시 확정해도 오류가 아니고 시각도 바뀌지 않는다")
    void confirmIsIdempotent() {
        Draft d = draft(DraftStatus.CONFIRMED);
        d.setConfirmedAt(java.time.OffsetDateTime.parse("2026-09-10T18:10:00+09:00"));

        var res = service.confirm(7L, OWNER);

        assertThat(res.status()).isEqualTo(DraftStatus.CONFIRMED);
        assertThat(res.confirmedAt()).isEqualTo(java.time.OffsetDateTime.parse("2026-09-10T18:10:00+09:00"));
        verify(drafts, never()).save(any());
    }

    @Test
    @DisplayName("남의 초안은 확정도 못 한다")
    void otherCannotConfirm() {
        draft(DraftStatus.DRAFT);
        assertThatThrownBy(() -> service.confirm(7L, OTHER))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "NOT_DRAFT_OWNER");
    }

    @Test
    @DisplayName("없는 초안은 404")
    void notFound() {
        when(drafts.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detail(99L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "DRAFT_NOT_FOUND");
    }
}
