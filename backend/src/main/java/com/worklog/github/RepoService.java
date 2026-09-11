package com.worklog.github;

import com.worklog.auth.User;
import com.worklog.auth.UserRepository;
import com.worklog.auth.UserService;
import com.worklog.config.ApiException;
import com.worklog.github.dto.GitHubRepoDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집 대상 리포 등록·삭제 (PRD F1-1).
 *
 * <p>등록자의 OAuth 토큰으로 실제 접근이 되는지 확인한 리포만 저장한다.
 */
@Service
public class RepoService {

    private static final Logger log = LoggerFactory.getLogger(RepoService.class);

    /** 한 번에 등록할 리포 수 상한. 최근에 손댄 것부터 채운다. */
    private static final int IMPORT_LIMIT = 20;

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

    /** 내가 등록한 리포만 (9/10 결정). */
    @Transactional(readOnly = true)
    public List<Repo> listMine(Long userId) {
        return repoRepository.findMineWithRegistrant(userId);
    }

    /**
     * 등록된 리포 이름 전부 — <b>등록자가 누구든</b>.
     *
     * <p>화면 목록({@link #listMine})과 달라야 하는 자리다. 확장은 "이 폴더의 작업을 보낼까"
     * 를 이 목록으로 가리는데, 내 것만 주면 <b>남이 등록한 리포에서 일하는 팀원의 기록이
     * 통째로 버려진다</b> — 리포는 한 사람만 등록할 수 있으므로(full_name UNIQUE) 두 번째
     * 사람은 등록할 길도 없다 (BACKLOG2 §2-4).
     */
    @Transactional(readOnly = true)
    public List<String> knownFullNames() {
        return repoRepository.findAllFullNames();
    }

    /** 사람을 부를 이름 — 이름이 없으면 GitHub 로그인, 그것도 없으면 사원번호. */
    private static String displayName(User user) {
        if (user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        if (user.getLogin() != null && !user.getLogin().isBlank()) {
            return user.getLogin();
        }
        return user.getLoginId() == null ? "다른 사람" : user.getLoginId();
    }

    /**
     * 내 GitHub 에서 접근 가능한 리포를 한 번에 등록한다 (9/10 "전체 등록").
     *
     * <p>계정에 리포가 수백 개인 사람도 있다. 전부 등록하면 수집이 감당하지 못하고 rate limit
     * 에도 걸린다. **최근에 손댄 것부터** {@link #IMPORT_LIMIT} 개까지만 가져온다.
     *
     * <p>이미 등록된 리포는 건너뛴다 — 남이 등록한 것도 같다. 한 리포가 두 번 등록되면
     * 수집이 겹친다.
     *
     * @return 새로 등록한 리포
     */
    @Transactional
    public List<Repo> importMine(Long userId) {
        User registrant = userRepository
                .findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
        String token = userService.githubTokenOf(registrant);
        if (token == null) {
            throw ApiException.forbidden(
                    "GITHUB_NOT_LINKED", "먼저 설정에서 GitHub 을 연결해 주세요.");
        }

        List<Repo> added = new ArrayList<>();
        for (GitHubRepoDto dto : gitHubApiClient.listMyRepos(token)) {
            if (dto.fullName() == null || repoRepository.existsByFullName(dto.fullName())) {
                continue;
            }
            String[] parts = dto.fullName().split("/", 2);
            if (parts.length != 2) {
                continue;
            }
            Repo repo = new Repo();
            repo.setOwner(parts[0]);
            repo.setName(parts[1]);
            repo.setFullName(dto.fullName());
            repo.setDefaultBranch(dto.defaultBranch());
            repo.setRegisteredBy(registrant);
            added.add(repoRepository.save(repo));
            if (added.size() >= IMPORT_LIMIT) {
                break;
            }
        }
        log.info("사용자 {} 의 GitHub 리포 {}개를 새로 등록했다.", registrant.getLogin(), added.size());
        return added;
    }

    /**
     * GitHub 주소에서 {@code owner/repo} 를 뽑는다 (9/10 — 주소를 붙여 넣어 등록한다).
     *
     * <p>브라우저 주소창에서 복사하면 {@code https://github.com/owner/repo} 이고, 클론 주소는
     * {@code .git} 이 붙는다. 트리·이슈 경로까지 함께 복사되는 일도 흔하다. 셋 다 받는다.
     * {@code owner/repo} 를 그대로 적은 것도 그대로 통과시킨다 — 쓰던 방식을 막을 이유가 없다.
     *
     * @return 뽑아낸 owner/repo. 형식이 아니면 {@code null}
     */
    static String parseRepoRef(String input) {
        String text = input == null ? "" : input.trim();
        if (text.isEmpty()) {
            return null;
        }
        // git@github.com:owner/repo.git 형태도 받는다.
        text = text.replaceFirst("^git@github\\.com:", "https://github.com/");
        text = text.replaceFirst("^(https?://)?(www\\.)?github\\.com/", "");
        text = text.replaceFirst("\\.git$", "");
        // /tree/main, /issues 처럼 뒤에 붙은 경로를 떼어 낸다.
        String[] parts = text.split("/");
        if (parts.length < 2) {
            return null;
        }
        String ref = parts[0] + "/" + parts[1];
        return FULL_NAME.matcher(ref).matches() ? ref : null;
    }

    @Transactional
    public Repo register(Long userId, String fullName) {
        String normalized = parseRepoRef(fullName);
        if (normalized == null) {
            throw ApiException.badRequest(
                    "INVALID_FULL_NAME",
                    "GitHub 주소를 붙여 넣어 주세요. 예) https://github.com/owner/repo");
        }
        // 리포는 한 사람만 등록한다 — 수집이 등록자 토큰으로 돌기 때문이다. 두 사람이 같은
        // 리포를 등록하면 같은 커밋을 두 번 모은다. 그래서 여기서 막되, 막힌 사람이 "그럼 내
        // 기록은 어떻게 되지" 를 알 수 있게 누가 등록했는지와 다음에 할 일을 함께 알린다.
        Optional<Repo> already = repoRepository.findByFullName(normalized);
        if (already.isPresent()) {
            User owner = already.get().getRegisteredBy();
            String who = owner == null ? "다른 사람" : displayName(owner);
            throw ApiException.conflict(
                    "REPO_ALREADY_REGISTERED",
                    "%s 님이 이미 등록한 리포입니다. 한 사람만 등록하면 팀 전체의 커밋이 모이고,"
                            .formatted(who)
                            + " VS Code 확장도 이 리포의 작업을 그대로 보냅니다.");
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
