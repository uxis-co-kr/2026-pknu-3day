package com.worklog.stats;

import com.worklog.activity.ActivityRepository;
import com.worklog.activity.ActivityType;
import com.worklog.config.KstDates;
import com.worklog.notify.RemindPolicy;
import com.worklog.stats.dto.DailyStatsResponse;
import com.worklog.vscode.VscodeSessionRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일별 집계 (PRD 7. /stats/daily).
 */
@Service
public class StatsService {

    private final ActivityRepository activityRepository;
    private final VscodeSessionRepository sessionRepository;

    public StatsService(
            ActivityRepository activityRepository, VscodeSessionRepository sessionRepository) {
        this.activityRepository = activityRepository;
        this.sessionRepository = sessionRepository;
    }

    @Transactional(readOnly = true)
    public DailyStatsResponse daily(LocalDate date) {
        Map<ActivityType, Long> today = countsFor(date);
        long commitsToday = today.getOrDefault(ActivityType.COMMIT, 0L);
        long commitsYesterday = countsFor(date.minusDays(1)).getOrDefault(ActivityType.COMMIT, 0L);

        // 리마인드(F7-2)와 같은 기준을 쓴다. 값이 어긋나면 화면과 알림이 따로 논다.
        OffsetDateTime staleThreshold = RemindPolicy.staleThreshold(OffsetDateTime.now());

        Map<ActivityType, Long> unmapped = unmappedFor(date);

        return new DailyStatsResponse(
                date,
                commitsToday,
                today.getOrDefault(ActivityType.PR_OPENED, 0L),
                today.getOrDefault(ActivityType.PR_MERGED, 0L),
                sessionRepository.countByWorkDate(date),
                commitsToday - commitsYesterday,
                sessionRepository.countStale(date, staleThreshold),
                new DailyStatsResponse.Unmapped(
                        unmapped.getOrDefault(ActivityType.COMMIT, 0L),
                        unmapped.getOrDefault(ActivityType.PR_OPENED, 0L),
                        unmapped.getOrDefault(ActivityType.PR_MERGED, 0L)),
                byUser(date));
    }

    /** 총계와 byUser 합계의 차이 — 가입하지 않은 GitHub 계정의 활동이다. */
    private Map<ActivityType, Long> unmappedFor(LocalDate date) {
        Map<ActivityType, Long> counts = new EnumMap<>(ActivityType.class);
        for (Object[] row : activityRepository.countUnmappedByTypeBetween(
                KstDates.startOf(date), KstDates.endOf(date))) {
            counts.put((ActivityType) row[0], (Long) row[1]);
        }
        return counts;
    }

    private Map<ActivityType, Long> countsFor(LocalDate date) {
        Map<ActivityType, Long> counts = new EnumMap<>(ActivityType.class);
        for (Object[] row : activityRepository.countByTypeBetween(
                KstDates.startOf(date), KstDates.endOf(date))) {
            counts.put((ActivityType) row[0], (Long) row[1]);
        }
        return counts;
    }

    private List<DailyStatsResponse.ByUser> byUser(LocalDate date) {
        // 사용자별로 타입 집계를 모은 뒤 세션 수를 합친다.
        Map<Long, Map<ActivityType, Long>> perUser = new LinkedHashMap<>();
        for (Object[] row : activityRepository.countByUserAndTypeBetween(
                KstDates.startOf(date), KstDates.endOf(date))) {
            perUser.computeIfAbsent((Long) row[0], k -> new EnumMap<>(ActivityType.class))
                    .put((ActivityType) row[1], (Long) row[2]);
        }

        Map<Long, Long> sessions = new LinkedHashMap<>();
        for (Object[] row : sessionRepository.countByUser(date)) {
            sessions.put((Long) row[0], (Long) row[1]);
        }
        // 활동은 없고 세션만 있는 사용자도 카드에 나와야 한다.
        sessions.keySet().forEach(userId -> perUser.computeIfAbsent(userId, k -> Map.of()));

        List<DailyStatsResponse.ByUser> result = new ArrayList<>();
        perUser.forEach((userId, counts) -> result.add(new DailyStatsResponse.ByUser(
                userId,
                counts.getOrDefault(ActivityType.COMMIT, 0L),
                counts.getOrDefault(ActivityType.PR_OPENED, 0L),
                counts.getOrDefault(ActivityType.PR_MERGED, 0L),
                sessions.getOrDefault(userId, 0L))));
        return result;
    }
}
