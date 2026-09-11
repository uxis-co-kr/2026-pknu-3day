package com.worklog.draft;

import com.worklog.activity.Activity;
import com.worklog.activity.ActivityType;
import com.worklog.vscode.RemoteUrlParser;
import com.worklog.vscode.TodoItem;
import com.worklog.vscode.UncommittedFile;
import com.worklog.vscode.UnpushedCommit;
import com.worklog.vscode.VscodeSession;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 업무 일지 Markdown 템플릿 (PRD F3 — 고정 1종).
 *
 * <p>순수 함수라 DB 없이 단위 테스트할 수 있다.
 */
public final class DraftTemplate {

    private DraftTemplate() {}

    /** 저장소를 알 수 없는 기록도 어딘가에는 실려야 한다 — 통째로 사라지면 안 된다. */
    private static final String UNKNOWN_REPO = "(알 수 없는 저장소)";

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

    /**
     * 하루치 일지 (PRD F3).
     *
     * <p><b>저장소로 먼저 나눈다</b> (9/11). 한 덩어리로 늘어놓으면 저장소를 둘 이상 오간 날에
     * 무엇이 어느 이야기인지 알 수 없다. 저장소마다 완료·진행 중·계획을 따로 적고, 사람이
     * 채우는 "메모" 만 맨 끝에 한 번 둔다.
     */
    public static String render(
            LocalDate workDate, String displayName, List<Activity> activities, List<VscodeSession> sessions) {

        StringBuilder md = new StringBuilder();
        md.append("# %s 업무 일지 — %s\n".formatted(workDate, displayName));

        Map<String, List<Activity>> activityByRepo = activities.stream()
                .collect(Collectors.groupingBy(DraftTemplate::repoOf, LinkedHashMap::new, Collectors.toList()));
        Map<String, List<VscodeSession>> sessionByRepo = sessions.stream()
                .collect(Collectors.groupingBy(DraftTemplate::repoOf, LinkedHashMap::new, Collectors.toList()));

        // 활동이 있는 저장소를 이름 순으로 먼저, 기록만 있는 저장소를 그다음에. 순서가 날마다
        // 흔들리면 어제 일지와 나란히 놓고 읽을 수 없다.
        List<String> repos = new ArrayList<>(new java.util.TreeSet<>(activityByRepo.keySet()));
        new java.util.TreeSet<>(sessionByRepo.keySet()).stream()
                .filter(r -> !repos.contains(r))
                .forEach(repos::add);

        if (repos.isEmpty()) {
            md.append("\n## 완료한 작업\n- (기록된 활동 없음)\n");
        }
        for (String repo : repos) {
            md.append("\n## %s\n".formatted(repo));

            md.append("\n### 완료한 작업\n");
            List<Activity> done = activityByRepo.getOrDefault(repo, List.of());
            if (done.isEmpty()) {
                md.append("- (없음)\n");
            } else {
                done.forEach(a -> md.append(completedLine(a)).append('\n'));
            }

            md.append("\n### 진행 중 / 미커밋\n");
            List<VscodeSession> mine = sessionByRepo.getOrDefault(repo, List.of());
            if (mine.isEmpty()) {
                md.append("- (없음)\n");
            } else {
                mine.forEach(s -> {
                    md.append(inProgressLine(s)).append('\n');
                    unpushedLine(s).ifPresent(line -> md.append(line).append('\n'));
                });
            }

            md.append("\n### 계획 / TODO\n");
            List<String> plans = planNotes(mine);
            List<String> todos = todoLines(mine);
            if (plans.isEmpty() && todos.isEmpty()) {
                md.append("- (없음)\n");
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
        }

        md.append("\n## 메모\n(직접 작성)\n");
        return md.toString();
    }

    /** 어느 저장소 이야기인가. 세션은 등록 전이라 repo 가 없을 수 있어 원격 주소에서 뽑는다. */
    private static String repoOf(Activity a) {
        return a.getRepo() == null ? UNKNOWN_REPO : a.getRepo().getFullName();
    }

    private static String repoOf(VscodeSession s) {
        if (s.getRepo() != null) {
            return s.getRepo().getFullName();
        }
        String url = s.getRemoteUrl();
        if (url == null || url.isBlank()) {
            return UNKNOWN_REPO;
        }
        return RemoteUrlParser.toFullName(url).orElse(url);
    }

    private static String completedLine(Activity a) {
        String text = a.getSummary() != null && !a.getSummary().isBlank()
                ? a.getSummary().replace('\n', ' ').strip()
                : nullToEmpty(a.getTitle());

        if (a.getType() == ActivityType.COMMIT) {
            String shortSha = a.getSha() == null ? "" : "  (commit %s)".formatted(shorten(a.getSha()));
            return "- %s%s".formatted(text, shortSha);
        }
        String label = a.getType() == ActivityType.PR_MERGED ? "머지" : "생성";
        return "- PR #%s %s: %s".formatted(a.getExternalId(), label, text);
    }

    private static String inProgressLine(VscodeSession s) {
        if (s.getSummary() != null && !s.getSummary().isBlank()) {
            return "- %s".formatted(s.getSummary().strip());
        }
        List<UncommittedFile> files = s.getUncommittedFiles();
        if (files == null || files.isEmpty()) {
            return "- %s 브랜치 작업 중".formatted(s.getBranch());
        }
        String names = files.stream().map(UncommittedFile::path).limit(5).collect(Collectors.joining(", "));
        String more = files.size() > 5 ? " 외 %d개".formatted(files.size() - 5) : "";
        return "- 미커밋 %d개 — %s%s".formatted(files.size(), names, more);
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
        String subjects = commits.stream()
                .limit(5)
                .map(UnpushedCommit::subject)
                .collect(Collectors.joining(", "));
        String more = commits.size() > 5 ? " 외 %d개".formatted(commits.size() - 5) : "";
        return Optional.of("- 미푸시 커밋 %d개 — %s%s".formatted(commits.size(), subjects, more));
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
