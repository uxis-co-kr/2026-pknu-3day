package com.worklog.auth;

import com.worklog.config.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GitHub OAuth 로그인 (PRD F5, 7).
 *
 * <pre>
 *   GET /auth/github          → 서명한 state 를 붙여 GitHub 인가 페이지로 302
 *   GET /auth/github/callback → state 서명·만료 검증 → code 교환 → 사용자 UPSERT → JWT 발급
 *                             → 302 {FRONTEND_URL}/auth/done?token=...
 * </pre>
 *
 * <p>state 는 쿠키가 아니라 서명으로 검증한다 ({@link OAuthStateCodec}). 화면 주소와 콜백 주소의
 * 호스트가 달라도(사내 IP 로 열고 콜백은 localhost 로 등록) 쿠키에 기대지 않으니 통한다.
 */
@RestController
@RequestMapping("/auth/github")
public class GitHubOAuthController {

    private static final Logger log = LoggerFactory.getLogger(GitHubOAuthController.class);

    private final GitHubOAuthClient client;
    private final GitHubOAuthProperties properties;
    private final UserService userService;
    private final JwtService jwtService;
    private final String frontendUrl;
    private final OAuthStateCodec stateCodec;
    private final OriginPolicy originPolicy;

    public GitHubOAuthController(
            GitHubOAuthClient client,
            GitHubOAuthProperties properties,
            UserService userService,
            JwtService jwtService,
            OAuthStateCodec stateCodec,
            OriginPolicy originPolicy,
            @Value("${worklog.frontend-url}") String frontendUrl) {
        this.client = client;
        this.properties = properties;
        this.userService = userService;
        this.jwtService = jwtService;
        this.stateCodec = stateCodec;
        this.originPolicy = originPolicy;
        this.frontendUrl = stripTrailingSlash(frontendUrl);
    }

    /**
     * GitHub 인가 주소를 만든다.
     *
     * @param link 이미 로그인한 계정에 GitHub 을 <b>붙이러</b> 온 것이면 그 사용자 id.
     *     <b>이 값은 반드시 인증된 자리에서 넘겨야 한다</b> — 요청 파라미터로 받으면 누구든
     *     남의 id 를 적어 자기 GitHub 을 그 계정에 붙일 수 있다.
     *     {@code POST /me/github/start} 가 토큰에서 꺼내 넘긴다.
     * @param returnTo 콜백 뒤 돌아갈 화면 주소. 사람마다 화면 주소가 다르므로(BACKLOG2 §2-1)
     *     요청의 Origin/Referer 에서 온 값을 {@link OriginPolicy} 로 걸러 넘긴다. 없으면 FRONTEND_URL
     */
    String authorizeUrl(Long link, String returnTo, HttpServletRequest request) {
        requireConfigured();
        // 콜백은 새 요청이라 Authorization 헤더가 없다. 누구에게 붙일지, 어디로 돌아갈지를
        // state 안에 서명해 넘긴다.
        String state = stateCodec.issue(link, returnTo);
        return AUTHORIZE_URL_PREFIX
                + "?client_id=" + encode(properties.getClientId())
                + "&redirect_uri=" + encode(redirectUri(request))
                + "&scope=" + encode(properties.getScope())
                + "&state=" + encode(state);
    }

    /**
     * GitHub 로그인 시작. 연동(link)은 여기서 받지 않는다 — {@code POST /me/github/start} 를 쓴다.
     */
    @GetMapping
    public void authorize(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        // 브라우저 이동이라 Origin 이 없다. Referer 가 있으면 같은 기준으로 쓴다.
        response.sendRedirect(authorizeUrl(null, allowedReturnTo(request.getHeader("Referer")), request));
    }

    /**
     * 돌아갈 화면 주소로 써도 되는 값만 남긴다. 거절해도 오류를 내지 않는다 — 그냥 서버 설정
     * ({@code FRONTEND_URL}) 으로 돌아가게 두고 로그만 남긴다.
     */
    String allowedReturnTo(String origin) {
        if (origin == null || origin.isBlank()) {
            return null;
        }
        Optional<String> normalized = originPolicy.normalize(origin);
        if (normalized.isEmpty()) {
            log.info("허용되지 않은 화면 주소 {} — FRONTEND_URL 로 돌려보낸다.", origin);
        }
        return normalized.orElse(null);
    }

    @GetMapping("/callback")
    public void callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false, name = "error_description") String errorDescription,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {
        requireConfigured();

        if (code == null || code.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "OAUTH_CODE_MISSING",
                    errorDescription == null ? "GitHub 이 code 를 돌려주지 않았습니다." : errorDescription);
        }
        OAuthStateCodec.Parsed parsed = stateCodec.verify(state).orElseThrow(() -> {
            log.warn("OAuth state 검증 실패 — 서명이 다르거나 5분이 지났다. state={}", state);
            return new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "OAUTH_STATE_MISMATCH",
                    "로그인 요청이 유효하지 않습니다 (5분이 지났거나 주소가 손상됨). 다시 시도해 주세요.");
        });

        String accessToken = client.exchangeCode(
                code, properties.getClientId(), properties.getClientSecret(), redirectUri(request));
        GitHubOAuthClient.GitHubUserDto dto = client.fetchUser(accessToken);

        Long linkTo = parsed.linkUserId();
        // 시작할 때 서명해 둔 화면 주소로 돌아간다. 없으면 서버 설정값.
        String front = frontendFor(parsed);

        if (linkTo != null) {
            // 이미 로그인한 계정에 붙이는 경우. 새 계정을 만들지 않는다 (TODO_0910 §1-1).
            try {
                User user = userService.linkGitHub(linkTo, dto, accessToken);
                log.info("GitHub 연동 성공: {} → 사용자 {}", user.getLogin(), user.getId());
                response.sendRedirect(front + "/settings?github=linked");
            } catch (ApiException e) {
                log.warn("GitHub 연동 실패: {}", e.getMessage());
                response.sendRedirect(front + "/settings?github=" + encode(e.getCode()));
            }
            return;
        }

        User user = userService.upsertFromGitHub(dto, accessToken);
        String jwt = jwtService.issue(user);

        log.info("GitHub 로그인 성공: {} (id={})", user.getLogin(), user.getId());
        response.sendRedirect(front + "/auth/done?token=" + encode(jwt));
    }

    /** state 에 실린 화면 주소가 지금도 허용 대역이면 그것, 아니면 FRONTEND_URL. */
    String frontendFor(OAuthStateCodec.Parsed parsed) {
        String returnTo = parsed == null ? null : allowedReturnTo(parsed.returnTo());
        return returnTo == null ? frontendUrl : stripTrailingSlash(returnTo);
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "OAUTH_NOT_CONFIGURED",
                    "GITHUB_CLIENT_ID / GITHUB_CLIENT_SECRET 이 설정되지 않았습니다.");
        }
    }

    /**
     * OAuth App 에 등록한 콜백 주소와 글자까지 같아야 한다. 설정(GITHUB_REDIRECT_URI)이 있으면
     * 그것, 없으면 요청 URL 에서 만든다 — 프록시 뒤에서는 요청 호스트가 localhost 로 보이므로,
     * 다른 기기에서 쓰려면 설정으로 못 박는다.
     */
    private String redirectUri(HttpServletRequest request) {
        if (properties.getRedirectUri() != null && !properties.getRedirectUri().isBlank()) {
            return properties.getRedirectUri().trim();
        }
        String scheme = request.getScheme();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return scheme
                + "://"
                + request.getServerName()
                + (defaultPort ? "" : ":" + port)
                + request.getContextPath()
                + "/auth/github/callback";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String url) {
        return Optional.ofNullable(url)
                .map(u -> u.endsWith("/") ? u.substring(0, u.length() - 1) : u)
                .orElse("");
    }

    private static final String AUTHORIZE_URL_PREFIX = GitHubOAuthClient.AUTHORIZE_URL;
}
