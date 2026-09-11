package com.worklog.chat;

import com.worklog.activity.Activity;
import com.worklog.activity.ActivityRepository;
import com.worklog.admin.PeopleDirectoryService;
import com.worklog.admin.dto.PeopleDirectoryResponse;
import com.worklog.auth.User;
import com.worklog.auth.UserRepository;
import com.worklog.auth.UserRole;
import com.worklog.config.KstDates;
import com.worklog.draft.Draft;
import com.worklog.draft.DraftRepository;
import com.worklog.draft.DraftStatus;
import com.worklog.draft.DraftTemplate;
import com.worklog.llm.LlmProvider;
import com.worklog.llm.LlmProviderResolver;
import com.worklog.llm.LlmRequest;
import com.worklog.llm.LlmSettingService;
import com.worklog.llm.MockLlmProvider;
import com.worklog.llm.PromptLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.worklog.vscode.VscodeSession;
import com.worklog.vscode.VscodeSessionRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅으로 물어본 업무 일지에 답한다 (TODO_0910 — Mattermost 질의 응답).
 *
 * <p>답은 두 갈래다. 그날 초안이 있으면 <b>초안을 그대로</b> 준다 — 사람이 고치고 확정한
 * 내용이 곧 그날의 일지다. 없으면 활동과 세션으로 <b>그 자리에서 조립</b>해 준다. 이때 초안을
 * 저장하지 않는다. 물어봤다고 초안이 생기고 알림까지 나가면 놀란다.
 */
@Service
public class WorkLogAnswerService {

    private static final Logger log = LoggerFactory.getLogger(WorkLogAnswerService.class);

    private final UserRepository userRepository;
    private final PeopleDirectoryService peopleDirectoryService;
    private final DraftRepository draftRepository;
    private final ActivityRepository activityRepository;
    private final VscodeSessionRepository sessionRepository;
    private final com.worklog.draft.DraftGenerator draftGenerator;
    private final LlmProviderResolver llmResolver;
    private final LlmSettingService llmSettings;
    private final PromptLoader prompts;
    private final String frontendUrl;

    /** 기간 질문의 상한. 이보다 길면 끝에서부터 이만큼만 본다 — 채팅 한 글에 담기는 양이 있다. */
    static final int MAX_RANGE_DAYS = 31;
    /** 답 하나의 글자 상한. Mattermost 는 16,383자까지 받는다. 여유를 둔다. */
    static final int MAX_ANSWER_CHARS = 12_000;

    public WorkLogAnswerService(
            UserRepository userRepository,
            PeopleDirectoryService peopleDirectoryService,
            DraftRepository draftRepository,
            ActivityRepository activityRepository,
            VscodeSessionRepository sessionRepository,
            com.worklog.draft.DraftGenerator draftGenerator,
            LlmProviderResolver llmResolver,
            LlmSettingService llmSettings,
            PromptLoader prompts,
            @Value("${worklog.frontend-url}") String frontendUrl) {
        this.userRepository = userRepository;
        this.peopleDirectoryService = peopleDirectoryService;
        this.draftRepository = draftRepository;
        this.activityRepository = activityRepository;
        this.sessionRepository = sessionRepository;
        this.draftGenerator = draftGenerator;
        this.llmResolver = llmResolver;
        this.llmSettings = llmSettings;
        this.prompts = prompts;
        this.frontendUrl = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
    }

    /**
     * 문장 하나에 답한다.
     *
     * @return 업무 일지를 묻는 말이 아니면 {@link Optional#empty()} — 채널의 다른 대화에 끼어들지 않는다
     */
    @Transactional(readOnly = true)
    public Optional<String> answer(String text) {
        return answerInternal(text).map(Reply::text);
    }

    private Optional<Reply> answerInternal(String text) {
        WorkLogQueryParser.Intent intent = WorkLogQueryParser.intentOf(text);
        if (intent == WorkLogQueryParser.Intent.NONE) {
            return Optional.empty();
        }
        LocalDate date = WorkLogQueryParser.dateIn(text);
        Map<String, Person> people = people();

        List<String> names = WorkLogQueryParser.peopleIn(text, people.keySet());
        if (names.isEmpty()) {
            // "회의 요약 올립니다" 에 끼어들지 않는다. 분명히 일지를 물은 경우에만 쓰는 법을 알려 준다.
            return intent == WorkLogQueryParser.Intent.STRONG
                    ? Optional.of(Reply.of(unknownPerson(people)))
                    : Optional.empty();
        }
        // 이름이 여럿이면 문장에 나온 순서대로 한 사람씩. 사원 명단 이름과 계정 이름이 같은 사람을
        // 가리키면("조웅식", "UngsikJo") 한 번만 답한다.
        Map<Object, Person> targets = new LinkedHashMap<>();
        for (String name : names) {
            Person p = people.get(name);
            Object key = p.user() != null ? p.user().getId() : p.displayName();
            targets.putIfAbsent(key, p);
        }
        // "9월 8일~9월 10일", "이번 주", "최근 7일" 이면 기간으로 답한다.
        Optional<WorkLogQueryParser.DateRange> range = WorkLogQueryParser.rangeIn(text);

        StringBuilder out = new StringBuilder();
        // 한 사람·하루를 물었을 때만 "만들어 드릴까요" 를 묻는다. 여럿이면 ✅ 하나로
        // 무엇을 만들지 정할 수 없고, 기간은 주간 일지라 사람이 화면에서 만드는 편이 맞다.
        Reply offer = null;
        for (Person person : targets.values()) {
            if (out.length() > 0) {
                out.append("\n\n---\n\n");
            }
            if (person.user() == null) {
                out.append("**%s** 님은 사원 명단에는 있지만 아직 WorkLog Drafter 계정이 없어 기록이 없습니다."
                        .formatted(person.displayName()));
            } else if (range.isPresent()) {
                out.append(renderRange(person, range.get()));
            } else {
                Reply one = renderReply(person, date);
                out.append(one.text());
                if (one.offersToCreate() && targets.size() == 1) {
                    offer = one;
                }
            }
            if (targets.size() > 1 && out.length() > MAX_ANSWER_CHARS) {
                out.append("\n\n… _(너무 길어 여기서 줄였습니다. 사람이나 기간을 줄여 다시 물어봐 주세요)_");
                break;
            }
        }
        return Optional.of(offer == null
                ? Reply.of(out.toString())
                : new Reply(out.toString(), offer.offerUserId(), offer.offerDate()));
    }

    /**
     * 기간 답 — 날짜마다 그날의 일지(초안이 있으면 초안, 없으면 활동으로 조립)를 모으고,
     * 실제 LLM 이 있으면 맨 위에 기간 전체 요약을 얹는다. 기록 없는 날은 건너뛴다.
     */
    private String renderRange(Person person, WorkLogQueryParser.DateRange requested) {
        User user = person.user();
        LocalDate to = requested.to();
        LocalDate from = requested.from();
        String clampNote = "";
        if (requested.days() > MAX_RANGE_DAYS) {
            from = to.minusDays(MAX_RANGE_DAYS - 1L);
            clampNote = "\n_(기간이 길어 마지막 %d일만 봤습니다)_".formatted(MAX_RANGE_DAYS);
        }

        Map<LocalDate, Draft> draftsByDate = new LinkedHashMap<>();
        for (Draft d : draftRepository.findLatestBetween(from, to)) {
            if (d.getUser() != null && d.getUser().getId().equals(user.getId())) {
                draftsByDate.putIfAbsent(d.getWorkDate(), d);
            }
        }

        List<DaySection> days = new java.util.ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            Draft d = draftsByDate.get(date);
            if (d != null) {
                String label = d.getStatus() == DraftStatus.CONFIRMED ? "확정본" : "초안 v%d".formatted(d.getVersion());
                days.add(new DaySection(date, label, body(d.getContentMd()), d.getId()));
                continue;
            }
            List<Activity> activities = activityRepository.findForUserBetween(
                    user.getId(), KstDates.startOf(date), KstDates.endOf(date));
            List<VscodeSession> sessions = sessionRepository.findByUserIdAndWorkDate(user.getId(), date);
            if (activities.isEmpty() && sessions.isEmpty()) {
                continue;
            }
            String md = DraftTemplate.render(date, person.displayName(), activities, sessions);
            days.add(new DaySection(date, "초안 미생성 — 활동 %d건".formatted(activities.size()), body(md), null));
        }

        long dayCount = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        if (days.isEmpty()) {
            return "**%s · %s ~ %s** — 기록된 활동이 없습니다.%s".formatted(person.displayName(), from, to, clampNote);
        }

        StringBuilder out = new StringBuilder();
        out.append("**%s · %s ~ %s 업무 일지** _(%d일 중 %d일 기록)_%s\n"
                .formatted(person.displayName(), from, to, dayCount, days.size(), clampNote));

        rangeSummary(user, person.displayName(), from, to, dayCount, days)
                .ifPresent(summary -> out.append("\n**기간 요약**\n").append(summary).append("\n"));

        for (DaySection day : days) {
            out.append("\n### %s _(%s)_\n".formatted(day.date(), day.label()));
            out.append(day.body()).append('\n');
            if (day.draftId() != null) {
                out.append("🔗 %s/drafts/%d\n".formatted(frontendUrl, day.draftId()));
            }
            if (out.length() > MAX_ANSWER_CHARS) {
                out.append("\n… _(너무 길어 여기서 줄였습니다. 기간을 좁혀 다시 물어봐 주세요)_");
                break;
            }
        }
        return out.toString().strip();
    }

    /**
     * 기간 전체를 LLM 이 3~6줄로. 실제 모델이 없으면(mock) 얹지 않고, 실패해도 답은 나간다.
     * 어느 모델을 쓸지는 그 사람의 설정(F9)을 따른다.
     */
    private Optional<String> rangeSummary(
            User user, String name, LocalDate from, LocalDate to, long dayCount, List<DaySection> days) {
        try {
            LlmProvider provider = llmResolver.resolve(llmSettings.providerOf(user.getId()));
            if (provider == null || MockLlmProvider.ID.equals(provider.id())) {
                return Optional.empty();
            }
            StringBuilder joined = new StringBuilder();
            for (DaySection d : days) {
                joined.append("[").append(d.date()).append(" · ").append(d.label()).append("]\n")
                        .append(d.body()).append("\n\n");
            }
            Map<String, String> vars = new java.util.HashMap<>();
            vars.put("name", name);
            vars.put("from", from.toString());
            vars.put("to", to.toString());
            vars.put("dayCount", String.valueOf(dayCount));
            vars.put("recordedDays", String.valueOf(days.size()));
            vars.put("days", joined.toString().strip());
            String text = provider.complete(new LlmRequest(
                    prompts.load(PromptLoader.RANGE_SUMMARY_SYSTEM),
                    prompts.render(PromptLoader.RANGE_SUMMARY_USER, vars),
                    LlmRequest.DEFAULT_TEMPERATURE,
                    600,
                    vars));
            return text == null || text.isBlank() ? Optional.empty() : Optional.of(text.strip());
        } catch (Exception e) {
            log.warn("{} 의 {}~{} 기간 요약이 LLM 으로 실패해 날짜별 일지만 준다: {}", name, from, to, e.getMessage());
            return Optional.empty();
        }
    }

    /** 기간 답의 하루. draftId 는 초안에서 왔을 때만. */
    record DaySection(LocalDate date, String label, String body, Long draftId) {}

    /**
     * 채팅에서 "만들어 드릴까요" 에 "예" 가 왔을 때 그 자리에서 만든다 (9/11).
     *
     * <p>화면의 AI 생성과 같은 길을 쓴다 — 답과 화면이 다른 글이면 어느 쪽이 맞는지 헷갈린다.
     * <b>자동 생성됨</b>으로 표시한다. 본인이 쓴 글이 아니고, 본인이 손대면 그때부터 사람 글이 된다.
     *
     * @return 만든 초안 id. 재료가 없어 만들지 못하면 비어 있다
     */
    @Transactional
    public Optional<Long> createDraft(Long userId, LocalDate workDate) {
        return draftGenerator.generate(userId, workDate, true).map(com.worklog.draft.Draft::getId);
    }

    /**
     * 초안 하나를 채팅용으로 — 대표 채널의 "예" 에 답할 때 (V14). 사람이 물은 것과 같은 모양이다.
     */
    @Transactional(readOnly = true)
    public Optional<String> answerDraft(Long draftId) {
        return draftRepository.findById(draftId).map(d -> {
            String label = d.getStatus() == DraftStatus.CONFIRMED
                    ? "확정본"
                    : "초안 v%d — 아직 확정 전".formatted(d.getVersion());
            return header(displayName(d.getUser()), d.getWorkDate(), label)
                    + body(d.getContentMd())
                    + "\n\n🔗 %s/drafts/%d".formatted(frontendUrl, d.getId());
        });
    }

    /**
     * 채팅 한 마디에 대한 답.
     *
     * @param text 채널에 쓸 글
     * @param offerUserId 일지가 없어 <b>만들어 드릴까요</b> 를 물을 때 그 사람. 아니면 null
     * @param offerDate 그 날
     */
    public record Reply(String text, Long offerUserId, LocalDate offerDate) {

        static Reply of(String text) {
            return new Reply(text, null, null);
        }

        public boolean offersToCreate() {
            return offerUserId != null && offerDate != null;
        }
    }

    /**
     * {@link #answer(String)} 와 같지만, 일지가 없을 때 <b>만들지 말지 물어보는</b> 답을 낼 수 있다.
     *
     * <p>봇이 이 값을 받아 ✅ ❌ 를 달고, "예" 면 그 자리에서 일지를 만든다.
     */
    @Transactional(readOnly = true)
    public Optional<Reply> reply(String text) {
        return answerInternal(text);
    }

    private String render(Person person, LocalDate date) {
        return renderReply(person, date).text();
    }

    /**
     * 그날 일지. 없으면 <b>만들어 드릴까요</b> 를 묻는다 (9/11).
     *
     * <p>전에는 활동으로 그 자리에서 조립해 보여 줬다. 보기에는 같지만 <b>어디에도 남지 않아</b>
     * 물을 때마다 다시 만들어졌고, 정작 그 사람 화면에는 일지가 없었다. 이제 묻고, 그러라고 하면
     * 진짜 일지로 만든다.
     */
    private Reply renderReply(Person person, LocalDate date) {
        User user = person.user();
        Optional<Draft> draft = draftRepository.findFirstByUserIdAndWorkDateOrderByVersionDesc(user.getId(), date);
        if (draft.isPresent()) {
            Draft d = draft.get();
            String label = d.getStatus() == DraftStatus.CONFIRMED
                    ? "확정본"
                    : "초안 v%d — 아직 확정 전".formatted(d.getVersion());
            return Reply.of(header(person.displayName(), date, label)
                    + body(d.getContentMd())
                    + "\n\n🔗 %s/drafts/%d".formatted(frontendUrl, d.getId()));
        }

        List<Activity> activities = activityRepository.findForUserBetween(
                user.getId(), KstDates.startOf(date), KstDates.endOf(date));
        List<VscodeSession> sessions = sessionRepository.findByUserIdAndWorkDate(user.getId(), date);
        if (activities.isEmpty() && sessions.isEmpty()) {
            // 만들 재료가 없으니 물어볼 것도 없다.
            return Reply.of("**%s · %s** — 기록된 활동이 없습니다. 만들 재료가 없습니다."
                    .formatted(person.displayName(), date));
        }
        return new Reply(
                "**%s · %s** — 아직 업무 일지가 없습니다. 활동 %d건·기록 %d건이 있으니 **지금 만들어 드릴까요?**\n"
                        .formatted(person.displayName(), date, activities.size(), sessions.size())
                        + "아래 ✅ 를 누르시면 만듭니다. ❌ 는 그만둡니다. (**예**/**아니오**라고 답해도 됩니다)",
                user.getId(),
                date);
    }

    private static String header(String name, LocalDate date, String label) {
        return "**%s · %s 업무 일지** _(%s)_\n".formatted(name, date, label);
    }

    /**
     * 초안 Markdown 에서 채팅에 맞지 않는 부분을 뗀다 — 제목 줄(위에서 따로 붙인다)과
     * "직접 작성" 자리표시자뿐인 메모 절.
     */
    static String body(String contentMd) {
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

    /**
     * 물어볼 수 있는 이름을 알려 준다. 한 사람에 이름이 여럿(사원 명단 "조웅식", 계정 "ungsikJo")이면
     * 사원 명단 쪽 하나만 보여 주고, 관리자 계정은 활동이 없으므로 뺀다.
     */
    private String unknownPerson(Map<String, Person> people) {
        Map<Long, String> bestByUser = new LinkedHashMap<>();
        for (Person p : people.values()) {
            if (p.user() == null || p.user().getRole() == UserRole.ADMIN) {
                continue;
            }
            if (p.fromDirectory() || !bestByUser.containsKey(p.user().getId())) {
                bestByUser.put(p.user().getId(), p.displayName());
            }
        }
        String names = bestByUser.values().stream()
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("(없음)");
        return "누구의 일지인지 못 찾았습니다. 이렇게 물어봐 주세요: `조웅식의 오늘 업무일지 요약해줘`\n찾을 수 있는 이름: "
                + names;
    }

    /**
     * 문장에서 찾을 이름 → 사람. 사원 명단의 이름, 계정 이름, GitHub 로그인을 모두 받는다.
     * 사원번호는 넣지 않는다 — "114" 같은 숫자는 날짜 표현 안에도 들어 있을 수 있다.
     */
    private Map<String, Person> people() {
        Map<String, Person> map = new LinkedHashMap<>();
        Map<Long, User> byEmpSeq = new LinkedHashMap<>();
        for (User u : userRepository.findAll()) {
            if (u.getEmpSeq() != null) {
                byEmpSeq.put(u.getEmpSeq(), u);
            }
            Person p = new Person(displayName(u), u, false);
            put(map, u.getName(), p);
            put(map, u.getLogin(), p);
        }
        PeopleDirectoryResponse directory = peopleDirectoryService.directory();
        for (PeopleDirectoryResponse.EmployeeRow row : directory.employees()) {
            User linked = byEmpSeq.get(row.empSeq());
            // 사원 명단의 이름이 계정 이름보다 낫다 — "ungsikJo" 보다 "조웅식" 으로 답한다.
            put(map, row.empNm(), new Person(row.empNm(), linked, true));
        }
        return map;
    }

    private static void put(Map<String, Person> map, String key, Person p) {
        if (key == null || key.isBlank()) {
            return;
        }
        Person existing = map.get(key);
        // 사원 명단 쪽(나중에 들어옴)이 이기되, 계정 없는 사원이 계정 있는 항목을 덮지는 않는다.
        if (existing == null || p.user() != null) {
            map.put(key, p);
        }
    }

    private static String displayName(User u) {
        return u.getName() != null && !u.getName().isBlank() ? u.getName() : u.getLogin();
    }

    /**
     * 명단의 한 사람. 사원 명단에만 있고 계정이 없으면 user 가 null 이다.
     *
     * @param fromDirectory 사원 명단에서 온 이름인지 — 답할 때 이쪽 이름을 우선한다
     */
    record Person(String displayName, User user, boolean fromDirectory) {}
}
