package com.worklog.auth;

import com.worklog.config.ApiException;
import java.time.OffsetDateTime;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 계정의 GitHub 연동 상태 (설정 > 깃허브 연동).
 *
 * <p>연결 자체는 OAuth 라 브라우저가 {@code /auth/github?link={userId}} 로 넘어갔다 온다.
 * 여기서는 <b>상태를 읽고 끊는 것</b>만 한다 — fetch 로 붙일 수 있는 것이 아니다.
 */
@RestController
@RequestMapping("/me/github")
public class GitHubLinkController {

    private final UserRepository userRepository;
    private final UserService userService;

    public GitHubLinkController(UserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @GetMapping
    public GithubLink status(@AuthenticationPrincipal AuthenticatedUser principal) {
        User user = userRepository
                .findById(principal.id())
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));

        // 토큰이 있어야 수집을 할 수 있다. login 만 있고 토큰이 없으면 연결된 것이 아니다.
        boolean linked = user.getGithubTokenEnc() != null && !user.getGithubTokenEnc().isBlank();
        return new GithubLink(
                linked,
                linked ? user.getLogin() : null,
                linked ? user.getAvatarUrl() : null,
                linked ? user.getCreatedAt() : null);
    }

    @DeleteMapping
    public void unlink(@AuthenticationPrincipal AuthenticatedUser principal) {
        userService.unlinkGitHub(principal.id());
    }

    public record GithubLink(boolean linked, String login, String avatarUrl, OffsetDateTime linkedAt) {}
}
