package com.worklog.vscode;

import com.worklog.auth.User;
import com.worklog.auth.UserRepository;
import com.worklog.config.ApiException;
import com.worklog.github.Repo;
import com.worklog.github.RepoRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        // 저장 이벤트는 은퇴했다 (2026-09-11). 확장이 보내지 않고 화면도 그리지 않는다.
        // 옛 확장이 아직 보내 오더라도 받아 쓰지 않는다 — 지난 행의 값은 그대로 둔다.
        session.setUnsavedFiles(orEmpty(request.unsavedFiles()));
        // 확장은 요약을 모른다. 서버가 적어 둔 것을 물려주지 않으면 10분마다 지워진다.
        session.setAiSessions(carryOverSummaries(session.getAiSessions(), orEmpty(request.aiSessions())));
        // null 을 그대로 둔다. 빈 배열로 바꾸면 "셀 수 없음" 이 "미푸시 없음" 으로 둔갑한다.
        session.setUnpushedCommits(request.unpushedCommits());
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

    /** 기간 조회. VSCode 내역을 달 단위로 볼 때 쓴다 (BACKLOG2 §2-3). */
    @Transactional(readOnly = true)
    public List<VscodeSession> findBetween(LocalDate from, LocalDate to, Long userId) {
        return sessions.findBetween(from, to, userId);
    }

    /**
     * 새로 받은 대화 목록에 <b>이미 적어 둔 요약</b>을 옮겨 붙인다.
     *
     * <p>확장은 10분마다 같은 대화를 다시 보내는데 요약은 서버가 채운 값이라 보내 주지 않는다.
     * 그대로 덮으면 애써 만든 요약이 사라지고, 다음 요약이 또 돌아 같은 대화를 하루에 수십 번
     * 요약하게 된다.
     *
     * <p><b>대화가 이어졌으면 물려주지 않는다.</b> 오전에 요약한 뒤 오후 내내 이어 간 대화를
     * 오전 요약으로 남겨 두면 그날 한 일을 잘못 적게 된다. 비워 두면 다시 요약된다.
     *
     * <p>질문 수뿐 아니라 <b>주고받은 내용</b>이 달라졌는지도 본다. 물어 둔 질문에 답이
     * 뒤늦게 달리는 일이 흔한데, 그때 질문 수는 그대로라 수만 보면 답을 못 본 채로 적은
     * 요약이 그대로 남는다.
     */
    static List<AiSessionSummary> carryOverSummaries(
            List<AiSessionSummary> previous, List<AiSessionSummary> incoming) {
        if (previous == null || previous.isEmpty()) {
            return incoming;
        }
        Map<String, AiSessionSummary> before = new HashMap<>();
        previous.forEach(p -> before.put(p.id(), p));

        return incoming.stream()
                .map(now -> {
                    AiSessionSummary was = before.get(now.id());
                    if (was == null
                            || was.summary() == null
                            || was.summary().isBlank()
                            || now.summary() != null
                            || continued(was, now)) {
                        return now;
                    }
                    return new AiSessionSummary(
                            now.id(),
                            now.title(),
                            now.firstAt(),
                            now.lastAt(),
                            now.promptCount(),
                            now.turns(),
                            null,
                            was.summary());
                })
                .toList();
    }

    /** 요약을 적어 둔 뒤로 대화가 이어졌는지 — 질문이 늘었거나 오간 내용이 달라졌는지. */
    private static boolean continued(AiSessionSummary was, AiSessionSummary now) {
        return !Objects.equals(was.promptCount(), now.promptCount())
                || !Objects.equals(was.turns(), now.turns());
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
