package com.worklog.draft;

import com.worklog.activity.Activity;
import com.worklog.activity.ActivityType;
import com.worklog.vscode.TodoItem;
import com.worklog.vscode.UncommittedFile;
import com.worklog.vscode.UnpushedCommit;
import com.worklog.vscode.VscodeSession;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 업무 일지 Markdown 템플릿 (PRD F3 — 고정 1종).
 *
 * <p>순수 함수라 DB 없이 단위 테스트할 수 있다.
 */
public final class DraftTemplate {

    private DraftTemplate() {}

    /**
     * 활동 기록 없이 직접 쓰는 일지의 뼈대 (9/10).
     *
     * <p>머리말만 둔다. 무엇을 채우면 되는지는 보이되, 없는 활동을 지어내지는 않는다.
     */
    public static String blank(LocalDate workDate, String displayName) {
        return """
                # %s 업무 일지 — %s

                ## 완료한 작업
                - 

                ## 진행 중 / 미커밋
                - 

                ## 계획 / TODO
                - 

                ## 메모
                """
                .formatted(workDate, displayName);
    }

    public static String render(
            LocalDate workDate, String displayName, List<Activity> activities, List<VscodeSession> sessions) {

        StringBuilder md = new StringBuilder();
        md.append("# %s 업무 일지 — %s\n\n".formatted(workDate, displayName));

        md.append("## 완료한 작업\n");
        if (activities.isEmpty()) {
            md.append("- (기록된 활동 없음)\n");
        } else {
            activities.forEach(a -> md.append(completedLine(a)).append('\n'));
        }

        md.append("\n## 진행 중 / 미커밋\n");
        if (sessions.isEmpty()) {
            md.append("- (미커밋 작업 없음)\n");
        } else {
            sessions.forEach(s -> {
                md.append(inProgressLine(s)).append('\n');
                unpushedLine(s).ifPresent(line -> md.append(line).append('\n'));
            });
        }

        md.append("\n## 계획 / TODO\n");
        List<String> plans = planNotes(sessions);
        List<String> todos = todoLines(sessions);
        if (plans.isEmpty() && todos.isEmpty()) {
            md.append("- (기록된 계획 없음)\n");
        } else {
            // 계획은 확장에서 적은 markdown 문서 그대로다. 불릿을 덧붙이지 않는다.
            if (!plans.isEmpty()) {
                md.append(String.join("\n\n", plans)).append('\n');
            }
            if (!todos.isEmpty()) {
                // 문서 바로 아래에 붙이면 계획의 일부로 읽힌다. 한 줄 띄워 나눈다.
                if (!plans.isEmpty()) {
                    md.append('\n');
                }
                todos.forEach(t -> md.append("- ").append(t).append('\n'));
            }
        }

        md.append("\n## 메모\n(직접 작성)\n");
        return md.toString();
    }

    private static String completedLine(Activity a) {
        String repo = a.getRepo() == null ? "?" : a.getRepo().getFullName();
        String text = a.getSummary() != null && !a.getSummary().isBlank()
                ? a.getSummary().replace('\n', ' ').strip()
                : nullToEmpty(a.getTitle());

        if (a.getType() == ActivityType.COMMIT) {
            String shortSha = a.getSha() == null ? "" : "  (commit %s)".formatted(shorten(a.getSha()));
            return "- [%s] %s%s".formatted(repo, text, shortSha);
        }
        String label = a.getType() == ActivityType.PR_MERGED ? "머지" : "생성";
        return "- [%s] PR #%s %s: %s".formatted(repo, a.getExternalId(), label, text);
    }

    private static String inProgressLine(VscodeSession s) {
        String repo = s.getRepo() != null ? s.getRepo().getFullName() : s.getRemoteUrl();
        if (s.getSummary() != null && !s.getSummary().isBlank()) {
            return "- [%s] %s".formatted(repo, s.getSummary().strip());
        }
        List<UncommittedFile> files = s.getUncommittedFiles();
        if (files == null || files.isEmpty()) {
            return "- [%s] %s 브랜치 작업 중".formatted(repo, s.getBranch());
        }
        String names = files.stream().map(UncommittedFile::path).limit(5).collect(Collectors.joining(", "));
        String more = files.size() > 5 ? " 외 %d개".formatted(files.size() - 5) : "";
        return "- [%s] 미커밋 %d개 — %s%s".formatted(repo, files.size(), names, more);
    }

    /**
     * 커밋했지만 아직 push 하지 않은 것 (V11).
     *
     * <p>GitHub 수집기가 보지 못하는 구간이라 "완료한 작업" 에는 오르지 않는다. 그렇다고
     * 빼 두면 <b>커밋까지 해 둔 일</b>이 그날 일지에서 통째로 사라진다. 커밋 메시지는 이미
     * 사람이 쓴 요약이라 그대로 옮긴다.
     */
    private static Optional<String> unpushedLine(VscodeSession s) {
        List<UnpushedCommit> commits = s.getUnpushedCommits();
        if (commits == null || commits.isEmpty()) {
            return Optional.empty();
        }
        String repo = s.getRepo() != null ? s.getRepo().getFullName() : s.getRemoteUrl();
        String subjects = commits.stream()
                .limit(5)
                .map(UnpushedCommit::subject)
                .collect(Collectors.joining(", "));
        String more = commits.size() > 5 ? " 외 %d개".formatted(commits.size() - 5) : "";
        return Optional.of("- [%s] 미푸시 커밋 %d개 — %s%s".formatted(repo, commits.size(), subjects, more));
    }

    /**
     * 오늘 계획으로 적어 둔 markdown 문서 (PRD F3).
     *
     * <p><b>문서 한 통이 계획 하나다.</b> 예전에는 줄마다 불릿을 붙였는데, 확장이 문서를
     * 통째로 보내게 된 지금 그렇게 하면 제목도 들여쓴 목록도 평평한 불릿 더미가 된다.
     * 적은 그대로 옮기고 다듬지 않는다.
     *
     * <p>같은 폴더에서 브랜치를 옮겨 가며 일하면 세션이 여럿인데 계획 문서는 같다.
     * 같은 문서를 두 번 싣지 않는다.
     */
    private static List<String> planNotes(List<VscodeSession> sessions) {
        return sessions.stream()
                .map(VscodeSession::getPlanNote)
                .filter(p -> p != null && !p.isBlank())
                .map(String::strip)
                .distinct()
                .toList();
    }

    /** 계획 다음에 붙는 코드 안 TODO 주석 (PRD F3). */
    private static List<String> todoLines(List<VscodeSession> sessions) {
        List<String> lines = new java.util.ArrayList<>();
        for (VscodeSession s : sessions) {
            List<TodoItem> todos = s.getTodos();
            if (todos == null) {
                continue;
            }
            todos.stream().limit(10).forEach(t -> lines.add("%s:%d %s".formatted(t.path(), t.line(), t.text())));
        }
        return lines;
    }

    private static String shorten(String sha) {
        return sha.length() <= 7 ? sha : sha.substring(0, 7);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.replace('\n', ' ').strip();
    }
}
