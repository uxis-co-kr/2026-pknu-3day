package com.worklog.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

/**
 * 웹 로그인용 JWT 발급·검증 (PRD F5 — HS256, 12시간).
 *
 * <p>oauth2-client 스타터에 딸려오는 spring-security-oauth2-jose(Nimbus)를 쓰므로
 * JWT 라이브러리를 따로 추가하지 않는다. sub 에 userId, login 클레임에 GitHub 로그인을 담는다.
 */
@Service
public class JwtService {

    private static final String ISSUER = "worklog";
    private static final String LOGIN_CLAIM = "login";
    private static final String ROLE_CLAIM = "role";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public JwtService(
            @Value("${worklog.jwt.secret}") String secret,
            @Value("${worklog.jwt.ttl-hours}") long ttlHours) {
        this(secret, ttlHours, Clock.systemUTC());
    }

    /** 만료 동작을 검증할 수 있게 시계를 주입받는 생성자. */
    JwtService(String secret, long ttlHours, Clock clock) {
        SecretKeySpec key = new SecretKeySpec(decodeSecret(secret), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        this.ttl = Duration.ofHours(ttlHours);
        this.clock = clock;
    }

    public String issue(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .subject(String.valueOf(user.getId()))
                // 자체 계정은 GitHub login 이 없다. 화면에 보일 이름으로 login_id 를 대신 쓴다.
                .claim(LOGIN_CLAIM, displayLoginOf(user))
                .claim(ROLE_CLAIM, user.getRole().name())
                .build();
        return encoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    /**
     * 서명·만료를 검증하고 principal 을 만든다.
     *
     * @throws JwtException 서명이 틀렸거나 만료됐을 때
     */
    public AuthenticatedUser verify(String token) {
        Jwt jwt = decoder.decode(token);
        return new AuthenticatedUser(
                Long.valueOf(jwt.getSubject()),
                jwt.getClaimAsString(LOGIN_CLAIM),
                AuthMethod.JWT,
                // role 클레임이 없는 예전 토큰은 일반 회원으로 본다.
                parseRole(jwt.getClaimAsString(ROLE_CLAIM)));
    }

    /** 표시용 이름 — GitHub 로그인이 없으면 자체 아이디를 쓴다. 둘 다 없을 수는 없다 (V5 제약). */
    private static String displayLoginOf(User user) {
        if (user.getLogin() != null && !user.getLogin().isBlank()) {
            return user.getLogin();
        }
        return user.getLoginId() != null ? user.getLoginId() : String.valueOf(user.getId());
    }

    private static UserRole parseRole(String value) {
        if (value == null) {
            return UserRole.MEMBER;
        }
        try {
            return UserRole.valueOf(value);
        } catch (IllegalArgumentException e) {
            return UserRole.MEMBER;
        }
    }

    /**
     * HS256 키는 32바이트 이상이어야 한다. base64 로 디코딩되면 그 바이트를, 아니면 UTF-8 바이트를 쓴다
     * (openssl rand -base64 48 결과를 그대로 붙여도, 아무 문자열을 넣어도 동작하게).
     */
    private static byte[] decodeSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET 이 비어 있다. backend/.env 를 확인한다.");
        }
        String trimmed = secret.trim();
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException e) {
            bytes = trimmed.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) {
            bytes = trimmed.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET 은 32바이트 이상이어야 한다 (openssl rand -base64 48).");
        }
        return bytes;
    }
}
