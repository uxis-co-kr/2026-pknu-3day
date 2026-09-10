package com.worklog.auth;

import com.worklog.config.ApiException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 현재 사용자 (PRD 7. GET /me ★ — JWT / API Key 둘 다 허용).
 */
@RestController
public class MeController {

    private final UserRepository userRepository;

    public MeController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        User user = userRepository
                .findById(principal.id())
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
        return new MeResponse(
                user.getId(),
                user.getLogin(),
                user.getName(),
                user.getAvatarUrl(),
                user.getLoginId(),
                user.getRole() == null ? null : user.getRole().name(),
                Boolean.TRUE.equals(user.getMustChangePassword()));
    }

    /**
     * 담당자 1의 목업 JSON 과 필드명이 같아야 한다 (PRD 7. 대표 응답 스키마).
     *
     * <p>{@code mustChangePassword} 를 여기서도 돌려준다. 화면이 로그인 응답만 보고 브라우저에
     * 표시를 남겨 두면, 이미 바꾼 계정이 옛 표시 때문에 변경 화면에 갇힐 수 있다. 서버가 매번
     * 사실을 알려 주면 화면이 스스로 바로잡는다.
     */
    public record MeResponse(
            Long id,
            String login,
            String name,
            String avatarUrl,
            String loginId,
            String role,
            boolean mustChangePassword) {}
}
