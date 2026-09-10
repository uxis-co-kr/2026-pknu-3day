package com.worklog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.worklog.config.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 최초 로그인 강제 변경을 통과할 수 있는지 본다.
 *
 * <p>서버가 화면보다 긴 비밀번호를 요구하면 변경이 늘 실패하고, 그 계정은 로그인할 때마다
 * 같은 화면으로 되돌아온다. 화면(PasswordPage.tsx)이 안내하는 "4자 이상" 이 기준이다.
 */
class LocalAuthControllerPasswordTest {

    private UserRepository userRepository;
    private LocalAuthController controller;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        controller =
                new LocalAuthController(
                        userRepository, mock(JwtService.class), mock(EmployeeAccountService.class));

        user = new User();
        user.setId(1L);
        user.setLoginId("9999");
        user.setPasswordHash(PasswordHasher.hash("9999"));
        user.setMustChangePassword(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void change(String current, String next) {
        controller.changePassword(
                new AuthenticatedUser(1L, "9999", AuthMethod.JWT, UserRole.MEMBER),
                new LocalAuthController.ChangePasswordRequest(current, next));
    }

    @Test
    @DisplayName("화면이 안내하는 4자면 바꿀 수 있고, 강제 변경 표시가 풀린다")
    void acceptsTheLengthTheScreenPromises() {
        change("9999", "abcd");

        assertThat(user.getMustChangePassword()).isFalse();
        assertThat(PasswordHasher.matches("abcd", user.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("안내보다 짧으면 거절한다")
    void rejectsShorterThanPromised() {
        assertThatThrownBy(() -> change("9999", "abc"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining(LocalAuthController.MIN_PASSWORD_LENGTH + "자 이상");
    }

    @Test
    @DisplayName("사원번호를 그대로 새 비밀번호로 쓸 수 없다 — 공개 정보다")
    void rejectsTheEmployeeNumberItself() {
        user.setPasswordHash(PasswordHasher.hash("temp0910"));

        assertThatThrownBy(() -> change("temp0910", "9999"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("사원번호와 같은");
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 401")
    void rejectsWrongCurrentPassword() {
        assertThatThrownBy(() -> change("0000", "abcd"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("현재 비밀번호");
    }
}
