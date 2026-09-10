package com.worklog.auth;

/**
 * SecurityContext 의 principal. 컨트롤러는 {@code @AuthenticationPrincipal AuthenticatedUser} 로 받는다.
 *
 * <p>User 엔티티를 그대로 싣지 않는 이유: 필터가 세션 밖에서 만들기 때문에 지연 로딩이 걸리고,
 * 요청 처리 중 엔티티가 detached 상태로 떠돌게 된다. 필요한 최소 정보만 담는다.
 */
public record AuthenticatedUser(Long id, String login, AuthMethod authMethod, UserRole role) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
