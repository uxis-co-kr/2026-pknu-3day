package com.worklog.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worklog.auth.User;
import com.worklog.auth.UserRepository;
import com.worklog.auth.UserService;
import com.worklog.config.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RepoServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final Long REPO_ID = 10L;

    private RepoRepository repoRepository;
    private RepoService repoService;

    @BeforeEach
    void setUp() {
        repoRepository = mock(RepoRepository.class);
        repoService = new RepoService(
                repoRepository, mock(UserRepository.class), mock(UserService.class),
                mock(GitHubApiClient.class));
    }

    private static User user(Long id) {
        User u = new User();
        u.setId(id);
        u.setLogin("user" + id);
        return u;
    }

    private Repo givenRepo(User registrant) {
        Repo repo = new Repo();
        repo.setId(REPO_ID);
        repo.setFullName("uxis-co-kr/2026-pknu-3day");
        repo.setRegisteredBy(registrant);
        when(repoRepository.findWithRegistrant(REPO_ID)).thenReturn(Optional.of(repo));
        return repo;
    }

    @Test
    @DisplayName("등록한 사람은 삭제할 수 있다")
    void ownerCanDelete() {
        Repo repo = givenRepo(user(OWNER_ID));

        repoService.delete(OWNER_ID, REPO_ID);

        verify(repoRepository).delete(repo);
    }

    @Test
    @DisplayName("남이 등록한 리포는 삭제할 수 없다 — 활동 기록이 CASCADE 로 사라지는 것을 막는다")
    void nonOwnerCannotDelete() {
        givenRepo(user(OWNER_ID));

        assertThatThrownBy(() -> repoService.delete(OTHER_ID, REPO_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(api.getCode()).isEqualTo("REPO_NOT_OWNED");
                });
        verify(repoRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("등록자가 없는 리포는 아무도 삭제할 수 없다")
    void nobodyCanDeleteOrphanRepo() {
        givenRepo(null);

        assertThatThrownBy(() -> repoService.delete(OWNER_ID, REPO_ID))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("등록한 사람만");
        verify(repoRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("없는 리포는 404 — 소유자 검사보다 먼저 걸린다")
    void missingRepoIs404() {
        when(repoRepository.findWithRegistrant(REPO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> repoService.delete(OWNER_ID, REPO_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("REPO_NOT_FOUND"));
    }
}
