package com.worklog.github;

import com.worklog.auth.User;
import com.worklog.auth.UserRepository;
import com.worklog.auth.UserService;
import com.worklog.config.ApiException;
import com.worklog.github.dto.GitHubRepoDto;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집 대상 리포 등록·삭제 (PRD F1-1).
 *
 * <p>등록자의 OAuth 토큰으로 실제 접근이 되는지 확인한 리포만 저장한다.
 */
@Service
public class RepoService {

    /** GitHub 이 허용하는 owner/name 문자 집합. */
    private static final Pattern FULL_NAME =
            Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})/[A-Za-z0-9._-]{1,100}$");

    private final RepoRepository repoRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final GitHubApiClient gitHubApiClient;

    public RepoService(
            RepoRepository repoRepository,
            UserRepository userRepository,
            UserService userService,
            GitHubApiClient gitHubApiClient) {
        this.repoRepository = repoRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.gitHubApiClient = gitHubApiClient;
    }

    @Transactional(readOnly = true)
    public List<Repo> list() {
        return repoRepository.findAllWithRegistrant();
    }

    @Transactional
    public Repo register(Long userId, String fullName) {
        String normalized = fullName == null ? "" : fullName.trim();
        if (!FULL_NAME.matcher(normalized).matches()) {
            throw ApiException.badRequest("INVALID_FULL_NAME", "owner/repo 형식으로 입력해 주세요.");
        }
        if (repoRepository.existsByFullName(normalized)) {
            throw ApiException.conflict("REPO_ALREADY_REGISTERED", "이미 등록된 리포입니다.");
        }

        User registrant = userRepository
                .findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
        String token = userService.githubTokenOf(registrant);
        if (token == null) {
            throw ApiException.forbidden("GITHUB_TOKEN_MISSING", "GitHub 토큰이 없습니다. 다시 로그인해 주세요.");
        }

        String[] parts = normalized.split("/", 2);
        // 접근 권한 확인 — 없는 리포와 권한 없는 리포 모두 REPO_NOT_ACCESSIBLE 로 답한다.
        GitHubRepoDto dto = gitHubApiClient.getRepo(parts[0], parts[1], token);

        Repo repo = new Repo();
        repo.setOwner(parts[0]);
        repo.setName(parts[1]);
        // GitHub 이 돌려준 정식 표기를 쓴다 (대소문자가 다르게 입력될 수 있다).
        repo.setFullName(dto != null && dto.fullName() != null ? dto.fullName() : normalized);
        repo.setDefaultBranch(dto == null ? null : dto.defaultBranch());
        repo.setRegisteredBy(registrant);
        return repoRepository.save(repo);
    }

    /**
     * 등록자만 삭제할 수 있다.
     *
     * <p>{@code activities.repo_id} 가 ON DELETE CASCADE 라 리포를 지우면 그 리포의 활동 기록이
     * 통째로 사라진다. 화면의 휴지통 아이콘 한 번으로 남의 수집 결과를 날릴 수 있어선 안 된다.
     *
     * <p>등록자가 비어 있는 리포(등록자 탈퇴 등)는 아무도 지울 수 없다. 관리자 개념이 생기면 다시 본다.
     */
    @Transactional
    public void delete(Long userId, Long repoId) {
        Repo repo = repoRepository
                .findWithRegistrant(repoId)
                .orElseThrow(() -> ApiException.notFound("REPO_NOT_FOUND", "리포를 찾을 수 없습니다."));

        User registrant = repo.getRegisteredBy();
        if (registrant == null || !registrant.getId().equals(userId)) {
            throw ApiException.forbidden("REPO_NOT_OWNED", "등록한 사람만 리포를 삭제할 수 있습니다.");
        }
        repoRepository.delete(repo);
    }

    @Transactional(readOnly = true)
    public Repo get(Long repoId) {
        return repoRepository
                .findById(repoId)
                .orElseThrow(() -> ApiException.notFound("REPO_NOT_FOUND", "리포를 찾을 수 없습니다."));
    }
}
