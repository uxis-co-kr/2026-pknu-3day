package com.worklog.stats;

import com.worklog.activity.ActivityRepository;
import com.worklog.activity.ActivityType;
import com.worklog.auth.User;
import com.worklog.auth.UserRepository;
import com.worklog.config.ApiException;
import com.worklog.config.KstDates;
import com.worklog.draft.Draft;
import com.worklog.draft.DraftRepository;
import com.worklog.stats.dto.PeopleStatsResponse;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인원별 시계열 (PRD 7. GET /stats/people — F10b).
 *
 * <p>빈 구간도 0 으로 채운다. 화면이 막대 그래프를 그리므로 날짜가 비면 축이 어긋난다.
 */
@Service
public class PeopleStatsService {

    /** 한 번에 조회할 수 있는 기간 상한. 그래프에 담을 수 있는 크기이기도 하다. */
    private static final int MAX_RANGE_DAYS = 92;

    /** from/to 를 생략했을 때의 기본 구간 (오늘 포함 7일). */
    private static final int DEFAULT_RANGE_DAYS = 7;

    private final ActivityRepository activityRepository;
    private final DraftRepository draftRepository;
    private final UserRepository userRepository;

    public PeopleStatsService(
            ActivityRepository activityRepository,
            DraftRepository draftRepository,
            UserRepository userRepository) {
        this.activityRepository = activityRepository;
        this.draftRepository = draftRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PeopleStatsResponse people(
            LocalDate from, LocalDate to, Granularity granularity, Long userId) {

        LocalDate end = to != null ? to : KstDates.today();
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_RANGE_DAYS - 1L);
        validate(start, end);

        List<User> users = targetUsers(userId);
        if (users.isEmpty()) {
            return new PeopleStatsResponse(start, end, granularity.wireName(), List.of());
        }

        Map<Long, Map<LocalDate, Map<ActivityType, Long>>> counts = countActivities(start, end, granularity);
        Map<Long, Map<LocalDate, Draft>> drafts = latestDrafts(start, end);
        List<LocalDate> buckets = buckets(start, end, granularity);

        List<PeopleStatsResponse.Item> items = new ArrayList<>();
        for (User user : users) {
            Map<LocalDate, Map<ActivityType, Long>> perDate =
                    counts.getOrDefault(user.getId(), Map.of());
            Map<LocalDate, Draft> perDraft = drafts.getOrDefault(user.getId(), Map.of());

            List<PeopleStatsResponse.SeriesPoint> series = new ArrayList<>();
            long commits = 0;
            long prs = 0;
            long merges = 0;
            for (LocalDate bucket : buckets) {
                Map<ActivityType, Long> byType = perDate.getOrDefault(bucket, Map.of());
                long c = byType.getOrDefault(ActivityType.COMMIT, 0L);
                long p = byType.getOrDefault(ActivityType.PR_OPENED, 0L);
                long m = byType.getOrDefault(ActivityType.PR_MERGED, 0L);
                commits += c;
                prs += p;
                merges += m;

                // 주 단위는 한 주에 초안이 여럿이라 하나를 고를 근거가 없다.
                Draft draft = granularity == Granularity.DAY ? perDraft.get(bucket) : null;
                series.add(new PeopleStatsResponse.SeriesPoint(
                        bucket, c, p, m,
                        draft == null
                                ? null
                                : new PeopleStatsResponse.DraftRef(draft.getId(), draft.getStatus())));
            }

            items.add(new PeopleStatsResponse.Item(
                    new PeopleStatsResponse.UserSummary(
                            user.getId(), displayLogin(user), user.getName(), user.getAvatarUrl()),
                    new PeopleStatsResponse.Totals(commits, prs, merges),
                    series));
        }
        // 활동이 많은 사람부터. 같으면 로그인 순.
        items.sort((a, b) -> {
            int byCommits = Long.compare(b.totals().commits(), a.totals().commits());
            return byCommits != 0 ? byCommits : a.user().login().compareTo(b.user().login());
        });
        return new PeopleStatsResponse(start, end, granularity.wireName(), items);
    }

    /**
     * 화면에 보일 로그인. GitHub 을 붙이지 않은 자체 계정(관리자, 사원번호 로그인)은 login 이
     * 비어 있다 — 그대로 내보내면 정렬에서 NPE 가 나고 화면에는 빈 이름이 뜬다.
     */
    private static String displayLogin(User user) {
        if (user.getLogin() != null && !user.getLogin().isBlank()) {
            return user.getLogin();
        }
        if (user.getLoginId() != null && !user.getLoginId().isBlank()) {
            return user.getLoginId();
        }
        return String.valueOf(user.getId());
    }

    private void validate(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw ApiException.badRequest("INVALID_DATE_RANGE", "from 이 to 보다 뒤입니다.");
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw ApiException.badRequest(
                    "INVALID_DATE_RANGE", "조회 기간은 최대 %d일입니다.".formatted(MAX_RANGE_DAYS));
        }
    }

    /**
     * 대상 사용자. userId 를 생략하면 전원이다.
     *
     * <p>활동이 없는 사용자도 넣는다 — 화면의 사용자 선택기에서 사라지면 안 된다.
     * 가입하지 않은 GitHub 계정은 user 가 없으므로 애초에 대상이 아니다 (PRD F1-5).
     */
    private List<User> targetUsers(Long userId) {
        if (userId == null) {
            return userRepository.findAll();
        }
        return userRepository.findById(userId).map(List::of).orElseThrow(
                () -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    /** [userId][구간 시작일][타입] → 건수. */
    private Map<Long, Map<LocalDate, Map<ActivityType, Long>>> countActivities(
            LocalDate start, LocalDate end, Granularity granularity) {

        Map<Long, Map<LocalDate, Map<ActivityType, Long>>> result = new HashMap<>();
        for (Object[] row : activityRepository.countByUserAndDateBetween(
                KstDates.startOf(start), KstDates.endOf(end))) {
            Long uid = ((Number) row[0]).longValue();
            LocalDate bucket = granularity.bucketOf(((Date) row[1]).toLocalDate());
            ActivityType type = ActivityType.valueOf((String) row[2]);
            long count = ((Number) row[3]).longValue();

            result.computeIfAbsent(uid, k -> new HashMap<>())
                    .computeIfAbsent(bucket, k -> new EnumMap<>(ActivityType.class))
                    .merge(type, count, Long::sum);
        }
        return result;
    }

    private Map<Long, Map<LocalDate, Draft>> latestDrafts(LocalDate start, LocalDate end) {
        Map<Long, Map<LocalDate, Draft>> result = new HashMap<>();
        for (Draft draft : draftRepository.findLatestBetween(start, end)) {
            result.computeIfAbsent(draft.getUser().getId(), k -> new LinkedHashMap<>())
                    .put(draft.getWorkDate(), draft);
        }
        return result;
    }

    /** 최신 구간이 앞에 오도록 내림차순 (PRD 7. 예시가 그렇다). */
    private List<LocalDate> buckets(LocalDate start, LocalDate end, Granularity granularity) {
        List<LocalDate> buckets = new ArrayList<>();
        LocalDate cursor = granularity.bucketOf(start);
        while (!cursor.isAfter(end)) {
            buckets.add(cursor);
            cursor = granularity.next(cursor);
        }
        java.util.Collections.reverse(buckets);
        return buckets;
    }
}
