package com.worklog.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 관리자 콘솔 — 회사 직원 목록과 GitHub 활성화 상태 (TODO_0910 §1-3).
 *
 * <p>세 갈래를 한 화면에 모은다.
 * <ul>
 *   <li>{@code employees} — 사내 회원(와플) 사원. 우리 계정과 이어졌는지 보여 준다
 *   <li>{@code unlinkedAccounts} — 로그인은 했지만 사원과 잇지 않은 계정
 *   <li>{@code unclaimedContributors} — 커밋은 있는데 로그인한 적이 없는 GitHub 계정 (BACKLOG §3-4)
 * </ul>
 *
 * @param wapleConfigured 와플 API 설정 여부. false 면 employees 가 비고 화면이 안내를 띄운다
 */
public record PeopleDirectoryResponse(
        boolean wapleConfigured,
        Long companySeq,
        List<EmployeeRow> employees,
        List<AccountRow> unlinkedAccounts,
        List<ContributorRow> unclaimedContributors) {

    /**
     * @param account 이 사원과 이어진 우리 계정. 없으면 null — 아직 서비스를 쓰지 않는 사람이다
     */
    public record EmployeeRow(Long empSeq, String empNm, AccountRow account) {}

    /**
     * @param githubLinked GitHub 활성화 상태. 토큰이 있어야 수집기가 그 사람 리포를 읽는다
     * @param activityCount 수집된 활동 수 — 실제로 쓰고 있는지 판단하는 값
     */
    public record AccountRow(
            Long userId,
            String login,
            String name,
            String avatarUrl,
            String role,
            boolean githubLinked,
            Long empSeq,
            long activityCount,
            OffsetDateTime joinedAt) {}

    /**
     * 커밋 author 로만 남은 GitHub 계정 (PRD F1-5).
     *
     * <p>이 사람들의 활동은 총계에는 들어가지만 어느 사용자 카드에도 속하지 않는다.
     * 관리자가 여기서 누가 빠져 있는지 본다.
     */
    public record ContributorRow(String externalLogin, long activityCount, OffsetDateTime lastSeenAt) {}
}
