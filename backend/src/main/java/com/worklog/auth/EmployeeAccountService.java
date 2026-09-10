package com.worklog.auth;

import com.worklog.admin.WapleClient;
import com.worklog.admin.WapleProperties;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사원 번호로 처음 로그인할 때 계정을 만든다 (TODO_0910 §1-1).
 *
 * <p>로그인 화면이 "처음 로그인한다면 비밀번호는 사원번호와 같습니다" 라고 안내한다. 회원을
 * 우리가 따로 등록하지 않고 <b>사내 회원 조회 API 에 있는 사람이면 들어올 수 있게</b> 한다는
 * 뜻이다. 계정은 그 첫 로그인 때 만들어진다.
 *
 * <p><b>이것은 본인 확인이 아니다.</b> 사원 번호를 아는 사람이면 누구든 그 사람으로 처음
 * 들어올 수 있다 (TODO_0910 §1-1 이 지적한 그대로다). 사원 번호는 비밀이 아니기 때문이다.
 * 회의에서 정한 임시 흐름이라 그대로 구현하되, 최초 비밀번호는 반드시 바꾸게 하고
 * 비밀번호를 한 번 바꾼 계정은 이 경로로 다시 들어올 수 없다.
 */
@Service
public class EmployeeAccountService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeAccountService.class);

    private final UserRepository userRepository;
    private final WapleClient wapleClient;
    private final WapleProperties wapleProperties;

    public EmployeeAccountService(
            UserRepository userRepository, WapleClient wapleClient, WapleProperties wapleProperties) {
        this.userRepository = userRepository;
        this.wapleClient = wapleClient;
        this.wapleProperties = wapleProperties;
    }

    /**
     * 사원 번호로 첫 로그인을 시도한다.
     *
     * @return 계정을 만들었으면 그 계정. 사원 번호가 아니거나 비밀번호가 규칙과 다르면 empty
     */
    @Transactional
    public Optional<User> provisionOnFirstLogin(String loginId, String password) {
        // 최초 비밀번호는 사원 번호와 같다 (로그인 화면 안내).
        if (!loginId.equals(password)) {
            return Optional.empty();
        }
        long empSeq;
        try {
            empSeq = Long.parseLong(loginId);
        } catch (NumberFormatException e) {
            return Optional.empty(); // 사원 번호가 아닌 아이디는 여기서 만들지 않는다
        }

        Long companySeq = wapleProperties.getCompanySeq();
        Optional<WapleClient.Employee> employee =
                wapleClient.employees(companySeq == null ? 0L : companySeq).stream()
                        .filter(e -> e.empSeq() != null && e.empSeq() == empSeq)
                        .findFirst();
        if (employee.isEmpty()) {
            log.info("사원 번호 {} 가 회원 목록에 없어 계정을 만들지 않는다.", loginId);
            return Optional.empty();
        }

        // 관리자가 이미 이 사원을 어떤 계정(예: GitHub 로그인으로 만든 것)에 이어 두었을 수 있다.
        // 그때는 계정을 새로 만들지 않고 그 계정에 로그인 수단만 붙인다 — 한 사람이 계정 둘을
        // 갖게 되면 활동과 업무 일지가 갈린다.
        Optional<User> existing = userRepository.findByEmpSeq(empSeq).stream().findFirst();
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getPasswordHash() != null) {
                // 이미 비밀번호가 있는 계정이다. 사원 번호로 다시 만들어 주지 않는다.
                log.info("사원 {} 에 이미 비밀번호가 설정된 계정이 있다.", loginId);
                return Optional.empty();
            }
            user.setLoginId(loginId);
            user.setPasswordHash(PasswordHasher.hash(password));
            user.setMustChangePassword(true);
            log.info("사원 {} 에 이어져 있던 계정 {} 에 로그인 수단을 붙였다.", loginId, user.getLogin());
            return Optional.of(userRepository.save(user));
        }

        User user = new User();
        user.setLoginId(loginId);
        user.setName(employee.get().empNm());
        user.setCoSeq(companySeq);
        user.setEmpSeq(empSeq);
        user.setRole(UserRole.MEMBER);
        user.setPasswordHash(PasswordHasher.hash(password));
        // 사원 번호는 비밀이 아니다. 반드시 바꾸게 한다.
        user.setMustChangePassword(true);
        User saved = userRepository.save(user);
        log.info("사원 {}({}) 의 계정을 처음 로그인에서 만들었다.", employee.get().empNm(), loginId);
        return Optional.of(saved);
    }
}
