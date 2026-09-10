package com.worklog.auth;

/**
 * 계정 권한 (TODO_0910 §1-3 — 관리자 콘솔 접근 제어).
 */
public enum UserRole {
    MEMBER,
    ADMIN;

    /** SecurityConfig 의 hasAuthority 매칭에 쓰는 권한 이름. */
    public String authority() {
        return "ROLE_" + name();
    }
}
