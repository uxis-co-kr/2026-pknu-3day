package com.worklog.vscode;

import com.worklog.llm.LlmProvider;
import com.worklog.llm.LlmProviderResolver;
import com.worklog.llm.LlmRequest;
import com.worklog.llm.LlmSettingService;
import com.worklog.llm.PromptLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 대화 한 세션이 무엇이었는지 LLM 으로 적어 둔다.
 *
 * <p>대화 원문은 질문·답변이 수십 줄이라 일지를 쓸 때도, 나중에 훑을 때도 그대로 읽기 어렵다.
 * 커밋에 요약이 붙듯(F2) 대화에도 두어 문장을 붙인다.
 *
 * <p><b>세션 단위다.</b> 질의마다 붙이지 않는다 — 질문 원문이 이미 있어 값어치가 낮은 데 비해
 * 호출 수가 질문 수만큼 늘어난다.
 *
 * <p><b>전송 때 채우고, 이미 있으면 건너뛴다.</b> 확장은 10분마다 같은 대화를 다시 보내므로
 * 그때마다 부르면 같은 대화를 하루에 수십 번 요약하게 된다. 물려주는 규칙은
 * {@link VscodeSessionService#carryOverSummaries} 에 있다.
 */
@Service
public class AiSessionSummarizer {

    private static final Logger log = LoggerFactory.getLogger(AiSessionSummarizer.class);

    /** 한 번에 요약할 대화 수. 한 폴더에 열어 둔 대화가 이보다 많은 일은 드물다. */
    private static final int MAX_PER_RUN = 10;
    /** 프롬프트에 담을 질문·답변 수. 뒤쪽(최근)부터 담는다. */
    private static final int MAX_TURNS = 12;
    /** 답변 한 줄의 길이 상한. 확장이 이미 자르지만 옛 기록에는 긴 것이 있다. */
    private static final int MAX_ANSWER_LEN = 300;

    private final VscodeSessionRepository sessions;
    private final LlmProviderResolver resolver;
    private final PromptLoader prompts;
    private final LlmSettingService settings;

    public AiSessionSummarizer(
            VscodeSessionRepository sessions,
            LlmProviderResolver resolver,
            PromptLoader prompts,
            LlmSettingService settings) {
        this.sessions = sessions;
        this.resolver = resolver;
        this.prompts = prompts;
        this.settings = settings;
    }

    /**
     * 그 세션 행에서 <b>아직 요약이 없는</b> 대화만 요약해 채운다.
     *
     * <p>확장의 전송을 막지 않도록 응답을 보낸 뒤 따로 돈다. 실패하면 그 대화만 비워 두고
     * 넘어간다 — 다음 전송 때 다시 집힌다. 요약은 일지의 재료일 뿐이라, 못 만들었다고
     * 그날 기록까지 잃을 이유는 없다.
     */
    @Async
    @Transactional
    public void summarizeMissing(Long sessionId) {
        VscodeSession session = sessions.findById(sessionId).orElse(null);
        if (session == null) {
            return;
        }
        List<AiSessionSummary> ai = session.getAiSessions();
        if (ai == null || ai.isEmpty()) {
            return;
        }

        LlmProvider provider = null;
        List<AiSessionSummary> out = new ArrayList<>(ai.size());
        int done = 0;
        for (AiSessionSummary conversation : ai) {
            if (!needsSummary(conversation) || done >= MAX_PER_RUN) {
                out.add(conversation);
                continue;
            }
            if (provider == null) {
                Long userId = session.getUser() == null ? null : session.getUser().getId();
                provider = resolver.resolve(userId == null ? null : settings.providerOf(userId));
            }
            String summary = summarize(conversation, provider);
            out.add(summary == null ? conversation : withSummary(conversation, summary));
            if (summary != null) {
                done++;
            }
        }
        if (done == 0) {
            return;
        }
        session.setAiSessions(out);
        sessions.save(session);
        log.info("AI 대화 요약 — 세션 {} 에서 {}건을 채웠습니다", sessionId, done);
    }

    /** 질문이 하나라도 있고 아직 요약이 없는 대화. */
    private static boolean needsSummary(AiSessionSummary c) {
        return (c.summary() == null || c.summary().isBlank()) && !c.turns().isEmpty();
    }

    private String summarize(AiSessionSummary conversation, LlmProvider provider) {
        try {
            String text = provider.complete(buildRequest(conversation));
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("빈 응답");
            }
            return text.strip();
        } catch (Exception e) {
            // 삼키고 비워 둔다. 다음 전송 때 다시 집힌다.
            log.warn("AI 대화 {} 요약 실패: {}", conversation.id(), e.getMessage());
            return null;
        }
    }

    LlmRequest buildRequest(AiSessionSummary conversation) {
        Map<String, String> vars = new HashMap<>();
        vars.put("title", conversation.title());
        vars.put("firstAt", nullToEmpty(conversation.firstAt()));
        vars.put("lastAt", nullToEmpty(conversation.lastAt()));
        vars.put(
                "promptCount",
                String.valueOf(conversation.promptCount() == null
                        ? conversation.turns().size()
                        : conversation.promptCount()));
        vars.put("turns", turnsOf(conversation));

        return LlmRequest.of(
                prompts.load(PromptLoader.AI_SESSION_SUMMARY_SYSTEM),
                prompts.render(PromptLoader.AI_SESSION_SUMMARY_USER, vars),
                vars);
    }

    /** 최근 질문·답변을 프롬프트에 담을 모양으로 편다. */
    private static String turnsOf(AiSessionSummary conversation) {
        List<AiTurn> turns = conversation.turns();
        List<AiTurn> recent = turns.size() <= MAX_TURNS
                ? turns
                : turns.subList(turns.size() - MAX_TURNS, turns.size());
        StringBuilder out = new StringBuilder();
        for (AiTurn turn : recent) {
            out.append("- 물음: ").append(turn.prompt() == null ? "" : turn.prompt().strip()).append('\n');
            if (turn.answer() != null && !turn.answer().isBlank()) {
                out.append("  답: ").append(clip(turn.answer().strip())).append('\n');
            }
        }
        return out.toString().strip();
    }

    private static AiSessionSummary withSummary(AiSessionSummary c, String summary) {
        return new AiSessionSummary(
                c.id(), c.title(), c.firstAt(), c.lastAt(), c.promptCount(), c.turns(), null, summary);
    }

    private static String clip(String text) {
        return text.length() <= MAX_ANSWER_LEN ? text : text.substring(0, MAX_ANSWER_LEN) + "…";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
