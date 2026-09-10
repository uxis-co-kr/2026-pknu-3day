package com.worklog.admin;

import com.worklog.activity.ActivityRepository;
import com.worklog.admin.dto.PeopleDirectoryResponse;
import com.worklog.auth.User;
import com.worklog.auth.UserRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 콘솔의 직원·계정 현황 (TODO_0910 §1-3).
 */
@Service
public class PeopleDirectoryService {

    private final WapleClient wapleClient;
    private final WapleProperties wapleProperties;
    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;

    public PeopleDirectoryService(
            WapleClient wapleClient,
            WapleProperties wapleProperties,
            UserRepository userRepository,
            ActivityRepository activityRepository) {
        this.wapleClient = wapleClient;
        this.wapleProperties = wapleProperties;
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
    }

    @Transactional(readOnly = true)
    public PeopleDirectoryResponse directory() {
        Map<Long, Long> activityCounts = activityCounts();
        List<User> users = userRepository.findAll();

        // 사원 번호로 계정을 찾을 수 있게 미리 모은다.
        Map<Long, User> byEmpSeq = new HashMap<>();
        users.stream()
                .filter(u -> u.getEmpSeq() != null)
                .forEach(u -> byEmpSeq.put(u.getEmpSeq(), u));

        Long companySeq = wapleProperties.getCompanySeq();
        List<PeopleDirectoryResponse.EmployeeRow> employees = new ArrayList<>();
        if (companySeq != null) {
            for (WapleClient.Employee e : wapleClient.employees(companySeq)) {
                User linked = byEmpSeq.get(e.empSeq());
                employees.add(new PeopleDirectoryResponse.EmployeeRow(
                        e.empSeq(), e.empNm(), linked == null ? null : toRow(linked, activityCounts)));
            }
        }

        // 사원과 잇지 않은 계정 — 관리자가 여기서 짝을 맞춰 준다.
        List<PeopleDirectoryResponse.AccountRow> unlinked = users.stream()
                .filter(u -> u.getEmpSeq() == null)
                .map(u -> toRow(u, activityCounts))
                .sorted((a, b) -> Long.compare(b.activityCount(), a.activityCount()))
                .toList();

        return new PeopleDirectoryResponse(
                wapleClient.isConfigured(), companySeq, employees, unlinked, unclaimed());
    }

    private Map<Long, Long> activityCounts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : activityRepository.countAllByUser()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private List<PeopleDirectoryResponse.ContributorRow> unclaimed() {
        List<PeopleDirectoryResponse.ContributorRow> rows = new ArrayList<>();
        for (Object[] row : activityRepository.countUnclaimedContributors()) {
            rows.add(new PeopleDirectoryResponse.ContributorRow(
                    (String) row[0], (Long) row[1], (OffsetDateTime) row[2]));
        }
        return rows;
    }

    private PeopleDirectoryResponse.AccountRow toRow(User user, Map<Long, Long> counts) {
        return new PeopleDirectoryResponse.AccountRow(
                user.getId(),
                user.getLogin(),
                user.getName(),
                user.getAvatarUrl(),
                user.getRole().name(),
                // 토큰이 있어야 수집기가 그 사람 권한으로 리포를 읽는다.
                user.getGithubTokenEnc() != null,
                user.getEmpSeq(),
                counts.getOrDefault(user.getId(), 0L),
                user.getCreatedAt());
    }
}
