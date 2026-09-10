package com.worklog.draft;

import com.worklog.activity.Activity;
import com.worklog.activity.ActivityType;
import com.worklog.llm.LlmProvider;
import com.worklog.llm.LlmProviderResolver;
import com.worklog.llm.LlmRequest;
import com.worklog.llm.LlmSettingService;
import com.worklog.llm.PromptLoader;
import com.worklog.vscode.TodoItem;
import com.worklog.vscode.UncommittedFile;
import com.worklog.vscode.VscodeSession;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 하루치 활동을 <b>한 번에</b> 읽어 업무 일지를 쓴다 (BACKLOG F-1).
 *
 * <p>지금까지 초안은 {@link DraftTemplate} 이 활동 행을 한 줄씩 늘어놓은 것이었다. 커밋별
 * 한 줄 요약을 이어 붙인 목록은 "그날 무슨 일을 했는지" 가 아니다. 여기서는 그날 기록
 * 전체를 프롬프트에 담아 LLM 이 묶어 쓰게 한다.
 *
 * <p>실패하면 템플릿으로 돌아간다. 일지가 아예 안 만들어지는 것보다 낫다.
 */
@Service
public class WorklogWriter {

    private static final Logger log = LoggerFactory.getLogger(WorklogWriter.class);

    /** 한 번에 넘길 활동 수. 넘치면 프롬프트가 컨텍스트를 넘어선다. */
    private static final int MAX_ACTIVITIES = 60;
    private static final int MAX_TOKENS = 1200;

    private final LlmProviderResolver resolver;
    private final LlmSettingService settingService;
    private final PromptLoader prompts;

    public WorklogWriter(
            LlmProviderResolver resolver, LlmSettingService settingService, PromptLoader prompts) {
        this.resolver = resolver;
        this.settingService = settingService;
        this.prompts = prompts;
    }

    /**
     * @return 실패하면 {@link DraftTemplate} 이 만든 본문. 절대 예외를 던지지 않는다.
     */
    public String write(
            Long userId,
            LocalDate workDate,
            String displayName,
            List<Activity> activities,
            List<VscodeSession> sessions) {

        try {
            LlmProvider provider = resolver.resolve(settingService.providerOf(userId));
            String body = provider.complete(request(workDate, displayName, activities, sessions));
            if (body == null || body.isBlank()) {
                throw new IllegalStateException("빈 응답");
            }
            return "# %s 업무 일지 — %s\n\n%s\n".formatted(workDate, displayName, body.strip());
        } catch (Exception e) {
            log.warn(
                    "{} 의 {} 업무 일지 생성이 LLM 으로 실패해 템플릿으로 대신한다: {}",
                    displayName,
                    workDate,
                    e.getMessage());
            return DraftTemplate.render(workDate, displayName, activities, sessions);
        }
    }

    LlmRequest request(
            LocalDate workDate,
            String displayName,
            List<Activity> activities,
            List<VscodeSession> sessions) {

        Map<String, String> vars = new HashMap<>();
        vars.put("workDate", workDate.toString());
        vars.put("name", displayName);
        vars.put("activities", activityLines(activities));
        vars.put("sessions", sessionLines(sessions));
        vars.put("plans", planLines(sessions));

        return new LlmRequest(
                prompts.load(PromptLoader.WORKLOG_SYSTEM),
                prompts.render(PromptLoader.WORKLOG_USER, vars),
                LlmRequest.DEFAULT_TEMPERATURE,
                MAX_TOKENS,
                vars);
    }

    /** 커밋별 요약이 이미 있으면 그것을 쓴다 — 두 번 요약하지 않고 컨텍스트도 아낀다. */
    private static String activityLines(List<Activity> activities) {
        if (activities.isEmpty()) {
            return "(없음)";
        }
        return activities.stream()
                .limit(MAX_ACTIVITIES)
                .map(WorklogWriter::activityLine)
                .collect(Collectors.joining("\n"));
    }

    private static String activityLine(Activity a) {
        String repo = a.getRepo() == null ? "?" : a.getRepo().getFullName();
        String text = a.getSummary() != null && !a.getSummary().isBlank()
                ? a.getSummary().replace('\n', ' ').strip()
                : String.valueOf(a.getTitle());
        String mark = a.getType() == ActivityType.COMMIT
                ? shorten(a.getSha())
                : "PR #" + a.getExternalId();
        String kind = switch (a.getType()) {
            case COMMIT -> "커밋";
            case PR_OPENED -> "PR 생성";
            case PR_MERGED -> "PR 머지";
        };
        return "- [%s] %s (%s, %s) +%d −%d"
                .formatted(repo, text, kind, mark, nz(a.getAdditions()), nz(a.getDeletions()));
    }

    private static String sessionLines(List<VscodeSession> sessions) {
        if (sessions.isEmpty()) {
            return "(없음)";
        }
        StringBuilder sb = new StringBuilder();
        for (VscodeSession s : sessions) {
            String repo = s.getRepo() != null ? s.getRepo().getFullName() : s.getRemoteUrl();
            sb.append("- [%s] %s 브랜치\n".formatted(repo, s.getBranch()));
            List<UncommittedFile> files = s.getUncommittedFiles();
            if (files != null) {
                for (UncommittedFile f : files.stream().limit(20).toList()) {
                    sb.append("  · %s (+%d −%d)\n".formatted(f.path(), f.additions(), f.deletions()));
                }
            }
            List<TodoItem> todos = s.getTodos();
            if (todos != null) {
                for (TodoItem t : todos.stream().limit(10).toList()) {
                    sb.append("  · TODO %s:%d %s\n".formatted(t.path(), t.line(), t.text()));
                }
            }
        }
        return sb.toString().strip();
    }

    private static String planLines(List<VscodeSession> sessions) {
        String plans = sessions.stream()
                .map(VscodeSession::getPlanNote)
                .filter(p -> p != null && !p.isBlank())
                // 확장이 여러 건을 줄바꿈으로 이어 보낸다 (서버 계약은 문자열 한 칸).
                .flatMap(p -> p.lines())
                .map(String::strip)
                .filter(p -> !p.isEmpty())
                .distinct()
                .map(p -> "- " + p)
                .collect(Collectors.joining("\n"));
        return plans.isEmpty() ? "(없음)" : plans;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static String shorten(String sha) {
        if (sha == null) {
            return "?";
        }
        return sha.length() <= 7 ? sha : sha.substring(0, 7);
    }
}
