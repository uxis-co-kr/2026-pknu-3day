package com.worklog.auth;

/**
 * 요청이 어떤 자격으로 인증됐는지 (PRD 7).
 *
 * <p>{@code POST /vscode/sessions} 와 {@code /external/**} 는 API Key 로만 열려 있어야 해서
 * SecurityConfig 가 이 값을 권한 문자열로 받아 경로별로 구분한다.
 */
public enum AuthMethod {
    JWT,
    API_KEY;

    /** SecurityConfig 의 hasAuthority 매칭에 쓰는 권한 이름. */
    public String authority() {
        return "AUTH_" + name();
    }
}
