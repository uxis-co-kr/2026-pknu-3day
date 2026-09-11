package com.worklog.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.worklog.activity.ActivityRepository;
import com.worklog.auth.User;
import com.worklog.auth.UserRepository;
import com.worklog.auth.UserRole;
import com.worklog.draft.DraftRepository;
import com.worklog.stats.dto.PeopleStatsResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PeopleStatsServiceTest {

    @Test
    @DisplayName("GitHub 을 붙이지 않은 자체 계정(login 이 null)이 있어도 팀원 내역이 나온다")
    void survivesAccountsWithoutGithubLogin() {
        User github = new User();
        github.setId(1L);
        github.setLogin("UngsikJo");
        github.setName("ungsikJo");
        User local = new User();
        local.setId(7L);
        local.setLoginId("9998");
        local.setName("배태일");
        User admin = new User();
        admin.setId(3L);
        admin.setLoginId("admin");
        admin.setName("관리자");

        UserRepository users = mock(UserRepository.class);
        ActivityRepository activities = mock(ActivityRepository.class);
        DraftRepository drafts = mock(DraftRepository.class);
        when(users.findAll()).thenReturn(List.of(local, github, admin));
        when(activities.countByUserAndDateBetween(any(), any())).thenReturn(List.of());
        when(drafts.findLatestBetween(any(), any())).thenReturn(List.of());

        PeopleStatsResponse res = new PeopleStatsService(activities, drafts, users)
                .people(LocalDate.of(2026, 9, 4), LocalDate.of(2026, 9, 10), Granularity.DAY, null);

        assertThat(res.items()).hasSize(3);
        // 자체 계정은 사원번호·아이디를 로그인 자리에 쓴다 — 빈 이름이 화면에 뜨지 않게.
        assertThat(res.items()).extracting(i -> i.user().login()).containsExactly("9998", "UngsikJo", "admin");
    }

    /**
     * 콘솔을 열려고 둔 관리자 계정은 사원도 아니고 GitHub 도 붙어 있지 않아 늘 0 인 줄로만
     * 남는다. "누가 무엇을 했나" 를 보는 화면에 팀원인 척 서 있을 이유가 없다 (9/11).
     */
    @Test
    @DisplayName("사원 정보도 GitHub 도 없는 관리자 계정은 팀원 내역에서 뺀다")
    void hidesServiceAdminAccount() {
        User member = new User();
        member.setId(7L);
        member.setLoginId("9998");
        member.setName("배태일");
        member.setEmpSeq(9998L);

        User serviceAdmin = new User();
        serviceAdmin.setId(3L);
        serviceAdmin.setLoginId("admin");
        serviceAdmin.setName("관리자");
        serviceAdmin.setRole(UserRole.ADMIN);

        // 관리자를 겸하는 팀원은 사람이다. 그대로 남아야 한다.
        User adminEmployee = new User();
        adminEmployee.setId(9L);
        adminEmployee.setLoginId("0139");
        adminEmployee.setName("이상래");
        adminEmployee.setEmpSeq(139L);
        adminEmployee.setRole(UserRole.ADMIN);

        UserRepository users = mock(UserRepository.class);
        ActivityRepository activities = mock(ActivityRepository.class);
        DraftRepository drafts = mock(DraftRepository.class);
        when(users.findAll()).thenReturn(List.of(member, serviceAdmin, adminEmployee));
        when(activities.countByUserAndDateBetween(any(), any())).thenReturn(List.of());
        when(drafts.findLatestBetween(any(), any())).thenReturn(List.of());

        PeopleStatsResponse res = new PeopleStatsService(activities, drafts, users)
                .people(LocalDate.of(2026, 9, 4), LocalDate.of(2026, 9, 10), Granularity.DAY, null);

        assertThat(res.items()).extracting(i -> i.user().login()).containsExactlyInAnyOrder("9998", "0139");
    }
}
