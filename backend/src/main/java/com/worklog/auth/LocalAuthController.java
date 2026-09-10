package com.worklog.auth;

import com.worklog.config.ApiException;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    private final UserRepository userRepository;
    private final JwtService jwtService;

    public LocalAuthController(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @PostMapping("/auth/login")
    @Transactional
    public LoginResponse login(@RequestBody @jakarta.validation.Valid LoginRequest request) {
        String loginId = request.loginId().trim();
        User user = userRepository.findByLoginId(loginId).orElse(null);

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
     */
    @PutMapping("/me/password")
    @Transactional
    public ChangePasswordResponse changePassword(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody @jakarta.validation.Valid ChangePasswordRequest request) {

        User user = userRepository
                .findById(principal.id())
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));

        if (user.getPasswordHash() == null) {
            throw ApiException.badRequest(
                    "NO_LOCAL_PASSWORD", "이 계정은 GitHub 으로 로그인합니다. 바꿀 비밀번호가 없습니다.");
        }
        if (!PasswordHasher.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "현재 비밀번호가 올바르지 않습니다.");
        }
        if (request.newPassword().length() < 8) {
            throw ApiException.badRequest("PASSWORD_TOO_SHORT", "비밀번호는 8자 이상이어야 합니다.");
        }
        if (PasswordHasher.matches(request.newPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("PASSWORD_UNCHANGED", "지금 쓰는 비밀번호와 다르게 정해 주세요.");
        }

        user.setPasswordHash(PasswordHasher.hash(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
        log.info("{} 비밀번호 변경", user.getLoginId());
        return new ChangePasswordResponse(true);
    }

    public record LoginRequest(@NotBlank String loginId, @NotBlank String password) {}

    public record LoginResponse(String token, boolean mustChangePassword, String role) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword, @NotBlank String newPassword) {}

    public record ChangePasswordResponse(boolean changed) {}
}
