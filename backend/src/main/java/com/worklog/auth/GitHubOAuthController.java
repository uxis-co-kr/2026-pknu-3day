package com.worklog.auth;

import com.worklog.config.ApiException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
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
 *   GET /auth/github          → state 쿠키 발급 후 GitHub 인가 페이지로 302
 *   GET /auth/github/callback → state 검증 → code 교환 → 사용자 UPSERT → JWT 발급
 *                             → 302 {FRONTEND_URL}/auth/done?token=...
 * </pre>
 */
@RestController
@RequestMapping("/auth/github")
public class GitHubOAuthController {

    private static final Logger log = LoggerFactory.getLogger(GitHubOAuthController.class);

    private static final String STATE_COOKIE = "worklog_oauth_state";
    private static final int STATE_TTL_SECONDS = 300;

    private final GitHubOAuthClient client;
    private final GitHubOAuthProperties properties;
    private final UserService userService;
    private final JwtService jwtService;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();

    public GitHubOAuthController(
            GitHubOAuthClient client,
            GitHubOAuthProperties properties,
            UserService userService,
            JwtService jwtService,
            @Value("${worklog.frontend-url}") String frontendUrl) {
        this.client = client;
        this.properties = properties;
        this.userService = userService;
        this.jwtService = jwtService;
        this.frontendUrl = stripTrailingSlash(frontendUrl);
    }

    /**
     * @param link 이미 로그인한 계정에 GitHub 을 <b>붙이러</b> 온 것이면 그 사용자 id.
     *     사원 번호로 로그인한 사람이 설정에서 연동할 때 쓴다. 없으면 GitHub 로그인이다.
     */
    @GetMapping
    public void authorize(
            @RequestParam(required = false) Long link,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {
        requireConfigured();

        String state = randomState();
        response.addCookie(stateCookie(state, STATE_TTL_SECONDS, request.isSecure()));
        // 콜백은 새 요청이라 Authorization 헤더가 없다. 누구에게 붙일지 쿠키로 넘긴다.
        response.addCookie(linkCookie(link == null ? "" : link.toString(), request.isSecure()));

        String url = AUTHORIZE_URL_PREFIX
                + "?client_id=" + encode(properties.getClientId())
                + "&redirect_uri=" + encode(redirectUri(request))
                + "&scope=" + encode(properties.getScope())
                + "&state=" + encode(state);
        response.sendRedirect(url);
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
        // state 쿠키는 성공하든 실패하든 재사용되지 않게 지운다.
        response.addCookie(stateCookie("", 0, request.isSecure()));

        if (code == null || code.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "OAUTH_CODE_MISSING",
                    errorDescription == null ? "GitHub 이 code 를 돌려주지 않았습니다." : errorDescription);
        }
        String expected = readStateCookie(request);
        if (expected == null || !expected.equals(state)) {
            log.warn("OAuth state 불일치 — CSRF 가능성. expected={}, actual={}", expected, state);
            throw new ApiException(HttpStatus.BAD_REQUEST, "OAUTH_STATE_MISMATCH", "로그인 요청이 유효하지 않습니다. 다시 시도해 주세요.");
        }

        String accessToken = client.exchangeCode(
                code, properties.getClientId(), properties.getClientSecret(), redirectUri(request));
        GitHubOAuthClient.GitHubUserDto dto = client.fetchUser(accessToken);

        Long linkTo = readLinkCookie(request);
        response.addCookie(linkCookie("", request.isSecure()));

        if (linkTo != null) {
            // 이미 로그인한 계정에 붙이는 경우. 새 계정을 만들지 않는다 (TODO_0910 §1-1).
            try {
                User user = userService.linkGitHub(linkTo, dto, accessToken);
                log.info("GitHub 연동 성공: {} → 사용자 {}", user.getLogin(), user.getId());
                response.sendRedirect(frontendUrl + "/settings?github=linked");
            } catch (ApiException e) {
                log.warn("GitHub 연동 실패: {}", e.getMessage());
                response.sendRedirect(frontendUrl + "/settings?github=" + encode(e.getCode()));
            }
            return;
        }

        User user = userService.upsertFromGitHub(dto, accessToken);
        String jwt = jwtService.issue(user);

        log.info("GitHub 로그인 성공: {} (id={})", user.getLogin(), user.getId());
        response.sendRedirect(frontendUrl + "/auth/done?token=" + encode(jwt));
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
     * OAuth App 에 등록한 콜백 주소와 글자까지 같아야 한다. 요청 URL 에서 만들어
     * 포트를 바꿔 띄워도 어긋나지 않게 한다.
     */
    private String redirectUri(HttpServletRequest request) {
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

    private static final String LINK_COOKIE = "worklog_oauth_link";

    private Cookie linkCookie(String value, boolean secure) {
        Cookie cookie = new Cookie(LINK_COOKIE, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setPath("/");
        cookie.setMaxAge(value.isEmpty() ? 0 : STATE_TTL_SECONDS);
        return cookie;
    }

    private Long readLinkCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (LINK_COOKIE.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                try {
                    return Long.valueOf(cookie.getValue());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private String randomState() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Cookie stateCookie(String value, int maxAge, boolean secure) {
        Cookie cookie = new Cookie(STATE_COOKIE, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        return cookie;
    }

    private String readStateCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(c -> STATE_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst()
                .orElse(null);
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
