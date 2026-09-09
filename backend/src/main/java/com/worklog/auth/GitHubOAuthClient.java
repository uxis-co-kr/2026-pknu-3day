package com.worklog.auth;

import com.worklog.config.ApiException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * GitHub OAuth code 교환과 사용자 조회 (PRD F5).
 *
 * <p>Spring Security 의 OAuth2 Client 자동 흐름 대신 수동 교환을 쓴다. 콜백 경로와
 * {@code /auth/done?token=} 리다이렉트 규칙을 PRD 그대로 맞추기 위해서다.
 */
@Component
public class GitHubOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubOAuthClient.class);

    public static final String AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String API_BASE = "https://api.github.com";

    private final RestClient restClient = RestClient.create();

    /** code → access token. GitHub 은 실패해도 200 에 {@code error} 필드를 담아 준다. */
    public String exchangeCode(String code, String clientId, String clientSecret, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", code);
        form.add("redirect_uri", redirectUri);

        Map<?, ?> body = restClient
                .post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(Map.class);

        if (body == null || body.get("access_token") == null) {
            String error = body == null ? "empty response" : String.valueOf(body.get("error_description"));
            log.warn("GitHub 토큰 교환 실패: {}", error);
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "OAUTH_EXCHANGE_FAILED", "GitHub 인증에 실패했습니다: " + error);
        }
        return String.valueOf(body.get("access_token"));
    }

    /** GET /user — 로그인한 사용자 본인 정보. */
    public GitHubUserDto fetchUser(String accessToken) {
        GitHubUserDto user = restClient
                .get()
                .uri(API_BASE + "/user")
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .body(GitHubUserDto.class);

        if (user == null || user.id() == null) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "OAUTH_USER_FETCH_FAILED", "GitHub 사용자 정보를 가져오지 못했습니다.");
        }
        return user;
    }

    /** GET /user 응답 중 쓰는 필드만. */
    public record GitHubUserDto(Long id, String login, String name, String avatar_url) {}
}
