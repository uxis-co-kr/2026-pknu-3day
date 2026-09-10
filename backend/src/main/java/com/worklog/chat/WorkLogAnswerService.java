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

    private final UserRepository userRepository;
    private final PeopleDirectoryService peopleDirectoryService;
    private final DraftRepository draftRepository;
    private final ActivityRepository activityRepository;
    private final VscodeSessionRepository sessionRepository;
    private final String frontendUrl;

    public WorkLogAnswerService(
            UserRepository userRepository,
            PeopleDirectoryService peopleDirectoryService,
            DraftRepository draftRepository,
            ActivityRepository activityRepository,
            VscodeSessionRepository sessionRepository,
            @Value("${worklog.frontend-url}") String frontendUrl) {
        this.userRepository = userRepository;
        this.peopleDirectoryService = peopleDirectoryService;
        this.draftRepository = draftRepository;
        this.activityRepository = activityRepository;
        this.sessionRepository = sessionRepository;
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
        if (!WorkLogQueryParser.asksForWorkLog(text)) {
            return Optional.empty();
        }
        LocalDate date = WorkLogQueryParser.dateIn(text);
        Map<String, Person> people = people();

        Optional<String> name = WorkLogQueryParser.personIn(text, people.keySet());
        if (name.isEmpty()) {
            return Optional.of(unknownPerson(people));
        }
        Person person = people.get(name.get());
        if (person.user() == null) {
            return Optional.of("**%s** 님은 사원 명단에는 있지만 아직 WorkLog Drafter 계정이 없어 기록이 없습니다."
                    .formatted(person.displayName()));
        }
        return Optional.of(render(person, date));
    }

    private String render(Person person, LocalDate date) {
        User user = person.user();
        Optional<Draft> draft = draftRepository.findFirstByUserIdAndWorkDateOrderByVersionDesc(user.getId(), date);
        if (draft.isPresent()) {
            Draft d = draft.get();
            String label = d.getStatus() == DraftStatus.CONFIRMED
                    ? "확정본"
                    : "초안 v%d — 아직 확정 전".formatted(d.getVersion());
            return header(person.displayName(), date, label)
                    + body(d.getContentMd())
                    + "\n\n🔗 %s/drafts/%d".formatted(frontendUrl, d.getId());
        }

        List<Activity> activities = activityRepository.findForUserBetween(
                user.getId(), KstDates.startOf(date), KstDates.endOf(date));
        List<VscodeSession> sessions = sessionRepository.findByUserIdAndWorkDate(user.getId(), date);
        if (activities.isEmpty() && sessions.isEmpty()) {
            return "**%s · %s** — 기록된 활동이 없습니다.".formatted(person.displayName(), date);
        }
        // 초안 템플릿을 그대로 빌려 쓴다. 채팅 답과 초안이 다르게 생기면 어느 쪽이 맞는지 헷갈린다.
        String md = DraftTemplate.render(date, person.displayName(), activities, sessions);
        return header(person.displayName(), date, "초안 미생성 — 지금 집계 (활동 %d건)".formatted(activities.size()))
                + body(md);
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
