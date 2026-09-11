package com.worklog.auth;

import com.worklog.config.ApiException;
import com.worklog.config.InternalNetwork;
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
    /** 비우면 요청에서 만든다. 여럿이 한 서버를 볼 때만 채운다 (BACKLOG2 §2-1). */
    private final String configuredRedirectUri;
    private final OAuthStateCodec stateCodec;

    public GitHubOAuthController(
            GitHubOAuthClient client,
            GitHubOAuthProperties properties,
            UserService userService,
            JwtService jwtService,
            OAuthStateCodec stateCodec,
            @Value("${worklog.frontend-url}") String frontendUrl,
            @Value("${worklog.oauth-redirect-uri:}") String configuredRedirectUri) {
        this.client = client;
        this.properties = properties;
        this.userService = userService;
        this.jwtService = jwtService;
        this.stateCodec = stateCodec;
        this.frontendUrl = stripTrailingSlash(frontendUrl);
        this.configuredRedirectUri = configuredRedirectUri == null ? null : configuredRedirectUri.trim();
    }

    /**
     * GitHub 인가 주소를 만든다.
     *
     * @param link 이미 로그인한 계정에 GitHub 을 <b>붙이러</b> 온 것이면 그 사용자 id.
     *     <b>이 값은 반드시 인증된 자리에서 넘겨야 한다</b> — 요청 파라미터로 받으면 누구든
     *     남의 id 를 적어 자기 GitHub 을 그 계정에 붙일 수 있다.
     *     {@code POST /me/github/start} 가 토큰에서 꺼내 넘긴다.
     */
    String authorizeUrl(Long link, HttpServletRequest request) {
        requireConfigured();
        // 콜백은 새 요청이라 Authorization 헤더가 없다. 누구에게 붙일지, 어디로 돌아갈지
        // state 안에 서명해 넘긴다. 사람마다 접속 주소가 다르므로(각자 자기 내부 IP)
        // FRONTEND_URL 하나로는 늘 남의 화면으로 돌아간다 (BACKLOG2 §2-1).
        String state = stateCodec.issue(link, allowedOrigin(request));
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
        response.sendRedirect(authorizeUrl(null, request));
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
        // 시작한 사람의 화면으로 돌려보낸다. 사람마다 접속 주소가 다르다 (BACKLOG2 §2-1).
        String returnTo = returnTo(parsed.returnOrigin());

        if (linkTo != null) {
            // 이미 로그인한 계정에 붙이는 경우. 새 계정을 만들지 않는다 (TODO_0910 §1-1).
            try {
                User user = userService.linkGitHub(linkTo, dto, accessToken);
                log.info("GitHub 연동 성공: {} → 사용자 {}", user.getLogin(), user.getId());
                response.sendRedirect(returnTo + "/settings?github=linked");
            } catch (ApiException e) {
                log.warn("GitHub 연동 실패: {}", e.getMessage());
                response.sendRedirect(returnTo + "/settings?github=" + encode(e.getCode()));
            }
            return;
        }

        User user = userService.upsertFromGitHub(dto, accessToken);
        String jwt = jwtService.issue(user);

        log.info("GitHub 로그인 성공: {} (id={})", user.getLogin(), user.getId());
        response.sendRedirect(returnTo + "/auth/done?token=" + encode(jwt));
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
    /**
     * 돌아갈 화면 주소. 사내망 주소만 받는다.
     *
     * <p>요청이 알려 준 주소를 그대로 믿으면 <b>열린 리다이렉트</b>가 된다 — 바깥 주소를
     * 적어 보내면 토큰이 그리로 날아간다. 그래서 사설 대역과 localhost 만 통과시킨다.
     *
     * @return 통과하면 그 주소, 아니면 null (설정의 FRONTEND_URL 을 쓴다)
     */
    String allowedOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            return null;
        }
        if (!InternalNetwork.isInternalOrigin(origin)) {
            log.warn("사내망 밖의 Origin 은 돌아갈 주소로 쓰지 않는다: {}", origin);
            return null;
        }
        return stripTrailingSlash(origin.trim());
    }

    private String returnTo(String returnOrigin) {
        return returnOrigin == null || returnOrigin.isBlank() ? frontendUrl : returnOrigin;
    }

    /**
     * GitHub 이 돌아올 콜백 주소.
     *
     * <p>기본은 요청에서 만든다. 그런데 여럿이 한 서버를 볼 때는 그러면 안 된다 — 프록시를
     * 지나온 요청은 {@code localhost:8080} 으로 보이고, GitHub 은 그 주소로 <b>각자의
     * 브라우저</b>를 보낸다. 남의 PC 에는 그 서버가 없다.
     *
     * <p>그래서 {@code WORKLOG_OAUTH_REDIRECT_URI} 가 있으면 그것을 쓴다. OAuth App 에
     * 등록한 주소와 글자까지 같아야 하므로, 어차피 한 곳에 적어 두는 편이 맞다.
     */
    private String redirectUri(HttpServletRequest request) {
        if (configuredRedirectUri != null && !configuredRedirectUri.isBlank()) {
            return configuredRedirectUri;
        }
        return derivedRedirectUri(request);
    }

    private String derivedRedirectUri(HttpServletRequest request) {
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
