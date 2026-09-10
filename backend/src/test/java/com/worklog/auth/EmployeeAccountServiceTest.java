package com.worklog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worklog.admin.WapleClient;
import com.worklog.admin.WapleProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 사원 번호 첫 로그인 (TODO_0910 §1-1).
 * 로그인 화면이 "처음 로그인한다면 비밀번호는 사원번호와 같습니다" 라고 안내한다.
 */
class EmployeeAccountServiceTest {

    private UserRepository userRepository;
    private WapleClient wapleClient;
    private EmployeeAccountService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        wapleClient = mock(WapleClient.class);
        when(wapleClient.employees(anyLong()))
                .thenReturn(List.of(new WapleClient.Employee(9998L, "배태일")));
        when(userRepository.findByEmpSeq(anyLong())).thenReturn(List.of());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        WapleProperties properties = new WapleProperties();
        properties.setCompanySeq(29L);
        service = new EmployeeAccountService(userRepository, wapleClient, properties);
    }

    @Test
    @DisplayName("사원 목록에 있고 비밀번호가 사원번호와 같으면 계정을 만든다")
    void createsAccount() {
        User user = service.provisionOnFirstLogin("9998", "9998").orElseThrow();

        assertThat(user.getLoginId()).isEqualTo("9998");
        assertThat(user.getName()).isEqualTo("배태일");
        assertThat(user.getEmpSeq()).isEqualTo(9998L);
        assertThat(user.getCoSeq()).isEqualTo(29L);
        assertThat(user.getRole()).isEqualTo(UserRole.MEMBER);
        // 사원 번호는 비밀이 아니다.
        assertThat(user.getMustChangePassword()).isTrue();
        assertThat(PasswordHasher.matches("9998", user.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("비밀번호가 사원번호와 다르면 만들지 않는다")
    void requiresPasswordEqualToLoginId() {
        assertThat(service.provisionOnFirstLogin("9998", "other")).isEmpty();
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("사원 목록에 없는 번호는 만들지 않는다")
    void requiresKnownEmployee() {
        assertThat(service.provisionOnFirstLogin("1234", "1234")).isEmpty();
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("숫자가 아닌 아이디는 이 경로로 만들지 않는다 — admin 같은 계정은 따로 만든다")
    void ignoresNonNumericId() {
        assertThat(service.provisionOnFirstLogin("admin", "admin")).isEmpty();
    }

    @Test
    @DisplayName("이미 그 사원에 이어진 계정이 있으면 새로 만들지 않고 로그인 수단만 붙인다")
    void claimsExistingLinkedAccount() {
        User linked = new User();
        linked.setId(1L);
        linked.setLogin("UngsikJo");
        linked.setEmpSeq(9998L);
        linked.setRole(UserRole.ADMIN);
        when(userRepository.findByEmpSeq(9998L)).thenReturn(List.of(linked));

        User user = service.provisionOnFirstLogin("9998", "9998").orElseThrow();

        // 같은 계정이어야 한다. 한 사람이 계정 둘을 가지면 활동과 업무 일지가 갈린다.
        assertThat(user.getId()).isEqualTo(1L);
        assertThat(user.getLogin()).isEqualTo("UngsikJo");
        assertThat(user.getLoginId()).isEqualTo("9998");
        assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(PasswordHasher.matches("9998", user.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("이미 비밀번호가 있는 계정은 사원번호로 다시 열어 주지 않는다")
    void doesNotResetExistingPassword() {
        User existing = new User();
        existing.setId(1L);
        existing.setEmpSeq(9998L);
        existing.setPasswordHash(PasswordHasher.hash("이미바꾼비밀번호"));
        when(userRepository.findByEmpSeq(9998L)).thenReturn(List.of(existing));

        assertThat(service.provisionOnFirstLogin("9998", "9998")).isEmpty();
        verify(userRepository, never()).save(any());
    }
}
