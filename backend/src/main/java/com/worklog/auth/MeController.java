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
                Boolean.TRUE.equals(user.getMustChangePassword()),
                consoleOnly(user));
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
            boolean mustChangePassword,
            /** 관리자 콘솔 말고는 볼 것이 없는 계정인가. {@link #consoleOnly} 참고 */
            boolean consoleOnly) {}

    /**
     * 콘솔을 열려고 둔 관리자 계정인가 (9/11).
     *
     * <p>사원도 아니고 GitHub 도 붙어 있지 않아, 일반 화면(깃허브 내역·VSCode 내역·업무 일지)
     * 에는 보여 줄 것이 하나도 없다. 늘 빈 화면을 띄우느니 콘솔로 돌려보낸다.
     *
     * <p>이름을 콕 집지 않는다 — <b>관리자를 겸하는 팀원</b>은 자기 기록이 있으므로 일반 화면을
     * 그대로 쓴다. 팀원 내역에서 이 계정을 빼는 규칙과 같은 자다 ({@code PeopleStatsService}).
     */
    private static boolean consoleOnly(User user) {
        return user.getRole() == UserRole.ADMIN && user.getEmpSeq() == null && user.getGithubId() == null;
    }
}
