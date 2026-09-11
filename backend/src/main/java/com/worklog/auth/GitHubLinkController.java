package com.worklog.auth;

import com.worklog.config.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final GitHubOAuthController oauthController;

    public GitHubLinkController(
            UserRepository userRepository,
            UserService userService,
            GitHubOAuthController oauthController) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.oauthController = oauthController;
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

    /**
     * 연동을 시작할 GitHub 주소를 만들어 준다.
     *
     * <p>예전에는 화면이 {@code /auth/github?link=<id>} 로 바로 이동했다. 그 경로는 인증
     * 없이 열려 있어 <b>누구든 남의 id 를 적어 자기 GitHub 을 그 계정에 붙일 수 있었다.</b>
     * 브라우저 이동이라 헤더를 실을 수 없는 구조 때문이다 (담당자 2 가 짚어 줌).
     *
     * <p>이제 <b>인증된 이 경로</b>가 서명된 state 에 자기 id 를 담아 URL 을 돌려주고,
     * 화면은 그 URL 로 이동만 한다. id 는 요청자의 토큰에서 나오므로 남의 것을 적을 수 없다.
     *
     * <p>fetch 로 부르는 요청이라 {@code Origin} 이 온다. 콜백 뒤 <b>그 화면으로</b> 돌아가게
     * state 에 함께 넣는다 — 사람마다 화면 주소가 달라도 된다 (BACKLOG2 §2-1). Vite 프록시는
     * Host 만 바꾸고 Origin 은 그대로 넘긴다.
     */
    @PostMapping("/start")
    public StartResponse start(
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        String returnTo = oauthController.allowedReturnTo(request.getHeader("Origin"));
        return new StartResponse(oauthController.authorizeUrl(principal.id(), returnTo, request));
    }

    @DeleteMapping
    public void unlink(@AuthenticationPrincipal AuthenticatedUser principal) {
        userService.unlinkGitHub(principal.id());
    }

    public record StartResponse(String url) {}

    public record GithubLink(boolean linked, String login, String avatarUrl, OffsetDateTime linkedAt) {}
}
