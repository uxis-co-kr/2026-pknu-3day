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
    /** 발급 시점의 비밀번호 버전 (password_changed_at, epoch ms). 바뀌면 이 토큰은 죽는다 (V10). */
    private static final String PASSWORD_VERSION_CLAIM = "pwv";

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
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .subject(String.valueOf(user.getId()))
                // 자체 계정은 GitHub login 이 없다. 화면에 보일 이름으로 login_id 를 대신 쓴다.
                .claim(LOGIN_CLAIM, displayLoginOf(user))
                .claim(ROLE_CLAIM, user.getRole().name());
        Long pwv = passwordVersionOf(user.getPasswordChangedAt());
        if (pwv != null) {
            claims.claim(PASSWORD_VERSION_CLAIM, pwv);
        }
        JwtClaimsSet built = claims.build();
        return encoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), built))
                .getTokenValue();
    }

    /**
     * 서명·만료를 검증하고 principal 을 만든다.
     *
     * @throws JwtException 서명이 틀렸거나 만료됐을 때
     */
    public AuthenticatedUser verify(String token) {
        return verifyDetailed(token).user();
    }

    /**
     * {@link #verify(String)} 에 발급 시각을 더한 것. 필터가 비밀번호 변경 시각과 견준다 —
     * 로그아웃이 클라이언트에서 버리는 것뿐이라 유출된 토큰을 죽일 방법이 이것 하나다 (V10).
     */
    public Verified verifyDetailed(String token) {
        Jwt jwt = decoder.decode(token);
        AuthenticatedUser user = new AuthenticatedUser(
                Long.valueOf(jwt.getSubject()),
                jwt.getClaimAsString(LOGIN_CLAIM),
                AuthMethod.JWT,
                // role 클레임이 없는 예전 토큰은 일반 회원으로 본다.
                parseRole(jwt.getClaimAsString(ROLE_CLAIM)));
        Long pwv = jwt.hasClaim(PASSWORD_VERSION_CLAIM) ? jwt.getClaim(PASSWORD_VERSION_CLAIM) : null;
        return new Verified(user, jwt.getIssuedAt(), pwv == null ? null : pwv.longValue());
    }

    /**
     * 토큰이 지금 비밀번호로 발급된 것인가.
     *
     * <p>iat 와 변경 시각을 견주면 같은 초 안의 구분이 안 된다 — 로그인 직후 초기화하면 옛 토큰이
     * 살아남는다. 그래서 발급할 때 <b>비밀번호 버전(변경 시각 ms)</b> 을 토큰에 적고 지금 값과
     * 똑같은지 본다. 바꾼 적이 없으면 둘 다 비어 있어 통한다.
     */
    public static boolean matchesPasswordVersion(Long tokenVersion, java.time.OffsetDateTime changedAt) {
        Long current = passwordVersionOf(changedAt);
        if (current == null) {
            return true; // 바꾼 적이 없다 — 옛 토큰도 그대로
        }
        return current.equals(tokenVersion);
    }

    /** DB 는 마이크로초, 메모리는 나노초일 수 있다. 밀리초로 맞춰 견준다. */
    private static Long passwordVersionOf(java.time.OffsetDateTime changedAt) {
        return changedAt == null ? null : changedAt.toInstant().toEpochMilli();
    }

    /** @param passwordVersion 토큰의 pwv 클레임. 비밀번호를 바꾼 적 없는 계정의 토큰에는 없다 */
    public record Verified(AuthenticatedUser user, Instant issuedAt, Long passwordVersion) {}

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
