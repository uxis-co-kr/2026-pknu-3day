package com.worklog.auth;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * "이 화면 주소로 돌려보내도 되는가" 를 한 곳에서 판단한다 (BACKLOG2 §2-1 A+B).
 *
 * <p>사람마다 화면 주소가 다르다 — 나는 {@code 192.168.1.224:5173}, 옆자리는 {@code .218}.
 * OAuth 콜백 뒤에 어디로 돌아갈지를 요청의 {@code Origin} 에서 정하면 아무 설정 없이 각자
 * 자기 화면으로 돌아온다. 그런데 아무 주소로나 돌려보내면 열린 리다이렉트가 된다. 그래서
 * <b>사내망 대역만</b> 통과시킨다.
 *
 * <p>기본 허용: localhost · 127.0.0.1 · ::1 · 10/8 · 172.16/12 · 192.168/16. 여기에
 * {@code WORKLOG_ALLOWED_ORIGINS} (쉼표 구분, 호스트 이름 또는 CIDR) 를 더할 수 있다.
 * 포트는 보지 않는다 — vite 5173 도 백엔드 8080 도 올 수 있다. 스킴은 http·https 만.
 */
@Component
public class OriginPolicy {

    private static final List<Cidr> PRIVATE = List.of(
            Cidr.parse("10.0.0.0/8"),
            Cidr.parse("172.16.0.0/12"),
            Cidr.parse("192.168.0.0/16"),
            Cidr.parse("127.0.0.0/8"));

    private final List<String> hosts = new ArrayList<>();
    private final List<Cidr> ranges = new ArrayList<>();

    @Autowired
    public OriginPolicy(@Value("${worklog.allowed-origins:}") String extra) {
        this(extra == null ? List.of() : Arrays.asList(extra.split(",")));
    }

    OriginPolicy(List<String> extra) {
        for (String raw : extra) {
            String item = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            if (item.isEmpty()) {
                continue;
            }
            // "http://host:port" 로 적어도 호스트만 본다.
            if (item.contains("://")) {
                try {
                    String host = URI.create(item).getHost();
                    if (host != null) {
                        item = host.toLowerCase(Locale.ROOT);
                    }
                } catch (IllegalArgumentException ignored) {
                    // 그대로 호스트 이름으로 취급
                }
            }
            if (item.contains("/")) {
                Cidr cidr = Cidr.tryParse(item);
                if (cidr != null) {
                    ranges.add(cidr);
                }
            } else {
                hosts.add(item);
            }
        }
    }

    /**
     * 허용되는 주소면 {@code scheme://host[:port]} 로 다듬어 돌려준다. 아니면 비어 있다.
     * 경로·쿼리는 버린다 — 돌아갈 곳은 화면의 루트이고, 경로는 서버가 붙인다.
     */
    public Optional<String> normalize(String origin) {
        if (origin == null || origin.isBlank()) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = URI.create(origin.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null) {
            return Optional.empty();
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return Optional.empty();
        }
        if (!isAllowedHost(host.toLowerCase(Locale.ROOT))) {
            return Optional.empty();
        }
        String port = uri.getPort() < 0 ? "" : ":" + uri.getPort();
        return Optional.of(scheme + "://" + host + port);
    }

    public boolean isAllowed(String origin) {
        return normalize(origin).isPresent();
    }

    boolean isAllowedHost(String host) {
        // URI 는 IPv6 를 대괄호째 준다.
        String bare = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        if (bare.equals("localhost") || bare.equals("::1")) {
            return true;
        }
        if (hosts.contains(bare)) {
            return true;
        }
        byte[] address = literalIpv4(bare);
        if (address == null) {
            // 이름으로 적힌 사내 호스트는 목록에 있어야만 통과한다. DNS 조회는 하지 않는다 —
            // 공격자가 자기 이름을 사내 IP 로 가리키게 할 수 있다.
            return false;
        }
        for (Cidr cidr : PRIVATE) {
            if (cidr.contains(address)) {
                return true;
            }
        }
        for (Cidr cidr : ranges) {
            if (cidr.contains(address)) {
                return true;
            }
        }
        return false;
    }

    /** 숫자 네 마디일 때만 IPv4 로 본다. 그 외는 DNS 조회 없이 null. */
    private static byte[] literalIpv4(String host) {
        if (!host.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            return null;
        }
        try {
            return InetAddress.getByName(host).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /** IPv4 CIDR 하나. */
    record Cidr(int network, int mask) {

        static Cidr parse(String text) {
            Cidr cidr = tryParse(text);
            if (cidr == null) {
                throw new IllegalArgumentException("CIDR 형식이 아니다: " + text);
            }
            return cidr;
        }

        static Cidr tryParse(String text) {
            int slash = text.indexOf('/');
            if (slash < 0) {
                return null;
            }
            byte[] address = literalIpv4(text.substring(0, slash));
            if (address == null) {
                return null;
            }
            int bits;
            try {
                bits = Integer.parseInt(text.substring(slash + 1));
            } catch (NumberFormatException e) {
                return null;
            }
            if (bits < 0 || bits > 32) {
                return null;
            }
            int mask = bits == 0 ? 0 : (int) (0xFFFFFFFFL << (32 - bits));
            return new Cidr(toInt(address) & mask, mask);
        }

        boolean contains(byte[] address) {
            return address.length == 4 && (toInt(address) & mask) == network;
        }

        private static int toInt(byte[] b) {
            return ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
        }
    }
}
