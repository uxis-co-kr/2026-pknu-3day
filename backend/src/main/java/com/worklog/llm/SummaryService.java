package com.worklog.llm;

import com.worklog.activity.Activity;
import com.worklog.activity.ActivityRepository;
import com.worklog.activity.ActivityType;
import com.worklog.activity.SummaryStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PENDING 활동을 LLM 으로 요약해 채운다 (PRD F2).
 *
 * <p>API 응답을 막지 않도록 스케줄러가 백그라운드에서 돌리고, 상태는 DB 컬럼으로 관리한다
 * (PRD 11 — 비동기 큐 = @Async + DB 상태 컬럼). 실패는 최대 3회 재시도한다.
 */
@Service
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    /** PRD F2 — 최대 3회 재시도. */
    public static final int MAX_RETRIES = 3;

    private static final int BATCH_SIZE = 20;

    private final ActivityRepository activityRepository;
    private final LlmProviderResolver resolver;
    private final PromptLoader promptLoader;
    private final LlmSettingService settingService;

    public SummaryService(
            ActivityRepository activityRepository,
            LlmProviderResolver resolver,
            PromptLoader promptLoader,
            LlmSettingService settingService) {
        this.activityRepository = activityRepository;
        this.resolver = resolver;
        this.promptLoader = promptLoader;
        this.settingService = settingService;
    }

    /**
     * 한 배치를 처리한다.
     *
     * @return 이번에 요약을 채운 건수
     */
    @Transactional
    public int runOnce() {
        List<Activity> targets =
                activityRepository.findSummaryTargets(MAX_RETRIES, PageRequest.of(0, BATCH_SIZE));
        if (targets.isEmpty()) {
            return 0;
        }
        int done = 0;
        // 같은 사용자의 활동이 이어지므로 설정 조회 결과를 배치 안에서 재사용한다.
        Map<Long, LlmProvider> perUser = new HashMap<>();
        for (Activity activity : targets) {
            if (summarize(activity, providerFor(activity, perUser))) {
                done++;
            }
        }
        log.info(
                "요약 파이프라인 — 대상 {}건, 완료 {}건 (기본 provider={})",
                targets.size(),
                done,
                resolver.resolve().id());
        return done;
    }

    /**
     * 활동 소유자의 설정 → 전역 설정 순으로 프로바이더를 고른다 (PRD F9).
     *
     * <p>소유자가 없는(미가입 계정) 활동은 전역 설정을 쓴다.
     */
    private LlmProvider providerFor(Activity activity, Map<Long, LlmProvider> cache) {
        Long userId = activity.getUser() == null ? null : activity.getUser().getId();
        if (userId == null) {
            return resolver.resolve();
        }
        return cache.computeIfAbsent(
                userId, id -> resolver.resolve(settingService.providerOf(id)));
    }

    private boolean summarize(Activity activity, LlmProvider provider) {
        // 이미 요약이 있으면 재호출하지 않는다 (PRD F2).
        if (activity.getSummary() != null && !activity.getSummary().isBlank()) {
            activity.setSummaryStatus(SummaryStatus.DONE);
            return true;
        }
        try {
            String summary = provider.complete(buildRequest(activity));
            if (summary == null || summary.isBlank()) {
                throw new IllegalStateException("빈 응답");
            }
            activity.setSummary(summary.strip());
            activity.setSummaryStatus(SummaryStatus.DONE);
            return true;
        } catch (Exception e) {
            // 예외를 삼키고 상태만 남긴다. 다음 주기에 다시 집힌다 (PRD F2).
            activity.setSummaryRetries(activity.getSummaryRetries() + 1);
            activity.setSummaryStatus(SummaryStatus.FAILED);
            log.warn(
                    "활동 {} 요약 실패 ({}회): {}",
                    activity.getId(),
                    activity.getSummaryRetries(),
                    e.getMessage());
            return false;
        }
    }

    LlmRequest buildRequest(Activity activity) {
        Map<String, String> vars = new HashMap<>();
        vars.put("repo", activity.getRepo() == null ? "" : activity.getRepo().getFullName());
        vars.put("message", messageOf(activity));
        vars.put("files", filesOf(activity));
        vars.put("fileCount", String.valueOf(activity.getFilesChanged()));
        vars.put("diff", activity.getRawDiff() == null ? "" : activity.getRawDiff());

        return LlmRequest.of(
                promptLoader.load(PromptLoader.COMMIT_SUMMARY_SYSTEM),
                promptLoader.render(PromptLoader.COMMIT_SUMMARY_USER, vars),
                vars);
    }

    /** PR 은 message(본문)가 비어 있는 경우가 많아 제목을 쓴다. */
    private static String messageOf(Activity activity) {
        if (activity.getType() != ActivityType.COMMIT) {
            String title = activity.getTitle() == null ? "" : activity.getTitle();
            String body = activity.getMessage() == null ? "" : activity.getMessage();
            return body.isBlank() ? title : title + "\n\n" + body;
        }
        return activity.getMessage() == null ? activity.getTitle() : activity.getMessage();
    }

    /** diff 머리말("--- 파일명")에서 파일 목록을 만든다. PR 은 diff 가 없어 빈 문자열. */
    private static String filesOf(Activity activity) {
        String diff = activity.getRawDiff();
        if (diff == null || diff.isBlank()) {
            return "";
        }
        return diff.lines()
                .filter(line -> line.startsWith("--- "))
                .map(line -> line.substring(4))
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
}
