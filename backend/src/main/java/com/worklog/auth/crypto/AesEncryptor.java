package com.worklog.auth.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * GitHub access token 등 비밀값의 대칭 암호화 (PRD F5, 11 — 평문 저장 금지).
 *
 * <p>AES-256-GCM. 출력은 {@code base64(IV 12B || ciphertext || tag 16B)} 한 덩어리라
 * 컬럼 하나에 그대로 넣는다. IV 는 호출마다 새로 뽑으므로 같은 평문도 매번 다른 암호문이 된다.
 */
@Component
public class AesEncryptor {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesEncryptor(@Value("${worklog.encryption.key}") String base64Key) {
        byte[] decoded = decodeKey(base64Key);
        if (decoded.length != 32) {
            throw new IllegalStateException(
                    "ENCRYPTION_KEY 는 base64 로 인코딩된 32바이트(AES-256)여야 한다. 현재 %d바이트."
                            .formatted(decoded.length));
        }
        this.key = new SecretKeySpec(decoded, "AES");
    }

    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(cipherText, 0, out, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("암호화에 실패했다.", e);
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            if (all.length <= IV_LENGTH) {
                throw new IllegalArgumentException("암호문이 너무 짧다.");
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(all, 0, iv, 0, IV_LENGTH);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("복호화에 실패했다. ENCRYPTION_KEY 가 바뀌었는지 확인한다.", e);
        }
    }

    /** base64 표준/URL-safe 를 모두 받아준다 — openssl rand -base64 32 결과를 그대로 붙일 수 있게. */
    private static byte[] decodeKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("ENCRYPTION_KEY 가 비어 있다. backend/.env 를 확인한다.");
        }
        String trimmed = value.trim();
        try {
            return Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException e) {
            return Base64.getUrlDecoder().decode(trimmed);
        }
    }
}
