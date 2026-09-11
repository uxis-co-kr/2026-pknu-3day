package com.worklog.config;

import java.net.URI;

/**
 * 사내망 주소인지 가린다 (BACKLOG2 §2-1, §2-2).
 *
 * <p>사람마다 접속 주소가 다르다 — 나는 {@code 192.168.1.224}, 친구는 {@code ...218}. 그래서
 * <b>요청이 알려 준 주소</b>를 그대로 쓰는 자리가 둘 있다. CORS 허용 주소({@link SecurityConfig})
 * 와 OAuth 를 마치고 돌아갈 주소다. 아무 주소나 믿으면 열린 리다이렉트가 되므로 사설 대역과
 * localhost 만 통과시킨다.
 *
 * <p>두 자리가 <b>같은 자를 써야 한다</b>. 한쪽만 느슨하면 그쪽으로 들어온다.
 */
public final class InternalNetwork {

    private InternalNetwork() {}

    /**
     * {@code http://192.168.1.224:5173} 같은 Origin 헤더 값이 사내망에서 온 것인지.
     *
     * @param origin 브라우저가 실어 보낸 Origin. 없으면(같은 출처 GET 등) false
     */
    public static boolean isInternalOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            return false;
        }
        try {
            return isPrivateHost(URI.create(origin.trim()).getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * localhost 와 사설 IP 대역(10./172.16-31./192.168.) 만.
     *
     * <p>앞자리만 견주면 {@code 192.168.1.224.evil.com} 같은 주소가 통과한다 — 남이 그 도메인을
     * 잡아 두면 그리로 열린다. 그래서 <b>네 칸짜리 숫자 주소인지</b>부터 확인한다. 와일드카드
     * 패턴({@code http://192.168.*})으로는 이 구분을 할 수 없다.
     */
    public static boolean isPrivateHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        if ("localhost".equalsIgnoreCase(host) || "[::1]".equals(host) || "::1".equals(host)) {
            return true;
        }
        int[] octets = parseIpv4(host);
        if (octets == null) {
            return false;
        }
        if (octets[0] == 127 || octets[0] == 10) {
            return true;
        }
        if (octets[0] == 192 && octets[1] == 168) {
            return true;
        }
        return octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31;
    }

    /** 점 넷으로 나뉜 0~255 네 칸이어야 한다. 아니면 null. */
    private static int[] parseIpv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            if (parts[i].isEmpty() || parts[i].length() > 3) {
                return null;
            }
            for (int k = 0; k < parts[i].length(); k++) {
                if (!Character.isDigit(parts[i].charAt(k))) {
                    return null;
                }
            }
            octets[i] = Integer.parseInt(parts[i]);
            if (octets[i] > 255) {
                return null;
            }
        }
        return octets;
    }
}
