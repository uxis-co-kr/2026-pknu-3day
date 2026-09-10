package com.worklog.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 계정을 기동 시 한 번 만든다 (TODO_0910 §1-3).
 *
 * <p>콘솔에서 서로 권한을 주고받게 하면 <b>첫 관리자를 만들 방법이 없다.</b> 그래서 설정으로
 * 정한다. 계정이 이미 있으면 손대지 않는다 — 비밀번호를 바꿔 두었는데 재기동할 때마다
 * 초기값으로 돌아가면 곤란하다.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserRepository userRepository;
    private final String adminId;
    private final String adminPassword;

    public AdminAccountInitializer(
            UserRepository userRepository,
            @Value("${worklog.admin.login-id:}") String adminId,
            @Value("${worklog.admin.initial-password:}") String adminPassword) {
        this.userRepository = userRepository;
        this.adminId = adminId == null ? "" : adminId.trim();
        this.adminPassword = adminPassword == null ? "" : adminPassword.trim();
    }

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args) {
        if (adminId.isEmpty() || adminPassword.isEmpty()) {
            log.info("관리자 계정 설정이 없어 만들지 않는다 (worklog.admin.login-id / initial-password).");
            return;
        }
        if (userRepository.findByLoginId(adminId).isPresent()) {
            log.info("관리자 계정 {} 이 이미 있다.", adminId);
            return;
        }
        User admin = new User();
        admin.setLoginId(adminId);
        admin.setName("관리자");
        admin.setRole(UserRole.ADMIN);
        admin.setPasswordHash(PasswordHasher.hash(adminPassword));
        // 초기 비밀번호는 설정 파일에 적혀 있으므로 비밀이 아니다.
        admin.setMustChangePassword(true);
        userRepository.save(admin);
        log.info("관리자 계정 {} 을 만들었다. 첫 로그인에서 비밀번호를 바꿔야 한다.", adminId);
    }
}
