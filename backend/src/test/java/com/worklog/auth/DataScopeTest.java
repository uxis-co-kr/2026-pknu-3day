package com.worklog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.worklog.config.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DAY3_plan C-1 — MEMBER 는 자기 것만, ADMIN 은 아무 것이나. */
class DataScopeTest {

    private static final AuthenticatedUser MEMBER = new AuthenticatedUser(7L, "taeil", AuthMethod.JWT, UserRole.MEMBER);
    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(1L, "admin", AuthMethod.JWT, UserRole.ADMIN);

    @Test
    @DisplayName("MEMBER 가 userId 를 생략하면 자기 id 로 채운다")
    void memberDefaultsToSelf() {
        assertThat(DataScope.userIdFor(MEMBER, null)).isEqualTo(7L);
        assertThat(DataScope.userIdFor(MEMBER, 7L)).isEqualTo(7L);
    }

    @Test
    @DisplayName("MEMBER 가 남의 id 를 명시하면 403 — 조용히 바꾸면 화면 버그를 못 찾는다")
    void memberCannotAskForOthers() {
        assertThatThrownBy(() -> DataScope.userIdFor(MEMBER, 8L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("본인의 기록만");
    }

    @Test
    @DisplayName("ADMIN 은 생략하면 전원(null), 명시하면 그 사람")
    void adminIsUnrestricted() {
        assertThat(DataScope.userIdFor(ADMIN, null)).isNull();
        assertThat(DataScope.userIdFor(ADMIN, 8L)).isEqualTo(8L);
    }

    @Test
    @DisplayName("단건은 주인이 나거나 내가 관리자일 때만. 주인 없는 것은 MEMBER 에게 없는 것이다")
    void canSee() {
        assertThat(DataScope.canSee(MEMBER, 7L)).isTrue();
        assertThat(DataScope.canSee(MEMBER, 8L)).isFalse();
        assertThat(DataScope.canSee(MEMBER, null)).isFalse();
        assertThat(DataScope.canSee(ADMIN, 8L)).isTrue();
        assertThat(DataScope.canSee(ADMIN, null)).isTrue();
        assertThat(DataScope.canSee(null, 7L)).isFalse();
    }
}
