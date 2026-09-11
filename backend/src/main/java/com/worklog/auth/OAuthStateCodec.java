package com.worklog.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * OAuth {@code state} 를 서명해 만든다 — 쿠키 없이 검증한다.
 *
 * <p>처음엔 state 를 쿠키에 두고 콜백에서 견줬다. 그런데 화면은 {@code 192.168.x.x:5173} 으로
 * 열리고 콜백은 OAuth App 에 등록한 {@code localhost:8080} 으로 오므로, 쿠키가 다른 호스트에
 * 심겨 콜백에 따라오지 않았다. 브라우저가 쿠키를 막는 환경도 있다. state 안에 nonce·만료·
 * 연결할 사용자를 넣고 서명하면, 콜백이 어느 호스트로 오든 위조·재사용만 막으면 된다.
 *
 * <p>형식: {@code base64url(nonce|expEpochSec|linkUserId|returnTo).base64url(hmacSha256)}
 *
 * <p>{@code returnTo} 는 콜백 뒤 돌아갈 화면 주소다 (BACKLOG2 §2-1). 서명 안에 있으니 바꿔치기가
 * 안 되고, 넣을 때 {@link OriginPolicy} 가 사내망 대역만 통과시킨다 — 둘 다 있어야 열린
 * 리다이렉트가 되지 않는다.
 */
@Component
public class OAuthStateCodec {

    private static final int TTL_SECONDS = 300;
    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();

    private final SecretKeySpec key;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public OAuthStateCodec(@Value("${worklog.jwt.secret}") String secret) {
        this(secret, Clock.systemUTC());
    }

    OAuthStateCodec(String secret, Clock clock) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET 이 비어 있다. backend/.env 를 확인한다.");
        }
        try {
            // JWT 와 같은 비밀에서 파생하되 다른 키를 쓴다 — 한쪽 서명이 다른 쪽에 통하지 않게.
            byte[] derived = MessageDigest.getInstance("SHA-256")
                    .digest(("oauth-state:" + secret.trim()).getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(derived, "HmacSHA256");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        this.clock = clock;
    }

    /** @param linkUserId 이미 로그인한 계정에 GitHub 을 붙이는 경우 그 사용자. 로그인이면 null */
    public String issue(Long linkUserId) {
        return issue(linkUserId, null);
    }

    /**
     * @param linkUserId 이미 로그인한 계정에 GitHub 을 붙이는 경우 그 사용자. 로그인이면 null
     * @param returnTo 콜백 뒤 돌아갈 화면 주소 ({@code scheme://host[:port]}). 없으면 서버 설정값으로 간다
     */
    public String issue(Long linkUserId, String returnTo) {
        byte[] nonce = new byte[18];
        random.nextBytes(nonce);
        String payload = ENC.encodeToString(nonce)
                + "|" + clock.instant().plusSeconds(TTL_SECONDS).getEpochSecond()
                + "|" + (linkUserId == null ? "" : linkUserId)
                + "|" + (returnTo == null ? "" : ENC.encodeToString(returnTo.getBytes(StandardCharsets.UTF_8)));
        String body = ENC.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return body + "." + sign(body);
    }

    /**
     * 검증. 서명이 다르거나 만료됐으면 {@link Optional#empty()}.
     *
     * @return 붙일 사용자 id 를 담은 결과 (로그인이면 {@code linkUserId} 가 null)
     */
    public Optional<Parsed> verify(String state) {
        if (state == null) {
            return Optional.empty();
        }
        int dot = state.lastIndexOf('.');
        if (dot <= 0) {
            return Optional.empty();
        }
        String body = state.substring(0, dot);
        String sig = state.substring(dot + 1);
        if (!MessageDigest.isEqual(
                sign(body).getBytes(StandardCharsets.UTF_8), sig.getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }
        String[] parts;
        try {
            parts = new String(DEC.decode(body), StandardCharsets.UTF_8).split("\\|", -1);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (parts.length != 4) {
            return Optional.empty();
        }
        long exp;
        try {
            exp = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (Instant.ofEpochSecond(exp).isBefore(clock.instant())) {
            return Optional.empty();
        }
        Long link = null;
        if (!parts[2].isEmpty()) {
            try {
                link = Long.valueOf(parts[2]);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        String returnTo = null;
        if (!parts[3].isEmpty()) {
            try {
                returnTo = new String(DEC.decode(parts[3]), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
        return Optional.of(new Parsed(link, returnTo));
    }

    private String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return ENC.encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * @param linkUserId 붙일 사용자 (로그인이면 null)
     * @param returnTo 돌아갈 화면 주소 (없으면 null → 서버 설정 {@code FRONTEND_URL})
     */
    public record Parsed(Long linkUserId, String returnTo) {}
}
