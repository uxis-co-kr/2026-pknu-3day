package com.worklog.auth;

import java.time.OffsetDateTime;

import com.worklog.config.ApiException;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * 아이디·비밀번호 로그인 (TODO_0910 §1-1).
 *
 * <p>GitHub OAuth 는 그대로 남는다. 그쪽은 "GitHub 활동을 모을 권한"을 받는 수단이고,
 * 이쪽은 "이 서비스에 들어오는" 수단이다. 관리자처럼 GitHub 계정을 쓰지 않는 사람도 있다.
 */
@RestController
public class LocalAuthController {

    private static final Logger log = LoggerFactory.getLogger(LocalAuthController.class);

    /**
     * 최소 길이는 로그인 화면이 안내하는 값과 같아야 한다. 화면은 "4자 이상" 이라고 적어 두는데
     * 서버가 8자를 요구하면, 최초 로그인 강제 변경을 통과할 방법이 없어 로그인할 때마다 같은
     * 화면으로 되돌아온다. 규칙은 한 곳에서만 정한다 (PasswordPage.tsx / mockServer.ts 와 동일).
     */
    static final int MIN_PASSWORD_LENGTH = 4;

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final EmployeeAccountService employeeAccountService;

    public LocalAuthController(
            UserRepository userRepository,
            JwtService jwtService,
            EmployeeAccountService employeeAccountService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.employeeAccountService = employeeAccountService;
    }

    @PostMapping("/auth/login")
    @Transactional
    public LoginResponse login(@RequestBody @jakarta.validation.Valid LoginRequest request) {
        String loginId = request.loginId().trim();
        User user = userRepository.findByLoginId(loginId).orElse(null);

        // 계정이 없으면 사원 번호로 처음 들어오는 경우인지 본다 (TODO_0910 §1-1).
        if (user == null) {
            user = employeeAccountService
                    .provisionOnFirstLogin(loginId, request.password())
                    .orElse(null);
            if (user != null) {
                return new LoginResponse(jwtService.issue(user), true, user.getRole().name());
            }
        }

        // 아이디가 없는 것과 비밀번호가 틀린 것을 구분해 알려주지 않는다.
        if (user == null || !PasswordHasher.matches(request.password(), user.getPasswordHash())) {
            log.info("로그인 실패: {}", loginId);
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_CREDENTIALS",
                    "아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        log.info("로그인 성공: {} ({})", loginId, user.getRole());
        return new LoginResponse(
                jwtService.issue(user),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                user.getRole().name());
    }

    /**
     * 비밀번호 변경. 최초 비밀번호는 발급자가 알고 있으므로 처음 로그인하면 반드시 바꾸게 한다.
     *
     * <p>화면은 POST 로 부른다(hooks.ts / mockServer.ts). PUT 도 함께 받는다 — 한쪽만 열어 두면
     * 변경이 405 로 막히고, 그 계정은 로그인할 때마다 강제 변경 화면으로 되돌아온다.
     */
    @RequestMapping(value = "/me/password", method = {RequestMethod.POST, RequestMethod.PUT})
    @Transactional
    public ChangePasswordResponse changePassword(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody @jakarta.validation.Valid ChangePasswordRequest request) {

        User user = userRepository
                .findById(principal.id())
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));

        if (user.getPasswordHash() == null) {
            throw reject(user, "NO_LOCAL_PASSWORD", "이 계정은 GitHub 으로 로그인합니다. 바꿀 비밀번호가 없습니다.");
        }
        if (!PasswordHasher.matches(request.currentPassword(), user.getPasswordHash())) {
            log.info("{} 비밀번호 변경 거절: INVALID_CREDENTIALS", user.getLoginId());
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "현재 비밀번호가 올바르지 않습니다.");
        }
        if (request.newPassword().length() < MIN_PASSWORD_LENGTH) {
            throw reject(
                    user, "PASSWORD_TOO_SHORT", "비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.");
        }
        // 사원번호는 사원 목록 API 로 누구나 조회할 수 있어 비밀이 아니다 (TODO_0910 §1-1).
        if (request.newPassword().equals(user.getLoginId())) {
            throw reject(user, "PASSWORD_IS_LOGIN_ID", "사원번호와 같은 비밀번호는 쓸 수 없습니다.");
        }
        if (PasswordHasher.matches(request.newPassword(), user.getPasswordHash())) {
            throw reject(user, "PASSWORD_UNCHANGED", "지금 쓰는 비밀번호와 다르게 정해 주세요.");
        }

        user.setPasswordHash(PasswordHasher.hash(request.newPassword()));
        user.setMustChangePassword(false);
        // 이 시각보다 먼저 발급된 토큰은 전부 죽는다 — 다른 기기의 세션도, 유출된 토큰도 (V11).
        user.setPasswordChangedAt(OffsetDateTime.now());
        userRepository.save(user);
        log.info("{} 비밀번호 변경 — 이전 토큰 무효", user.getLoginId());
        // 지금 이 요청의 토큰도 방금 죽었다. 새 토큰을 실어 주어 화면이 갈아 끼우게 한다.
        return new ChangePasswordResponse(true, jwtService.issue(user));
    }

    /**
     * 변경이 거절된 이유를 남긴다. 최초 로그인 강제 변경을 통과하지 못하면 로그인할 때마다
     * 같은 화면으로 되돌아오는데, 성공만 기록하면 왜 막혔는지 볼 방법이 없다.
     */
    private ApiException reject(User user, String code, String message) {
        log.info("{} 비밀번호 변경 거절: {}", user.getLoginId(), code);
        return ApiException.badRequest(code, message);
    }

    public record LoginRequest(@NotBlank String loginId, @NotBlank String password) {}

    public record LoginResponse(String token, boolean mustChangePassword, String role) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword, @NotBlank String newPassword) {}

    /** @param token 변경 뒤 새로 발급한 토큰. 이전 토큰은 더 통하지 않는다 */
    public record ChangePasswordResponse(boolean changed, String token) {}
}
