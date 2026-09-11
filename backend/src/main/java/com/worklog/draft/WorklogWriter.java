package com.worklog.draft;

import com.worklog.activity.Activity;
import com.worklog.activity.ActivityType;
import com.worklog.llm.LlmProvider;
import com.worklog.llm.LlmProviderResolver;
import com.worklog.llm.LlmRequest;
import com.worklog.llm.LlmSettingService;
import com.worklog.llm.PromptLoader;
import com.worklog.vscode.AiSessionSummary;
import com.worklog.vscode.AiTurn;
import com.worklog.vscode.TodoItem;
import com.worklog.vscode.UncommittedFile;
import com.worklog.vscode.VscodeSession;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    /** 세션 하나에서 가져올 AI 프롬프트 수. */
    private static final int MAX_AI_PROMPTS_PER_SESSION = 6;
    /** 전체 AI 프롬프트 상한. */
    private static final int MAX_AI_PROMPTS = 20;

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
        vars.put("aiPrompts", aiLines(sessions));

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

    /**
     * AI 와 나눈 대화에서 사용자가 친 말만 (V9, V10 에서 제목이 붙었다).
     *
     * <p>커밋에도 미커밋 변경에도 남지 않는 작업의 단서다. 세션마다 앞의 몇 개만 넣는다 —
     * 전부 넣으면 이 부분이 프롬프트를 차지해 정작 커밋이 밀린다.
     *
     * <p>V10 부터는 답변도 함께 들어오지만 <b>여기에는 담지 않는다.</b> 답변은 질문보다
     * 훨씬 길어 20개만 넣어도 프롬프트가 두 배가 된다. 답변은 화면(VSCode 내역)에서 본다.
     */
    private static String aiLines(List<VscodeSession> sessions) {
        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (AiSessionSummary a : distinctAiSessions(sessions)) {
            if (used >= MAX_AI_PROMPTS) {
                break;
            }
            List<String> asked = a.turns().stream()
                    .map(AiTurn::prompt)
                    .filter(p -> p != null && !p.isBlank())
                    .map(String::strip)
                    .distinct()
                    .limit(Math.min(MAX_AI_PROMPTS_PER_SESSION, MAX_AI_PROMPTS - used))
                    .toList();
            if (asked.isEmpty()) {
                continue;
            }
            // 제목을 앞에 세운다 — 어느 대화에서 나온 말인지 묶여야 일지가 갈래를 잡는다.
            sb.append("· %s (%d회 물음)%n".formatted(a.title(), a.promptCount() == null ? asked.size() : a.promptCount()));
            for (String p : asked) {
                sb.append("  - ").append(p).append(System.lineSeparator());
            }
            used += asked.size();
        }
        return sb.isEmpty() ? "(없음)" : sb.toString().strip();
    }

    /**
     * 세션 행 여럿에 같은 대화가 들어 있을 수 있다.
     *
     * <p>세션 키는 브랜치별인데 AI 대화는 <b>폴더 단위</b>다. 오전에 A 브랜치, 오후에 B
     * 브랜치로 일하면 두 행이 같은 대화를 각각 들고 있다. 대화 id 로 한 번만 남긴다
     * (BACKLOG2_client C-1).
     */
    private static Collection<AiSessionSummary> distinctAiSessions(List<VscodeSession> sessions) {
        Map<String, AiSessionSummary> byId = new LinkedHashMap<>();
        for (VscodeSession s : sessions) {
            if (s.getAiSessions() == null) {
                continue;
            }
            for (AiSessionSummary a : s.getAiSessions()) {
                // 같은 대화가 여러 행에 있으면 질문이 더 많은 쪽(늦게 보고된 것)을 남긴다.
                byId.merge(a.id(), a, (x, y) -> y.turns().size() >= x.turns().size() ? y : x);
            }
        }
        return byId.values();
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
