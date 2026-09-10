package com.worklog.draft;

import com.worklog.activity.Activity;
import com.worklog.activity.ActivityType;
import com.worklog.vscode.TodoItem;
import com.worklog.vscode.UncommittedFile;
import com.worklog.vscode.VscodeSession;
import java.time.LocalDate;
import java.util.List;
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
            sessions.forEach(s -> md.append(inProgressLine(s)).append('\n'));
        }

        md.append("\n## 계획 / TODO\n");
        List<String> plans = plans(sessions);
        if (plans.isEmpty()) {
            md.append("- (기록된 계획 없음)\n");
        } else {
            plans.forEach(p -> md.append("- ").append(p).append('\n'));
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

    /** 계획 메모를 먼저, 그다음 코드 안 TODO 주석 (PRD F3). */
    private static List<String> plans(List<VscodeSession> sessions) {
        return sessions.stream()
                .flatMap(s -> {
                    List<String> lines = new java.util.ArrayList<>();
                    if (s.getPlanNote() != null && !s.getPlanNote().isBlank()) {
                        // 확장이 계획을 여러 건 받게 되면서 줄바꿈으로 이어 보낸다. 줄마다 불릿이 돼야 한다.
                        s.getPlanNote().lines()
                                .map(String::strip)
                                .filter(line -> !line.isEmpty())
                                .forEach(lines::add);
                    }
                    List<TodoItem> todos = s.getTodos();
                    if (todos != null) {
                        todos.stream()
                                .limit(10)
                                .forEach(t -> lines.add("%s:%d %s".formatted(t.path(), t.line(), t.text())));
                    }
                    return lines.stream();
                })
                .toList();
    }

    private static String shorten(String sha) {
        return sha.length() <= 7 ? sha : sha.substring(0, 7);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.replace('\n', ' ').strip();
    }
}
