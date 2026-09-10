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
 * <p>형식: {@code base64url(nonce|expEpochSec|linkUserId|returnOrigin).base64url(hmacSha256)}
 *
 * <p>{@code returnOrigin} 은 연동을 시작한 화면의 주소다. 사람마다 접속 주소가 달라
 * ({@code 192.168.1.224:5173} vs {@code ...218:5173}) 설정에 적힌 한 곳으로만 돌려보내면
 * 남의 화면으로 튕긴다. 시작할 때 받아 서명에 넣어 두고 콜백에서 꺼내 쓴다 — 서명돼 있으니
 * 중간에 바꿔 열린 리다이렉트로 만들 수 없다.
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
     * @param returnOrigin 끝나고 돌아갈 화면 주소({@code http://192.168.1.224:5173}).
     *     null 이면 설정의 {@code FRONTEND_URL} 을 쓴다
     */
    public String issue(Long linkUserId, String returnOrigin) {
        byte[] nonce = new byte[18];
        random.nextBytes(nonce);
        String payload = ENC.encodeToString(nonce)
                + "|" + clock.instant().plusSeconds(TTL_SECONDS).getEpochSecond()
                + "|" + (linkUserId == null ? "" : linkUserId)
                // 주소에 | 가 들어갈 일은 없지만, 들어오면 칸이 밀려 파싱이 어긋난다.
                + "|" + (returnOrigin == null ? "" : returnOrigin.replace("|", ""));
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
        // 예전 형식(칸 셋)도 받는다 — 이미 나간 state 가 만료되기 전까지 살아 있다.
        if (parts.length < 3 || parts.length > 4) {
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
        String returnOrigin = parts.length == 4 && !parts[3].isEmpty() ? parts[3] : null;
        return Optional.of(new Parsed(link, returnOrigin));
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

    public record Parsed(Long linkUserId, String returnOrigin) {}
}
