package com.worklog.auth;

import com.worklog.config.ApiException;

/**
 * 조회 API 의 {@code userId} 를 누구 것으로 볼지 한 곳에서 정한다 (BACKLOG2 §2-2, DAY3_plan C-1).
 *
 * <p>담당자 1 이 "일반 로그인은 내 것만 본다" 로 화면을 바꿨지만 서버는 그대로였다 —
 * {@code MEMBER} 토큰으로 {@code ?userId=} 를 바꾸면 남의 일지 본문과 미커밋 diff 까지 나왔다.
 *
 * <p>규칙 하나: <b>{@code MEMBER} 는 자기 것만.</b> {@code userId} 를 생략하면 자기 id 로 채우고,
 * 남의 id 를 명시하면 403 — 조용히 바꾸면 화면 버그를 못 찾는다. {@code ADMIN} 은 지금처럼
 * 아무 값이나 (생략하면 전원).
 */
public final class DataScope {

    private DataScope() {}

    /**
     * @param requested 쿼리 파라미터의 userId (없으면 null)
     * @return 실제로 조회할 userId. ADMIN 이 생략했으면 null (= 전원)
     * @throws ApiException 403 — MEMBER 가 남의 id 를 명시했을 때
     */
    public static Long userIdFor(AuthenticatedUser principal, Long requested) {
        if (principal == null) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다.");
        }
        if (principal.isAdmin()) {
            return requested;
        }
        if (requested != null && !requested.equals(principal.id())) {
            throw ApiException.forbidden("NOT_YOUR_DATA", "본인의 기록만 조회할 수 있습니다.");
        }
        return principal.id();
    }

    /** 단건 조회 — 남의 것이면 있는지 없는지도 알리지 않는다 (404). 주인 없는 것도 MEMBER 에게는 없는 것이다. */
    public static boolean canSee(AuthenticatedUser principal, Long ownerId) {
        if (principal == null) {
            return false;
        }
        if (principal.isAdmin()) {
            return true;
        }
        return ownerId != null && ownerId.equals(principal.id());
    }
}
