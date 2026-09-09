package com.worklog.auth;

import com.worklog.auth.crypto.AesEncryptor;
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

    private final UserRepository userRepository;
    private final AesEncryptor encryptor;

    public UserService(UserRepository userRepository, AesEncryptor encryptor) {
        this.userRepository = userRepository;
        this.encryptor = encryptor;
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
        return userRepository.save(user);
    }

    /** 저장된 GitHub 토큰을 복호화해 돌려준다. 없으면 null. */
    @Transactional(readOnly = true)
    public String githubTokenOf(User user) {
        return encryptor.decrypt(user.getGithubTokenEnc());
    }
}
