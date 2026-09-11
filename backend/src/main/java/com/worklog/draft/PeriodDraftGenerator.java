package com.worklog.draft;

import com.worklog.activity.Activity;
import com.worklog.activity.ActivityRepository;
import com.worklog.activity.ActivityType;
import com.worklog.auth.User;
import com.worklog.auth.UserRepository;
import com.worklog.config.ApiException;
import com.worklog.config.KstDates;
import com.worklog.github.Repo;
import com.worklog.github.RepoRepository;
import com.worklog.llm.LlmProvider;
import com.worklog.llm.LlmProviderResolver;
import com.worklog.llm.LlmRequest;
import com.worklog.llm.LlmSettingService;
import com.worklog.llm.PromptLoader;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기간 업무 일지를 AI 로 만든다 — 주간(WEEKLY)과 저장소별(REPO).
 *
 * <p>하루치({@link DraftGenerator})와 같은 자리에 같은 모양으로 저장한다. 그래서 저장·전송은
 * 기존 경로({@code PATCH /drafts/{id}}, {@code POST /drafts/{id}/notify})를 그대로 탄다.
 *
 * <p>무엇을 재료로 삼느냐만 다르다.
 *
 * <ul>
 *   <li><b>주간</b> — 그 기간의 하루치 일지를 모은다. 사람이 이미 고쳐 쓴 글이라 활동 원본보다 낫다.
 *       하루치가 하나도 없으면 활동에서 바로 만든다
 *   <li><b>저장소별</b> — 그 기간 그 저장소의 커밋·PR 을 모은다. 사람을 지정하면 그 사람 것만
 * </ul>
 *
 * <p>LLM 이 실패하면 예외를 던진다 — 하루치와 다르다. 하루치는 템플릿으로 돌아갈 수 있지만
 * 기간 일지는 "묶어 쓰는 것" 자체가 값어치라, 묶지 못한 글을 저장해 두면 사람이 지우게 된다.
 */
@Service
public class PeriodDraftGenerator {

    private static final Logger log = LoggerFactory.getLogger(PeriodDraftGenerator.class);

    /** 한 번에 볼 수 있는 기간. 넘치면 프롬프트가 컨텍스트를 넘어선다. */
    static final int MAX_DAYS = 92;
    /** 저장소별 일지에 넣을 최대 기록 줄 수. */
    static final int MAX_ENTRIES = 120;
    private static final int MAX_TOKENS = 1500;

    private final DraftRepository draftRepository;
    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final RepoRepository repoRepository;
    private final LlmProviderResolver resolver;
    private final LlmSettingService llmSettings;
    private final PromptLoader prompts;

    public PeriodDraftGenerator(
            DraftRepository draftRepository,
            ActivityRepository activityRepository,
            UserRepository userRepository,
            RepoRepository repoRepository,
            LlmProviderResolver resolver,
            LlmSettingService llmSettings,
            PromptLoader prompts) {
        this.draftRepository = draftRepository;
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
        this.repoRepository = repoRepository;
        this.resolver = resolver;
        this.llmSettings = llmSettings;
        this.prompts = prompts;
    }

    /**
     * 주간 업무일지. 고른 날이 든 <b>그 주(월~일)</b> 의 하루치 일지를 묶어 쓴다.
     *
     * <p>기간을 사람이 자유롭게 잡게 두면 같은 주를 조금씩 다르게 잡을 때마다 새 일지가 쌓인다.
     * 주를 단위로 고정하면 하루치와 똑같아진다 — 같은 주에 다시 만들면 버전만 올라가고
     * 목록에는 그 주의 최신 것 하나만 남는다.
     */
    @Transactional
    public Draft weekly(Long userId, LocalDate anyDayOfWeek) {
        LocalDate from = mondayOf(anyDayOfWeek);
        LocalDate to = from.plusDays(6);
        User user = findUser(userId);

        List<Draft> dailies = draftRepository.findLatestBetween(from, to).stream()
                .filter(d -> d.getKind() == DraftKind.DAILY)
                .filter(d -> d.getUser() != null && d.getUser().getId().equals(userId))
                .sorted(java.util.Comparator.comparing(Draft::getWorkDate))
                .toList();

        String material;
        int recordedDays;
        if (!dailies.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Draft d : dailies) {
                sb.append("[").append(d.getWorkDate()).append("]\n")
                        .append(stripped(d.getContentMd())).append("\n\n");
            }
            material = sb.toString().strip();
            recordedDays = dailies.size();
        } else {
            // 하루치를 한 번도 쓰지 않은 기간이다. 활동에서 바로 만든다 — 빈 일지를 주는 것보다 낫다.
            List<Activity> activities = activityRepository.findForUserBetween(
                    userId, KstDates.startOf(from), KstDates.endOf(to));
            if (activities.isEmpty()) {
                throw ApiException.badRequest(
                        "NO_MATERIAL", "이 기간에 쓸 재료가 없습니다. 하루치 일지도 활동 기록도 없습니다.");
            }
            Map<LocalDate, List<Activity>> byDay = new java.util.LinkedHashMap<>();
            activities.forEach(a -> byDay
                    .computeIfAbsent(KstDates.toKstDate(a.getOccurredAt()), k -> new ArrayList<>())
                    .add(a));
            StringBuilder sb = new StringBuilder();
            byDay.forEach((day, rows) -> {
                sb.append("[").append(day).append("]\n");
                rows.forEach(a -> sb.append(activityLine(a)).append('\n'));
                sb.append('\n');
            });
            material = sb.toString().strip();
            recordedDays = byDay.size();
        }

        long dayCount = ChronoUnit.DAYS.between(from, to) + 1;
        Map<String, String> vars = new HashMap<>();
        vars.put("name", displayName(user));
        vars.put("from", from.toString());
        vars.put("to", to.toString());
        vars.put("dayCount", String.valueOf(dayCount));
        vars.put("recordedDays", String.valueOf(recordedDays));
        vars.put("days", material);

        String body = complete(
                userId, PromptLoader.WEEKLY_WORKLOG_SYSTEM, PromptLoader.WEEKLY_WORKLOG_USER, vars);
        String content = "# %s 주간 업무일지 — %s\n\n%s\n\n%s\n"
                .formatted(weekLabel(from), displayName(user), body, periodNote(from, to));

        Draft draft = newDraft(user, DraftKind.WEEKLY, from, to, null);
        draft.setContentMd(content);
        Draft saved = draftRepository.save(draft);
        log.info("{} 의 {}~{} 주간 업무일지 v{} 생성 — 재료 {}일", displayName(user), from, to, saved.getVersion(), recordedDays);
        return saved;
    }

    /**
     * 저장소별 업무일지. 고른 날이 든 <b>그 주(월~일)</b> 그 저장소의 커밋·PR 을 묶어 쓴다.
     *
     * <p>주간과 같은 단위로 둔다. 그래야 같은 주에 다시 만들 때 버전만 올라가고, 저장소마다
     * 주에 하나씩 이어진다 — 하루치를 쓰는 방식 그대로다.
     */
    @Transactional
    public Draft byRepo(Long userId, Long repoId, LocalDate anyDayOfWeek, boolean mineOnly) {
        LocalDate from = mondayOf(anyDayOfWeek);
        LocalDate to = from.plusDays(6);
        User user = findUser(userId);
        Repo repo = repoRepository.findById(repoId)
                .orElseThrow(() -> ApiException.notFound("REPO_NOT_FOUND", "저장소를 찾을 수 없습니다."));

        List<Activity> rows = activityRepository
                .findBetweenForRepoDraft(
                        KstDates.startOf(from), KstDates.endOf(to), repoId, mineOnly ? userId : null);
        if (rows.isEmpty()) {
            throw ApiException.badRequest(
                    "NO_MATERIAL", "이 기간에 %s 저장소의 기록이 없습니다.".formatted(repo.getFullName()));
        }

        long commits = rows.stream().filter(a -> a.getType() == ActivityType.COMMIT).count();
        long prs = rows.stream().filter(a -> a.getType() == ActivityType.PR_OPENED).count();
        long merges = rows.stream().filter(a -> a.getType() == ActivityType.PR_MERGED).count();
        List<String> people = rows.stream()
                .map(a -> a.getUser() != null ? displayName(a.getUser()) : a.getExternalLogin())
                .filter(n -> n != null && !n.isBlank())
                .distinct()
                .sorted()
                .toList();

        Map<String, String> vars = new HashMap<>();
        vars.put("repo", repo.getFullName());
        vars.put("name", displayName(user));
        vars.put("from", from.toString());
        vars.put("to", to.toString());
        vars.put("people", people.isEmpty() ? "(없음)" : String.join(", ", people));
        vars.put("commits", String.valueOf(commits));
        vars.put("prs", String.valueOf(prs));
        vars.put("merges", String.valueOf(merges));
        vars.put("entries", rows.stream().limit(MAX_ENTRIES)
                .map(PeriodDraftGenerator::activityLine)
                .collect(java.util.stream.Collectors.joining("\n")));

        String body = complete(
                userId, PromptLoader.REPO_WORKLOG_SYSTEM, PromptLoader.REPO_WORKLOG_USER, vars);
        String content = "# %s — %s 저장소별 업무일지\n\n%s\n\n%s\n"
                .formatted(repo.getFullName(), weekLabel(from), body, periodNote(from, to));

        Draft draft = newDraft(user, DraftKind.REPO, from, to, repo);
        draft.setContentMd(content);
        draft.setSourceActivityIds(rows.stream().map(Activity::getId).toArray(Long[]::new));
        Draft saved = draftRepository.save(draft);
        log.info("{} 의 {}~{} 저장소별 업무일지 v{} 생성 — 활동 {}건",
                repo.getFullName(), from, to, saved.getVersion(), rows.size());
        return saved;
    }

    // ── 공통 ─────────────────────────────────────────────────────

    private Draft newDraft(User user, DraftKind kind, LocalDate from, LocalDate to, Repo repo) {
        Draft draft = new Draft();
        draft.setUser(user);
        draft.setKind(kind);
        // 기존 조회(GET /drafts?date=·from·to)가 그대로 돌도록 시작일을 work_date 에도 넣는다.
        draft.setWorkDate(from);
        draft.setPeriodStart(from);
        draft.setPeriodEnd(to);
        draft.setRepo(repo);
        draft.setStatus(DraftStatus.DRAFT);
        draft.setAutoGenerated(false);
        draft.setVersion(draftRepository.nextVersion(
                user.getId(), kind, from, repo == null ? null : repo.getId()));
        return draft;
    }

    /**
     * LLM 한 번. 하루치와 달리 실패하면 예외다 — 묶지 못한 글은 값어치가 없다.
     *
     * @throws ApiException 503 — 화면이 "잠시 뒤 다시" 를 띄울 수 있게 구분해 알린다
     */
    private String complete(Long userId, String systemPrompt, String userPrompt, Map<String, String> vars) {
        try {
            LlmProvider provider = resolver.resolve(llmSettings.providerOf(userId));
            String text = provider.complete(new LlmRequest(
                    prompts.load(systemPrompt),
                    prompts.render(userPrompt, vars),
                    LlmRequest.DEFAULT_TEMPERATURE,
                    MAX_TOKENS,
                    vars));
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("빈 응답");
            }
            return text.strip();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("기간 업무일지를 LLM 으로 만들지 못했다: {}", e.getMessage());
            throw new ApiException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "LLM_FAILED",
                    "AI 요약에 실패했습니다. 모델 설정을 확인하거나 잠시 뒤 다시 시도해 주세요.");
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    /** 그 날이 든 주의 월요일. 주는 월요일에 시작해 일요일에 끝난다. */
    static LocalDate mondayOf(LocalDate date) {
        if (date == null) {
            throw ApiException.badRequest("DATE_REQUIRED", "주를 골라 주세요.");
        }
        return date.with(java.time.DayOfWeek.MONDAY);
    }

    /** "2026-09-07 ~ 09-13" — 제목에 넣을 짧은 주 표기. */
    static String weekLabel(LocalDate monday) {
        LocalDate sunday = monday.plusDays(6);
        return "%s ~ %02d-%02d".formatted(monday, sunday.getMonthValue(), sunday.getDayOfMonth());
    }

    /** 본문 맨 아래 각주 — 이 일지가 어느 주를 다루는지 한 줄로 남긴다. */
    static String periodNote(LocalDate monday, LocalDate sunday) {
        return "---\n_기간: %s(월) ~ %s(일)_".formatted(monday, sunday);
    }

    /** 커밋별 요약이 이미 있으면 그것을 쓴다 — 두 번 요약하지 않고 컨텍스트도 아낀다. */
    static String activityLine(Activity a) {
        String kind = switch (a.getType()) {
            case COMMIT -> "커밋";
            case PR_OPENED -> "PR 생성";
            case PR_MERGED -> "PR 머지";
        };
        String mark = a.getType() == ActivityType.COMMIT
                ? shorten(a.getSha())
                : "PR #" + a.getExternalId();
        String who = a.getUser() != null ? displayName(a.getUser()) : nullToEmpty(a.getExternalLogin());
        String text = a.getSummary() != null && !a.getSummary().isBlank()
                ? a.getSummary().replace('\n', ' ').strip()
                : nullToEmpty(a.getTitle());
        return "- [%s] %s %s (%s): %s"
                .formatted(KstDates.toKstDate(a.getOccurredAt()), who, kind, mark, text);
    }

    /** 일지 본문에서 제목 줄과 "직접 작성" 자리표시자뿐인 메모 절을 뗀다. */
    static String stripped(String contentMd) {
        if (contentMd == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        boolean skippingMemo = false;
        for (String line : contentMd.split("\n")) {
            if (line.startsWith("# ")) {
                continue;
            }
            if (line.startsWith("## ")) {
                skippingMemo = line.contains("메모");
            }
            if (skippingMemo) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString().strip();
    }

    private static String displayName(User u) {
        if (u.getName() != null && !u.getName().isBlank()) {
            return u.getName();
        }
        return u.getLogin() != null ? u.getLogin() : String.valueOf(u.getLoginId());
    }

    private static String shorten(String sha) {
        return sha == null ? "" : sha.length() <= 7 ? sha : sha.substring(0, 7);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
