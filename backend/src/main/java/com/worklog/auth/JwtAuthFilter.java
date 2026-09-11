package com.worklog.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code Authorization: Bearer <jwt>} 를 검증해 SecurityContext 를 채운다 (PRD F5).
 *
 * <p>토큰이 없거나 틀려도 여기서 401 을 내지 않는다. 인증 없이 통과시키면 뒤의 인가 규칙이
 * 401/403 을 판단한다 — 무인증으로 열린 경로(/health)를 막지 않기 위해서다.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null
                && header.startsWith(PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(PREFIX.length()).trim();
            try {
                JwtService.Verified verified = jwtService.verifyDetailed(token);
                AuthenticatedUser fromToken = verified.user();
                // 권한은 토큰이 아니라 DB 의 지금 값이다. 토큰에 적힌 권한을 믿으면, 관리자였을 때
                // 받은 토큰이 만료될 때까지 계속 관리자로 통한다. 계정이 지워졌으면 토큰도 죽는다.
                // 비밀번호를 바꾼 뒤에는 그 전에 받은 토큰도 죽는다 (V11 password_changed_at).
                AuthenticatedUser user = userRepository
                        .findById(fromToken.id())
                        .filter(u -> {
                            boolean fresh = JwtService.matchesPasswordVersion(
                                    verified.passwordVersion(), u.getPasswordChangedAt());
                            if (!fresh) {
                                log.debug("사용자 {} 의 토큰이 비밀번호 변경 전 것이라 거절한다.", u.getId());
                            }
                            return fresh;
                        })
                        .map(u -> new AuthenticatedUser(
                                u.getId(), fromToken.login(), AuthMethod.JWT,
                                u.getRole() == null ? UserRole.MEMBER : u.getRole()))
                        .orElse(null);
                if (user == null) {
                    log.debug("토큰의 사용자 {} 가 없다.", fromToken.id());
                    chain.doFilter(request, response);
                    return;
                }
                var auth = new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        List.of(
                                new SimpleGrantedAuthority(AuthMethod.JWT.authority()),
                                new SimpleGrantedAuthority(user.role().authority())));
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException e) {
                log.debug("JWT 검증 실패: {}", e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
