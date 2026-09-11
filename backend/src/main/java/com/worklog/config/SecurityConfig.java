package com.worklog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worklog.auth.ApiKeyAuthFilter;
import com.worklog.auth.AuthMethod;
import com.worklog.auth.UserRole;
import com.worklog.auth.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * 인증 규칙 (PRD F5, 7).
 *
 * <p>servlet context-path 가 {@code /api} 이므로 아래 매처는 prefix 없이 {@code /me} 처럼 쓴다.
 *
 * <p>인증 수단은 두 가지다. ★ 표시 엔드포인트는 JWT / API Key 둘 다 받고,
 * VS Code 확장과 외부 연동 경로는 API Key 로만 연다. 필터가 부여한 권한
 * (AUTH_JWT / AUTH_API_KEY)으로 구분하므로 컨트롤러 쪽에는 아무 설정이 필요 없다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper;
    private final String frontendUrl;
    private final JwtAuthFilter jwtAuthFilter;
    private final ApiKeyAuthFilter apiKeyAuthFilter;

    public SecurityConfig(
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Value("${worklog.frontend-url}")
                    String frontendUrl,
            JwtAuthFilter jwtAuthFilter,
            ApiKeyAuthFilter apiKeyAuthFilter) {
        this.objectMapper = objectMapper;
        this.frontendUrl = frontendUrl;
        this.jwtAuthFilter = jwtAuthFilter;
        this.apiKeyAuthFilter = apiKeyAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .logout(l -> l.disable())
                .authorizeHttpRequests(auth -> auth.requestMatchers("/health", "/auth/**")
                        .permitAll()
                        // Mattermost 가 부른다. JWT 대신 웹훅 token 으로 확인한다 (MattermostChatController).
                        .requestMatchers(HttpMethod.POST, "/chat/mattermost")
                        .permitAll()
                        // 확장이 보고하는 경로와 외부 연동은 개인 키로만 열린다 (PRD 7).
                        // 같은 /vscode/sessions 라도 대시보드가 쓰는 GET 은 ★ 라 아래 규칙을 탄다.
                        .requestMatchers(HttpMethod.POST, "/vscode/sessions")
                        .hasAuthority(AuthMethod.API_KEY.authority())
                        .requestMatchers("/external/**")
                        .hasAuthority(AuthMethod.API_KEY.authority())
                        // 관리자 콘솔 (TODO_0910 §1-3). 로그인만으로는 들어갈 수 없다.
                        .requestMatchers("/admin/**")
                        .hasAuthority(UserRole.ADMIN.authority())
                        // Mattermost 웹훅과 LLM 모델은 관리자만 바꾼다 (9/10 결정).
                        // 화면에서 뺐더라도 API 를 직접 부르면 그만이므로 서버에서도 막는다.
                        .requestMatchers("/settings/notify", "/settings/notify/**")
                        .hasAuthority(UserRole.ADMIN.authority())
                        .requestMatchers("/settings/llm", "/settings/llm/**")
                        .hasAuthority(UserRole.ADMIN.authority())
                        .anyRequest()
                        .hasAnyAuthority(AuthMethod.JWT.authority(), AuthMethod.API_KEY.authority()))
                .exceptionHandling(e -> e.authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 프론트(Vite dev server)에서 Bearer 토큰으로 호출할 수 있게 열어둔다.
     *
     * <p>Vite 프록시를 타면 CORS 를 안 거치지만, 프록시 없이 :8080 을 직접 부르는 사람이
     * 생겨도 막히지 않게 사내망 대역을 연다. 사람마다 접속 주소가 다르다 (BACKLOG2 §2-1).
     *
     * <p>대역을 <b>와일드카드 패턴으로</b> 열지는 않는다. 스프링은 패턴의 {@code *} 를 점까지
     * 포함해 풀어서, {@code http://192.168.*} 이 {@code 192.168.1.224.evil.com} 까지 통과시킨다 —
     * 남이 그런 이름을 잡아 두면 사내망인 척 들어온다. 지금은 인증이 Bearer 토큰이라 그것만으로
     * 새지는 않지만, 쿠키를 쓰는 자리가 하나라도 생기면 그때는 구멍이다 (§2-2).
     *
     * <p>그래서 요청이 알려 준 Origin 을 {@link InternalNetwork} 로 가려 <b>네 칸짜리 숫자
     * 주소인지</b>까지 확인하고, 통과하면 그 주소 하나만 허용한다. OAuth 를 마치고 돌아갈
     * 주소를 고를 때와 같은 자다 — 한쪽만 느슨하면 그쪽으로 들어온다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return request -> {
            String origin = request.getHeader(HttpHeaders.ORIGIN);
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(
                    java.util.List.of(InternalNetwork.isInternalOrigin(origin) ? origin : frontendUrl));
            config.setAllowedMethods(java.util.List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(java.util.List.of("*"));
            config.setAllowCredentials(true);
            return config;
        };
    }

    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, ex) ->
                write(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다.");
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) ->
                write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "이 리소스에 접근할 권한이 없습니다.");
    }

    private void write(HttpServletResponse response, HttpStatus status, String code, String message)
            throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), new ApiError(code, message));
    }
}
