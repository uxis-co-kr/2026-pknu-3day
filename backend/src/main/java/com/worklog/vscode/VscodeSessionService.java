package com.worklog.vscode;

import com.worklog.auth.User;
import com.worklog.auth.UserRepository;
import com.worklog.config.ApiException;
import com.worklog.github.Repo;
import com.worklog.github.RepoRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 확장이 보고한 미커밋 작업 세션의 수신·조회 (PRD F6, 7. /vscode/sessions).
 */
@Service
public class VscodeSessionService {

    private final VscodeSessionRepository sessions;
    private final UserRepository users;
    private final RepoRepository repos;

    public VscodeSessionService(
            VscodeSessionRepository sessions, UserRepository users, RepoRepository repos) {
        this.sessions = sessions;
        this.users = users;
        this.repos = repos;
    }

    /**
     * (user, remoteUrl, branch, workDate) 기준 UPSERT. 확장은 30분마다 같은 세션을 다시 보내므로
     * 행이 쌓이면 안 되고, 늘 마지막 보고 내용만 남는다.
     */
    @Transactional
    public VscodeSession upsert(Long userId, SessionRequest request) {
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));

        VscodeSession session = sessions
                .findByUserIdAndRemoteUrlAndBranchAndWorkDate(
                        userId, request.remoteUrl(), request.branch(), request.workDate())
                .orElseGet(() -> {
                    VscodeSession fresh = new VscodeSession();
                    fresh.setUser(user);
                    fresh.setRemoteUrl(request.remoteUrl());
                    fresh.setBranch(request.branch());
                    fresh.setWorkDate(request.workDate());
                    return fresh;
                });

        session.setRepo(matchRepo(request.remoteUrl()));
        session.setUncommittedFiles(orEmpty(request.uncommittedFiles()));
        session.setTodos(orEmpty(request.todos()));
        session.setEditTimeline(orEmpty(request.editTimeline()));
        session.setAiSessions(orEmpty(request.aiSessions()));
        session.setLastCommitAt(request.lastCommitAt());
        // 확장이 보낸 계획을 그대로 둔다 — 지운 계획은 여기서도 지워져야 한다.
        //
        // 예전에는 빈 값을 무시했다. 확장이 재시작하면 메모를 잃고 null 을 보냈기 때문이다.
        // 지금은 확장이 계획을 globalState 에 두고 재시작해도 되살린다(a79ef18). 그래서
        // 빈 값은 "잃어버렸다" 가 아니라 "지웠다" 는 뜻이고, 무시하면 계획 문서에서 지운
        // 줄이 서버에 그대로 남는다 (BACKLOG2_client C-2).
        session.setPlanNote(
                request.planNote() == null || request.planNote().isBlank() ? null : request.planNote());
        session.setReportedAt(OffsetDateTime.now());

        return sessions.save(session);
    }

    /** 대시보드 조회 (★ JWT / API Key 둘 다). userId 가 없으면 그날 전원. */
    @Transactional(readOnly = true)
    public List<VscodeSession> findForDay(LocalDate workDate, Long userId) {
        return sessions.findForDay(workDate, userId);
    }

    /** 등록된 리포면 연결해 두고, 아니면 null 로 남긴다 (PRD 6. repo_id NULL 허용). */
    private Repo matchRepo(String remoteUrl) {
        return RemoteUrlParser.toFullName(remoteUrl)
                .flatMap(repos::findByFullName)
                .orElse(null);
    }

    private static <T> List<T> orEmpty(List<T> value) {
        return value == null ? new ArrayList<>() : new ArrayList<>(value);
    }
}
