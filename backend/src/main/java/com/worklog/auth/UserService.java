package com.worklog.auth;

import com.worklog.activity.ActivityRepository;
import com.worklog.auth.crypto.AesEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GitHub 로그인 사용자의 UPSERT (PRD F5).
 *
 * <p>login 은 GitHub 에서 바뀔 수 있으므로 불변인 {@code github_id} 를 기준으로 찾고,
 * 표시용 필드(login/name/avatar)는 로그인할 때마다 최신값으로 덮는다.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final AesEncryptor encryptor;
    private final java.util.Set<String> adminLogins;

    public UserService(
            UserRepository userRepository,
            ActivityRepository activityRepository,
            AesEncryptor encryptor,
            @Value("${worklog.admin-logins:}") String adminLogins) {
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
        this.encryptor = encryptor;
        // 관리자 계정은 운영자가 정한다. 콘솔에서 서로 권한을 주고받게 하면
        // 첫 관리자를 만들 방법이 없고, 아무나 자기를 관리자로 올릴 수 있다.
        this.adminLogins = java.util.Arrays.stream(adminLogins.split(","))
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .map(v -> v.toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Transactional
    public User upsertFromGitHub(GitHubOAuthClient.GitHubUserDto dto, String accessToken) {
        User user = userRepository.findByGithubId(dto.id()).orElseGet(User::new);
        user.setGithubId(dto.id());
        user.setLogin(dto.login());
        user.setName(dto.name());
        user.setAvatarUrl(dto.avatar_url());
        // 수집기가 이 토큰으로 리포에 접근한다. 평문 저장 금지 (PRD 11).
        user.setGithubTokenEnc(encryptor.encrypt(accessToken));
        applyAdminRole(user);
        User saved = userRepository.save(user);

        // 가입 전에 수집된 활동을 이어 붙인다. 재수집으로는 고쳐지지 않는 자리다 (PRD F1-5).
        int linked = activityRepository.linkExistingActivities(saved.getId(), saved.getLogin());
        if (linked > 0) {
            log.info("{} 의 기존 활동 {}건을 사용자에 연결했다.", saved.getLogin(), linked);
        }
        return saved;
    }

    /**
     * 설정에 적힌 로그인이면 관리자로, 아니면 일반 회원으로 맞춘다.
     *
     * <p>목록에서 빠지면 권한도 회수된다 — 설정이 곧 사실이어야 관리자 목록을 한 곳에서 볼 수 있다.
     */
    private void applyAdminRole(User user) {
        boolean shouldBeAdmin =
                user.getLogin() != null
                        && adminLogins.contains(user.getLogin().toLowerCase(java.util.Locale.ROOT));
        UserRole target = shouldBeAdmin ? UserRole.ADMIN : UserRole.MEMBER;
        if (user.getRole() != target) {
            log.info("{} 의 권한을 {} 로 바꾼다.", user.getLogin(), target);
            user.setRole(target);
        }
    }

    /** 저장된 GitHub 토큰을 복호화해 돌려준다. 없으면 null. */
    @Transactional(readOnly = true)
    public String githubTokenOf(User user) {
        return encryptor.decrypt(user.getGithubTokenEnc());
    }
}
